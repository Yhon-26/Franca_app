package com.grafito.finanzas.model

import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.data.categoryNameKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val decimalAmount = Regex("(?:[0-9]{1,9}|[0-9]{1,3}(?:[.,][0-9]{3})+)[.,][0-9]{1,2}")

private data class MovementSearchTerm(val text: String, val cents: Long?, val isWholeNumber: Boolean)

internal fun filterMovements(
    transactions: List<TransactionEntity>,
    accounts: Map<Long, AccountEntity>,
    categories: Map<Long, CategoryEntity>,
    query: String,
    type: String?,
    accountId: Long? = null,
    period: LongRange? = null
): List<TransactionEntity> {
    val terms = categoryNameKey(query).replace("s/", " ").split(' ').filter(String::isNotEmpty).map { text ->
        val amount = text.trimStart('+', '-', '−')
        val cents = if (decimalAmount.matches(amount)) {
            val separator = maxOf(amount.lastIndexOf('.'), amount.lastIndexOf(','))
            Money.parse(amount.substring(0, separator).filter(Char::isDigit) + "." + amount.substring(separator + 1))
        } else null
        MovementSearchTerm(text, cents, text.all { it in '0'..'9' })
    }
    if (terms.isEmpty() && type == null && accountId == null && period == null) return transactions

    return transactions.filter { entry ->
        if (type != null && entry.type != type) return@filter false
        if (period != null && entry.occurredAt !in period) return@filter false
        if (accountId != null && accountId != entry.accountId && accountId != entry.toAccountId) return@filter false
        if (terms.isEmpty()) return@filter true
        val typeName = when (entry.type) {
            EntryType.INCOME -> "Ingreso"
            EntryType.TRANSFER -> "Transferencia"
            else -> "Gasto"
        }
        val fields = categoryNameKey(
            listOfNotNull(
                typeName,
                entry.note,
                accounts[entry.accountId]?.name,
                entry.toAccountId?.let { accounts[it]?.name },
                categories[entry.categoryId]?.name
            ).joinToString(" ")
        )
        val wholeAmount = (entry.amountCents / 100).toString()
        terms.all { term ->
            term.text in fields || term.cents == entry.amountCents ||
                (term.isWholeNumber && term.text in wholeAmount)
        }
    }
}

internal enum class MovementDateFilter {
    ALL, TODAY, THIS_MONTH, LAST_MONTH, CUSTOM;

    fun range(today: LocalDate, zone: ZoneId, start: LocalDate?, end: LocalDate?): LongRange? = when (this) {
        ALL -> null
        TODAY -> localDateRange(today, today, zone)
        THIS_MONTH -> FinanceMath.monthRange(YearMonth.from(today), zone)
        LAST_MONTH -> FinanceMath.monthRange(YearMonth.from(today).minusMonths(1), zone)
        CUSTOM -> if (start != null && end != null && start <= end) localDateRange(start, end, zone) else null
    }

    private fun localDateRange(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): LongRange =
        start.atStartOfDay(zone).toInstant().toEpochMilli() until
            endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
