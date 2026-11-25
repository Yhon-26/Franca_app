package com.grafito.finanzas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, FavoriteEntity::class, SettingsEntity::class, YapeSuggestion::class, BcpMailSuggestion::class], version = 7, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    abstract fun yapeSuggestions(): YapeSuggestionDao
    abstract fun bcpMailSuggestions(): BcpMailDao

    /** Sin datos ficticios: solo la cuenta Efectivo con saldo 0 y categorías. */
    suspend fun initialize() = withTransaction {
        val dao = dao()
        if (dao.accountsCount() == 0) dao.insertAccount(AccountEntity(name = "Efectivo"))
        dao.ensureCategoryCatalog()
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE favorites_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, categoryId INTEGER, accountId INTEGER, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO favorites_new (id, title, amountCents, categoryId) SELECT id, title, amountCents, categoryId FROM favorites")
                db.execSQL("DROP TABLE favorites")
                db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")
                db.execSQL("CREATE INDEX index_favorites_categoryId ON favorites(categoryId)")
                db.execSQL("CREATE INDEX index_favorites_accountId ON favorites(accountId)")
                db.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, defaultAccountId INTEGER, FOREIGN KEY(defaultAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX index_settings_defaultAccountId ON settings(defaultAccountId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE yape_suggestions (id TEXT NOT NULL PRIMARY KEY, amountCents INTEGER NOT NULL, receivedAt INTEGER NOT NULL, pending INTEGER NOT NULL)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptFingerprint TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_transactions_receiptFingerprint ON transactions(receiptFingerprint)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE bcp_mail_suggestions (id TEXT NOT NULL PRIMARY KEY, amountCents INTEGER NOT NULL, occurredAt INTEGER NOT NULL, pending INTEGER NOT NULL)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN bcpOperationFingerprint TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_transactions_bcpOperationFingerprint ON transactions(bcpOperationFingerprint)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE yape_suggestions ADD COLUMN outgoing INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "grafito_finances.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build().also { instance = it }
        }
    }
}
