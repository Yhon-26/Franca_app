package com.grafito.finanzas.model

import com.grafito.finanzas.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class FinanceMathTest {
    @Test fun transfersPreserveTotalAndOpeningIsNotIncome() {
        val cash = AccountEntity(1, "Efectivo", 10_000)
        val yape = AccountEntity(2, "Yape", 2_000)
        val entries = listOf(
            TransactionEntity(1, EntryType.INCOME, 5_000, 1),
            TransactionEntity(2, EntryType.EXPENSE, 150, 1),
            TransactionEntity(3, EntryType.TRANSFER, 3_000, 1, 2))
        val balances = FinanceMath.accountBalances(listOf(cash, yape), entries)
        assertEquals(mapOf(1L to 11_850L, 2L to 5_000L), balances)
        assertEquals(16_850L, balances.values.sum())
        assertEquals(MonthlyTotals(5_000, 150), FinanceMath.monthlyTotals(entries, Long.MIN_VALUE..Long.MAX_VALUE))
    }

    @Test fun monthBoundaryUsesLocalYearAndMonth() {
        val january = FinanceMath.monthRange(YearMonth.of(2027, 1), ZoneId.of("America/Lima"))
        val start = ZonedDateTime.parse("2027-01-01T00:00:00-05:00").toInstant().toEpochMilli()
        val nextMonth = ZonedDateTime.parse("2027-02-01T00:00:00-05:00").toInstant().toEpochMilli()
        val entries = listOf(
            TransactionEntity(type = EntryType.INCOME, amountCents = 100, accountId = 1, occurredAt = start - 1),
            TransactionEntity(type = EntryType.INCOME, amountCents = 200, accountId = 1, occurredAt = start),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 300, accountId = 1, occurredAt = nextMonth - 1),
            TransactionEntity(type = EntryType.EXPENSE, amountCents = 400, accountId = 1, occurredAt = nextMonth)
        )
        assertEquals(MonthlyTotals(200, 300), FinanceMath.monthlyTotals(entries, january))
    }

    @Test fun monthlyReportTotalsReconcileWithCategoryDetails() {
        val range = FinanceMath.monthRange(YearMonth.of(2027, 2), ZoneId.of("America/Lima"))
        val entries = listOf(
            TransactionEntity(1, EntryType.EXPENSE, 500, 1, categoryId = 1, occurredAt = range.first),
            TransactionEntity(2, EntryType.EXPENSE, 750, 1, categoryId = 1, occurredAt = range.last),
            TransactionEntity(3, EntryType.EXPENSE, 300, 1, occurredAt = range.first),
            TransactionEntity(4, EntryType.INCOME, 2000, 1, categoryId = 2, occurredAt = range.first),
            TransactionEntity(5, EntryType.TRANSFER, 800, 1, toAccountId = 2, occurredAt = range.first),
            TransactionEntity(6, EntryType.EXPENSE, 100, 1, categoryId = 1, occurredAt = range.first - 1),
            TransactionEntity(7, EntryType.EXPENSE, 100, 1, occurredAt = range.last + 1)
        )
        val report = FinanceMath.monthlyReport(entries, range)
        assertEquals(MonthlyTotals(2000, 1550), report.totals)
        assertEquals(listOf(CategorySpending(1L, 1250), CategorySpending(null, 300)), report.expensesByCategory)
        assertEquals(listOf(1L, 2L), FinanceMath.reportExpenses(entries, range, 1).map { it.id })
        assertEquals(listOf(3L), FinanceMath.reportExpenses(entries, range, null).map { it.id })
    }
}
