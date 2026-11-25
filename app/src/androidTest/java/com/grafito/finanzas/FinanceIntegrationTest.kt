package com.grafito.finanzas

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.FinanceMath
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceIntegrationTest {
    private lateinit var db: FinanceDatabase
    private lateinit var repo: FinanceRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java).build()
        repo = FinanceRepository(db)
        db.initialize()
    }
    @After fun close() { db.close() }

    @Test fun entryEditDeleteUndoAndTransfer() = runBlocking {
        assertTrue(repo.addAccount("Yape", 10_000))
        val accounts = db.dao().allAccounts()
        val cash = accounts.first().id; val yape = accounts.last().id
        assertNull(repo.addEntry(EntryType.EXPENSE, 0, cash, null, ""))
        assertNull(repo.addEntry(EntryType.EXPENSE, 150, 9999, null, ""))
        val income = repo.addEntry(EntryType.INCOME, 20_000, cash, null, "Ingreso")!!
        val expense = repo.addEntry(EntryType.EXPENSE, 150, cash, null, "Pasaje")!!
        assertNull(expense.categoryId)
        val edited = repo.addEntry(EntryType.EXPENSE, 200, cash, null, "Corregido", expense)!!
        assertEquals(expense.occurredAt, edited.occurredAt)
        assertEquals(19_800, FinanceMath.accountBalances(accounts, db.dao().allTransactions()).getValue(cash))
        repo.deleteEntry(edited)
        assertEquals(20_000, FinanceMath.accountBalances(accounts, db.dao().allTransactions()).getValue(cash))
        repo.restoreEntry(edited)
        val transfer = repo.transfer(5_000, cash, yape, "")!!
        assertNull(repo.transfer(100, cash, cash, ""))
        val balances = FinanceMath.accountBalances(accounts, db.dao().allTransactions())
        assertEquals(14_800, balances.getValue(cash))
        assertEquals(15_000, balances.getValue(yape))
        assertEquals(29_800, balances.values.sum())
        assertFalse(repo.deleteAccount(accounts.first()))
        repo.deleteEntry(transfer); repo.deleteEntry(edited); repo.deleteEntry(income)
        assertTrue(repo.deleteAccount(accounts.last()))
        assertFalse(repo.deleteAccount(accounts.first()))
    }

    @Test fun favoritesUseAssignedAccountAndRejectImmediateRepeat() = runBlocking {
        repo.addAccount("Yape", 0)
        val yape = db.dao().allAccounts().last().id
        repo.setDefaultAccount(yape)
        repo.addEntry(EntryType.EXPENSE, 150, yape, null, "Pasaje", favoriteTitle = "Pasaje")
        val favorite = db.dao().allFavorites().single()
        assertEquals(yape, favorite.accountId)
        val entry = repo.addEntry(EntryType.EXPENSE, favorite.amountCents, yape, favorite.categoryId,
            favorite.title, sourceFavoriteId = favorite.id)!!
        assertEquals(yape, entry.accountId)
        assertNull(repo.addEntry(EntryType.EXPENSE, favorite.amountCents, yape, favorite.categoryId,
            favorite.title, sourceFavoriteId = favorite.id))
        assertEquals(2, db.dao().allTransactions().size)
        assertEquals(yape, repo.snapshot().defaultAccountId)
    }

    @Test fun encryptedRoundTripAndMalformedSnapshotPreserveData() = runBlocking {
        repo.addEntry(EntryType.INCOME, 15_000, db.dao().allAccounts().first().id, null, "Prueba")
        val original = repo.snapshot()
        val secret = "copia-prueba-local".toCharArray()
        val encrypted = BackupCipher.encrypt(original.encode(), secret)
        val decoded = BackupSnapshot.decode(BackupCipher.decrypt(encrypted, secret))
        repo.addAccount("Temporal", 100)
        repo.restore(decoded)
        assertEquals(original.accounts, repo.snapshot().accounts)
        assertEquals(original.transactions, repo.snapshot().transactions)
        val invalid = decoded.copy(transactions = decoded.transactions.map { it.copy(accountId = 9999) })
        try { repo.restore(invalid); fail("Debió rechazar referencias inválidas") } catch (_: IllegalArgumentException) { }
        assertEquals(original.transactions, repo.snapshot().transactions)
        val fractional = String(original.encode()).replace("15000", "15000.5").toByteArray()
        try { BackupSnapshot.decode(fractional); fail("Debió rechazar centavos decimales") } catch (_: IllegalArgumentException) { }
    }

    @Test fun failedRestoreRollsBackEntireDatabase() = runBlocking {
        val original = repo.snapshot()
        val replacement = original.copy(accounts = original.accounts.map { it.copy(name = "Reemplazo") })
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON accounts BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.restore(replacement); fail("Debió fallar") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(original.accounts, repo.snapshot().accounts)
        assertEquals(original.categories, repo.snapshot().categories)
    }

    @Test fun restoringLegacyCategoriesAddsExpensesAndPreservesReferences() = runBlocking {
        val account = db.dao().allAccounts().single()
        val legacy = CategoryEntity(Long.MAX_VALUE - 1, "Educacion", "", EntryType.EXPENSE)
        val entry = TransactionEntity(id = 8, type = EntryType.EXPENSE, amountCents = 1500,
            accountId = account.id, categoryId = legacy.id)
        val favorite = FavoriteEntity(id = 9, title = "Útiles", amountCents = 1500, categoryId = legacy.id)
        repo.restore(BackupSnapshot(listOf(account), listOf(legacy), listOf(entry), listOf(favorite), account.id))
        val afterRestore = repo.snapshot()

        db.initialize()
        db.initialize()
        val afterInitialization = repo.snapshot()
        assertEquals(afterRestore.categories, afterInitialization.categories)
        assertEquals(legacy, afterInitialization.categories.single { categoryNameKey(it.name) == "educacion" })
        assertTrue(afterInitialization.categories.any { it.name == "Mascotas" && it.type == EntryType.EXPENSE })
        assertEquals(legacy.id, afterInitialization.transactions.single().categoryId)
        assertEquals(legacy.id, afterInitialization.favorites.single().categoryId)
        afterInitialization.validate()
    }

    @Test fun persistedDatabaseSurvivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "grafito-persistence-test.db"
        context.deleteDatabase(name)
        try {
            var disk = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_1_2).build()
            disk.initialize()
            FinanceRepository(disk).addEntry(EntryType.INCOME, 500, disk.dao().allAccounts().first().id, null, "Persistente")
            disk.close()
            disk = Room.databaseBuilder(context, FinanceDatabase::class.java, name).addMigrations(FinanceDatabase.MIGRATION_1_2).build()
            try { assertEquals(500, disk.dao().allTransactions().single().amountCents) } finally { disk.close() }
        } finally { context.deleteDatabase(name) }
    }
}
