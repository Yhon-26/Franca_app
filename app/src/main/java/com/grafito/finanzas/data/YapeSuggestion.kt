package com.grafito.finanzas.data

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "yape_suggestions")
data class YapeSuggestion(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val receivedAt: Long,
    val pending: Boolean = true,
    @ColumnInfo(defaultValue = "0") val outgoing: Boolean = false
)

internal data class YapeConfirmation(val suggestion: YapeSuggestion, val transaction: TransactionEntity)

@Dao
interface YapeSuggestionDao {
    @Query("SELECT * FROM yape_suggestions WHERE pending = 1 ORDER BY receivedAt DESC LIMIT 20")
    fun pending(): Flow<List<YapeSuggestion>>

    @Query("SELECT * FROM yape_suggestions WHERE id = :id AND pending = 1")
    suspend fun findPending(id: String): YapeSuggestion?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: YapeSuggestion): Long

    @Query("UPDATE yape_suggestions SET pending = 0, amountCents = 0 WHERE id = :id AND pending = 1")
    suspend fun dismiss(id: String): Int

    @Query("""
        UPDATE yape_suggestions SET pending = 1, amountCents = :amountCents
        WHERE id = :id AND pending = 0 AND amountCents = 0 AND receivedAt = :receivedAt AND outgoing = :outgoing
    """)
    suspend fun reopen(id: String, amountCents: Long, receivedAt: Long, outgoing: Boolean): Int

    @Query("UPDATE yape_suggestions SET pending = 0, amountCents = 0 WHERE pending = 1")
    suspend fun dismissAll()

    @Query("DELETE FROM yape_suggestions WHERE receivedAt < :cutoff")
    suspend fun prune(cutoff: Long)
}

internal class YapeInbox(private val database: FinanceDatabase) {
    private val suggestions = database.yapeSuggestions()
    val pending = suggestions.pending()

    suspend fun record(suggestion: YapeSuggestion): Boolean = database.withTransaction {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        suggestions.prune(cutoff)
        suggestion.receivedAt >= cutoff &&
            (!suggestion.outgoing || !database.dao().hasReceipt(suggestion.id)) &&
            suggestions.insert(suggestion) != -1L
    }

    suspend fun discard(id: String) = suggestions.dismiss(id)

    suspend fun expireOld() = suggestions.prune(System.currentTimeMillis() - RETENTION_MILLIS)

    suspend fun confirm(id: String, yapeAccountId: Long, amountCents: Long, categoryId: Long? = null,
                        otherAccountId: Long? = null): Boolean =
        confirmForReview(id, yapeAccountId, amountCents, categoryId, otherAccountId) != null

    suspend fun confirmForReview(id: String, yapeAccountId: Long, amountCents: Long, categoryId: Long?,
                                 otherAccountId: Long?): YapeConfirmation? = database.withTransaction {
        if (amountCents !in 1..com.grafito.finanzas.model.Money.MAX_CENTS) return@withTransaction null
        val candidate = suggestions.findPending(id) ?: return@withTransaction null
        val dao = database.dao()
        val accounts = dao.allAccounts()
        if (accounts.none { it.id == yapeAccountId }) return@withTransaction null
        if (otherAccountId != null && (otherAccountId == yapeAccountId || accounts.none { it.id == otherAccountId })) {
            return@withTransaction null
        }
        val type = when {
            otherAccountId != null -> EntryType.TRANSFER
            candidate.outgoing -> EntryType.EXPENSE
            else -> EntryType.INCOME
        }
        if (categoryId != null && (type == EntryType.TRANSFER ||
                dao.allCategories().none { it.id == categoryId && it.type == type })) {
            return@withTransaction null
        }
        if (suggestions.dismiss(id) != 1) return@withTransaction null
        val entry = TransactionEntity(
            type = type,
            amountCents = amountCents,
            accountId = if (candidate.outgoing) yapeAccountId else otherAccountId ?: yapeAccountId,
            toAccountId = otherAccountId?.let { if (candidate.outgoing) it else yapeAccountId },
            categoryId = categoryId,
            note = when (type) {
                EntryType.INCOME -> "Yape recibido"
                EntryType.EXPENSE -> "Yape enviado"
                else -> if (candidate.outgoing) "Transferencia desde Yape" else "Transferencia a Yape"
            },
            occurredAt = candidate.receivedAt,
            receiptFingerprint = id.takeIf { candidate.outgoing }
        )
        YapeConfirmation(candidate, entry.copy(id = dao.insertTransaction(entry)))
    }

    suspend fun undoConfirmation(confirmation: YapeConfirmation): Boolean = database.withTransaction {
        val entry = confirmation.transaction
        val original = confirmation.suggestion
        val dao = database.dao()
        if (dao.transaction(entry.id) != entry) return@withTransaction false
        if (suggestions.reopen(original.id, original.amountCents, original.receivedAt, original.outgoing) != 1) {
            return@withTransaction false
        }
        dao.deleteTransaction(entry)
        true
    }

    private companion object {
        const val RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
