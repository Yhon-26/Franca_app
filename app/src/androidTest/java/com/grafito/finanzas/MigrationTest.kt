package com.grafito.finanzas

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grafito.finanzas.data.FinanceDatabase
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOnePreservesAccountsTransactionsAndFavorites() = validateMigration(1)

    @Test fun versionTwoPreservesAccountsAndAddsEmptyInbox() = validateMigration(2)

    @Test fun versionThreePreservesExistingMovementsAndAddsReceiptFingerprint() = validateMigration(3)
    @Test fun versionFourPreservesExistingMovementsAndAddsBcpMailInbox() = validateMigration(4)
    @Test fun versionFivePreservesLegacyReceiptAndPendingMail() = validateMigration(5)
    @Test fun versionSixPreservesYapeIncomeSuggestions() = validateMigration(6)

    private fun validateMigration(source: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "grafito-migration-test-$source"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.grafito.finanzas.data.FinanceDatabase/$source.json").use {
            JSONObject(it.bufferedReader().readText()).getJSONObject("database")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(source) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        helper.writableDatabase.apply {
            execSQL("INSERT INTO accounts (id,name,openingBalanceCents,createdAt) VALUES (7,'Efectivo',10000,1)")
            execSQL("INSERT INTO transactions (id,type,amountCents,accountId,note,occurredAt) VALUES (8,'EXPENSE',150,7,'Pasaje',2)")
            execSQL("INSERT INTO favorites (id,title,amountCents) VALUES (9,'Pasaje',150)")
            if (source >= 2) {
                execSQL("UPDATE favorites SET accountId = 7 WHERE id = 9")
                execSQL("INSERT INTO settings (id,defaultAccountId) VALUES (1,7)")
            }
            if (source == 5) {
                execSQL("UPDATE transactions SET receiptFingerprint = ? WHERE id = 8", arrayOf("a".repeat(64)))
                execSQL("INSERT INTO bcp_mail_suggestions VALUES (?,150,2,1)", arrayOf("b".repeat(64)))
            }
            if (source == 6) {
                execSQL("INSERT INTO yape_suggestions (id,amountCents,receivedAt,pending) VALUES ('legacy-yape',1250,2,1)")
            }
        }
        helper.close()
        val migrated = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3, FinanceDatabase.MIGRATION_3_4,
                FinanceDatabase.MIGRATION_4_5, FinanceDatabase.MIGRATION_5_6, FinanceDatabase.MIGRATION_6_7).build()
        try { migrated.openHelper.writableDatabase.apply {
            query("SELECT amountCents, accountId, receiptFingerprint, bcpOperationFingerprint FROM transactions WHERE id=8").use {
                assertTrue(it.moveToFirst())
                assertEquals(150, it.getLong(0))
                assertEquals(7, it.getLong(1))
                if (source == 5) assertEquals("a".repeat(64), it.getString(2)) else assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
            }
            query("SELECT title, accountId FROM favorites WHERE id=9").use {
                assertTrue(it.moveToFirst())
                assertEquals("Pasaje", it.getString(0))
                if (source == 1) assertTrue(it.isNull(1)) else assertEquals(7, it.getLong(1))
            }
            query("SELECT id, amountCents, outgoing, pending FROM yape_suggestions").use {
                if (source == 6) {
                    assertTrue(it.moveToNext())
                    assertEquals("legacy-yape", it.getString(0))
                    assertEquals(1250L, it.getLong(1))
                    assertEquals(0, it.getInt(2))
                    assertEquals(1, it.getInt(3))
                }
                assertFalse(it.moveToNext())
            }
            query("SELECT id, amountCents, pending FROM bcp_mail_suggestions").use {
                if (source == 5) {
                    assertTrue(it.moveToNext())
                    assertEquals("b".repeat(64), it.getString(0))
                    assertEquals(150L, it.getLong(1))
                    assertEquals(1, it.getInt(2))
                }
                assertFalse(it.moveToNext())
            }
        } } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
