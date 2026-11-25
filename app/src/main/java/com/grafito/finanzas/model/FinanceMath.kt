package com.grafito.finanzas.model

import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import java.time.YearMonth
import java.time.ZoneId

internal data class MonthlyTotals(val income: Long, val expenses: Long)
internal data class MonthlyReport(val totals: MonthlyTotals, val expensesByCategory: List<CategorySpending>)
internal data class CategorySpending(val categoryId: Long?, val cents: Long)
internal data class ReportExpenseSelection(val month: YearMonth, val categoryId: Long?)

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

    internal fun monthlyReport(entries: List<TransactionEntity>, month: LongRange): MonthlyReport {
        val spending = entries.asSequence()
            .filter { it.isExpenseIn(month) }
            .groupingBy { it.categoryId }
            .fold(0L) { sum, entry -> sum + entry.amountCents }
            .map { (id, cents) -> CategorySpending(id, cents) }
            .sortedByDescending { it.cents }
        return MonthlyReport(monthlyTotals(entries, month), spending)
    }

    internal fun reportExpenses(entries: List<TransactionEntity>, month: LongRange, categoryId: Long?): List<TransactionEntity> =
        entries.filter { it.isExpenseIn(month) && it.categoryId == categoryId }

    private fun TransactionEntity.isExpenseIn(month: LongRange): Boolean =
        type == EntryType.EXPENSE && occurredAt in month
}
