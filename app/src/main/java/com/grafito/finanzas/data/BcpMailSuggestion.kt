package com.grafito.finanzas.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bcp_mail_suggestions")
data class BcpMailSuggestion(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val occurredAt: Long,
    val pending: Boolean = true
)

@Dao
interface BcpMailDao {
    @Query("SELECT * FROM bcp_mail_suggestions WHERE pending = 1 ORDER BY occurredAt DESC")
    fun pending(): Flow<List<BcpMailSuggestion>>

    @Query("SELECT * FROM bcp_mail_suggestions WHERE id = :id AND pending = 1")
    suspend fun find(id: String): BcpMailSuggestion?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: BcpMailSuggestion): Long

    @Query("UPDATE bcp_mail_suggestions SET pending = 0, amountCents = 0 WHERE id = :id AND pending = 1")
    suspend fun dismiss(id: String)

    @Query("UPDATE bcp_mail_suggestions SET pending = 0, amountCents = 0 WHERE pending = 1")
    suspend fun dismissAll()
}

internal class BcpMailInbox(private val database: FinanceDatabase) {
    private val dao = database.bcpMailSuggestions()
    private val repository = FinanceRepository(database)

    val pending = dao.pending()

    suspend fun findPending(id: String): BcpMailSuggestion? = dao.find(id)

    suspend fun record(suggestion: BcpMailSuggestion): Boolean = database.withTransaction {
        !database.dao().hasReceipt(suggestion.id) && dao.insert(suggestion) != -1L
    }

    suspend fun confirm(id: String, cents: Long, accountId: Long, destinationId: Long?,
                        note: String, categoryId: Long?): ReceiptSaveResult = database.withTransaction {
        val suggestion = dao.find(id) ?: return@withTransaction ReceiptSaveResult.DUPLICATE
        val result = repository.saveBcpReceipt(id, cents, accountId, destinationId, note, categoryId, suggestion.occurredAt)
        if (result != ReceiptSaveResult.INVALID) dao.dismiss(id)
        result
    }
}
