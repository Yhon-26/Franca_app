package com.grafito.finanzas.data

import androidx.room.withTransaction
import com.grafito.finanzas.model.Money
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ReceiptSaveResult { SAVED, DUPLICATE, INVALID }
internal val receiptFingerprintFormat = Regex("[a-f0-9]{64}")

/** All related writes validate and commit against the database, never a stale UI snapshot. */
class FinanceRepository(private val database: FinanceDatabase) {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val recentFavorites = mutableMapOf<Long, Long>()

    suspend fun addAccount(name: String, opening: Long): Boolean = mutex.withLock {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 40 || opening !in 0..Money.MAX_CENTS) return@withLock false
        database.withTransaction { dao.insertAccount(AccountEntity(name = clean, openingBalanceCents = opening)) }
        true
    }

    suspend fun setDefaultAccount(id: Long) = mutex.withLock {
        database.withTransaction {
            require(dao.allAccounts().any { it.id == id }) { "La cuenta ya no existe." }
            dao.setSettings(SettingsEntity(defaultAccountId = id))
        }
    }

    suspend fun setFavoriteAccount(favorite: FavoriteEntity, accountId: Long) = mutex.withLock {
        database.withTransaction {
            require(dao.allAccounts().any { it.id == accountId }) { "La cuenta ya no existe." }
            dao.favorite(favorite.id)?.let { dao.updateFavorite(it.copy(accountId = accountId)) }
        }
    }

    suspend fun addEntry(type: String, cents: Long, accountId: Long, categoryId: Long?, note: String,
                         existing: TransactionEntity? = null, favoriteTitle: String? = null,
                         sourceFavoriteId: Long? = null): TransactionEntity? = mutex.withLock {
        if (sourceFavoriteId != null && favoriteWasJustUsed(sourceFavoriteId)) return@withLock null
        database.withTransaction {
            if (type !in listOf(EntryType.EXPENSE, EntryType.INCOME) || cents !in 1..Money.MAX_CENTS ||
                dao.allAccounts().none { it.id == accountId }) return@withTransaction null
            if (sourceFavoriteId != null && dao.favorite(sourceFavoriteId) == null) return@withTransaction null
            val old = existing?.let { dao.transaction(it.id) ?: return@withTransaction null }
            if (old?.type == EntryType.TRANSFER) return@withTransaction null
            val category = dao.allCategories().find { it.id == categoryId && it.type == type }
            val entry = TransactionEntity(id = old?.id ?: 0, type = type, amountCents = cents,
                accountId = accountId, categoryId = category?.id, note = note.trim().take(160),
                occurredAt = old?.occurredAt ?: System.currentTimeMillis(), receiptFingerprint = old?.receiptFingerprint,
                bcpOperationFingerprint = old?.bcpOperationFingerprint)
            val saved = if (old == null) entry.copy(id = dao.insertTransaction(entry))
                        else entry.also { dao.updateTransaction(it) }
            if (old == null && type == EntryType.EXPENSE && favoriteTitle != null && sourceFavoriteId == null) {
                insertExpenseFavorite(favoriteTitle, cents, accountId, category?.id)
            }
            saved
        }?.also { if (sourceFavoriteId != null) recentFavorites[sourceFavoriteId] = System.nanoTime() }
    }

    suspend fun addFavorite(title: String, cents: Long, accountId: Long, categoryId: Long?): Boolean = mutex.withLock {
        database.withTransaction {
            if (cents !in 1..Money.MAX_CENTS || dao.allAccounts().none { it.id == accountId }) return@withTransaction false
            val category = dao.allCategories().find { it.id == categoryId && it.type == EntryType.EXPENSE }
            if (categoryId != null && category == null) return@withTransaction false
            insertExpenseFavorite(title, cents, accountId, category?.id)
            true
        }
    }

    private suspend fun insertExpenseFavorite(title: String, cents: Long, accountId: Long, categoryId: Long?) {
        dao.insertFavorite(FavoriteEntity(title = title.trim().take(35).ifBlank { "Gasto frecuente" },
            amountCents = cents, categoryId = categoryId, accountId = accountId))
    }

    internal suspend fun isDuplicateSharedReceipt(fingerprint: String, operationFingerprint: String?): Boolean =
        database.withTransaction {
            if (operationFingerprint == null) return@withTransaction dao.hasReceipt(fingerprint)
            if (dao.hasReceipt(operationFingerprint)) return@withTransaction true
            // Re-sharing the same file can add the operation ID that older versions did not store.
            val linked = dao.attachBcpOperation(fingerprint, operationFingerprint) > 0
            if (linked) database.bcpMailSuggestions().dismiss(operationFingerprint)
            linked || dao.hasReceipt(fingerprint)
        }

    internal suspend fun saveBcpReceipt(fingerprint: String, cents: Long, accountId: Long, destinationId: Long?,
                                        note: String, categoryId: Long? = null,
                                        occurredAt: Long = System.currentTimeMillis(),
                                        operationFingerprint: String? = null): ReceiptSaveResult = mutex.withLock {
        database.withTransaction {
            val accounts = dao.allAccounts()
            if (!receiptFingerprintFormat.matches(fingerprint) || cents !in 1..Money.MAX_CENTS ||
                (operationFingerprint != null && (!receiptFingerprintFormat.matches(operationFingerprint) || operationFingerprint == fingerprint)) ||
                accounts.none { it.id == accountId } || destinationId == accountId ||
                (destinationId != null && accounts.none { it.id == destinationId })) {
                return@withTransaction ReceiptSaveResult.INVALID
            }
            if (categoryId != null && (destinationId != null ||
                    dao.allCategories().none { it.id == categoryId && it.type == EntryType.EXPENSE })) {
                return@withTransaction ReceiptSaveResult.INVALID
            }
            if (dao.hasReceipt(fingerprint, operationFingerprint)) return@withTransaction ReceiptSaveResult.DUPLICATE
            dao.insertTransaction(TransactionEntity(
                type = if (destinationId == null) EntryType.EXPENSE else EntryType.TRANSFER,
                amountCents = cents,
                accountId = accountId,
                toAccountId = destinationId,
                categoryId = categoryId,
                note = note.trim().take(160).ifBlank { if (destinationId == null) "Pago BCP" else "Transferencia BCP" },
                occurredAt = occurredAt,
                receiptFingerprint = fingerprint,
                bcpOperationFingerprint = operationFingerprint
            ))
            operationFingerprint?.let { database.bcpMailSuggestions().dismiss(it) }
            ReceiptSaveResult.SAVED
        }
    }

    suspend fun transfer(cents: Long, from: Long, to: Long, note: String): TransactionEntity? = mutex.withLock {
        database.withTransaction {
            val accounts = dao.allAccounts()
            if (cents !in 1..Money.MAX_CENTS || from == to || accounts.none { it.id == from } ||
                accounts.none { it.id == to }) return@withTransaction null
            val entry = TransactionEntity(type = EntryType.TRANSFER, amountCents = cents,
                accountId = from, toAccountId = to, note = note.trim().take(160))
            entry.copy(id = dao.insertTransaction(entry))
        }
    }

    private fun favoriteWasJustUsed(id: Long): Boolean =
        recentFavorites[id]?.let { System.nanoTime() - it < 1_500_000_000L } ?: false

    suspend fun deleteEntry(entry: TransactionEntity) = mutex.withLock {
        database.withTransaction {
            require(dao.transaction(entry.id) == entry) { "El movimiento cambió. Vuelve a abrirlo." }
            dao.deleteTransaction(entry)
        }
    }
    suspend fun restoreEntry(entry: TransactionEntity) = mutex.withLock { dao.restoreTransaction(entry) }
    suspend fun removeFavorite(favorite: FavoriteEntity) = mutex.withLock { dao.deleteFavorite(favorite) }
    suspend fun deleteAccount(account: AccountEntity): Boolean = mutex.withLock {
        database.withTransaction {
            if (dao.accountsCount() <= 1 || dao.countAccountTransactions(account.id) > 0) return@withTransaction false
            dao.deleteAccount(account)
            true
        }
    }

    suspend fun snapshot(): BackupSnapshot = mutex.withLock {
        database.withTransaction { BackupSnapshot(dao.allAccounts(), dao.allCategories(), dao.allTransactions(),
            dao.allFavorites(), dao.currentSettings()?.defaultAccountId) }
    }

    suspend fun restore(snapshot: BackupSnapshot) = mutex.withLock {
        snapshot.validate()
        database.withTransaction {
            database.yapeSuggestions().dismissAll()
            database.bcpMailSuggestions().dismissAll()
            dao.clearTransactions(); dao.clearFavorites(); dao.clearSettings(); dao.clearCategories(); dao.clearAccounts()
            snapshot.accounts.forEach { dao.insertAccount(it) }
            snapshot.categories.forEach { dao.insertCategory(it) }
            snapshot.transactions.forEach { dao.insertTransaction(it) }
            snapshot.favorites.forEach { dao.insertFavorite(it) }
            dao.setSettings(SettingsEntity(defaultAccountId = snapshot.defaultAccountId))
            dao.ensureCategoryCatalog()
        }
        recentFavorites.clear()
    }
}
