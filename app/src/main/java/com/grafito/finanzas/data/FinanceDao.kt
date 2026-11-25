package com.grafito.finanzas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM categories ORDER BY id")
    fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun transactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM favorites ORDER BY id DESC")
    fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM accounts ORDER BY id") suspend fun allAccounts(): List<AccountEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM favorites ORDER BY id DESC") suspend fun allFavorites(): List<FavoriteEntity>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun currentSettings(): SettingsEntity?
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun transaction(id: Long): TransactionEntity?
    @Query("SELECT id FROM transactions ORDER BY id LIMIT 2") suspend fun firstTransactionIds(): List<Long>
    @Query("""
        SELECT EXISTS(SELECT 1 FROM transactions
        WHERE receiptFingerprint IN (:fingerprint, :operationFingerprint)
           OR bcpOperationFingerprint IN (:fingerprint, :operationFingerprint))
    """)
    suspend fun hasReceipt(fingerprint: String, operationFingerprint: String? = null): Boolean
    @Query("""
        UPDATE transactions SET bcpOperationFingerprint = :operationFingerprint
        WHERE receiptFingerprint = :fingerprint AND bcpOperationFingerprint IS NULL
    """)
    suspend fun attachBcpOperation(fingerprint: String, operationFingerprint: String): Int
    @Query("SELECT * FROM favorites WHERE id = :id") suspend fun favorite(id: Long): FavoriteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSettings(value: SettingsEntity)
    @Update suspend fun updateFavorite(value: FavoriteEntity)
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM favorites") suspend fun clearFavorites()
    @Query("DELETE FROM settings") suspend fun clearSettings()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountsCount(): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countAccountTransactions(accountId: Long): Int

    @Insert suspend fun insertAccount(value: AccountEntity): Long
    @Insert suspend fun insertCategory(value: CategoryEntity): Long
    @Insert suspend fun insertTransaction(value: TransactionEntity): Long
    @Insert suspend fun restoreTransaction(value: TransactionEntity): Long
    @Update suspend fun updateTransaction(value: TransactionEntity)
    @Delete suspend fun deleteTransaction(value: TransactionEntity)
    @Delete suspend fun deleteAccount(value: AccountEntity)
    @Insert suspend fun insertFavorite(value: FavoriteEntity): Long
    @Delete suspend fun deleteFavorite(value: FavoriteEntity)
}
