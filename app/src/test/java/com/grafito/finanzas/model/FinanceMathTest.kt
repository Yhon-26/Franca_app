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
}
