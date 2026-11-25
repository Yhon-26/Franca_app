package com.grafito.finanzas.model

import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import java.time.YearMonth
import java.time.ZoneId

internal data class MonthlyTotals(val income: Long, val expenses: Long)

object FinanceMath {
    fun accountBalances(accounts: List<AccountEntity>, entries: List<TransactionEntity>): Map<Long, Long> {
        val balances = accounts.associateTo(mutableMapOf()) { it.id to it.openingBalanceCents }
        for (entry in entries) {
            val cents = entry.amountCents
            when (entry.type) {
                EntryType.INCOME -> balances.computeIfPresent(entry.accountId) { _, balance -> balance + cents }
                EntryType.EXPENSE -> balances.computeIfPresent(entry.accountId) { _, balance -> balance - cents }
                EntryType.TRANSFER -> {
                    balances.computeIfPresent(entry.accountId) { _, balance -> balance - cents }
                    val destination = entry.toAccountId
                    if (destination != null && destination != entry.accountId) {
                        balances.computeIfPresent(destination) { _, balance -> balance + cents }
                    }
                }
            }
        }
        return balances
    }

    internal fun monthRange(month: YearMonth, zone: ZoneId): LongRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until next
    }

    internal fun monthlyTotals(entries: List<TransactionEntity>, month: LongRange): MonthlyTotals {
        var income = 0L
        var expenses = 0L
        for (entry in entries) {
            if (entry.occurredAt !in month) continue
            when (entry.type) {
                EntryType.INCOME -> income += entry.amountCents
                EntryType.EXPENSE -> expenses += entry.amountCents
            }
        }
        return MonthlyTotals(income, expenses)
    }
}
