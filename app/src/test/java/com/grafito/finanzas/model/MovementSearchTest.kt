package com.grafito.finanzas.model

import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MovementSearchTest {
    private val accounts = listOf(AccountEntity(1, "Efectivo"), AccountEntity(2, "Yape")).associateBy { it.id }
    private val categories = listOf(CategoryEntity(1, "Educación", "", EntryType.EXPENSE)).associateBy { it.id }
    private val entries = listOf(
        TransactionEntity(1, EntryType.EXPENSE, 1250, 1, categoryId = 1, note = "Útiles escolares"),
        TransactionEntity(2, EntryType.INCOME, 11250, 2, note = "Yape recibido"),
        TransactionEntity(3, EntryType.TRANSFER, 123450, 1, toAccountId = 2, note = "Ahorro")
    )

    private fun find(query: String, type: String? = null) =
        filterMovements(entries, accounts, categories, query, type).map { it.id }

    @Test fun amountsMatchWithEitherDecimalSeparator() {
        assertEquals(listOf(1L), find("12,50"))
        assertEquals(listOf(1L), find("S/ 12.50"))
        assertEquals(listOf(3L), find("1,234.50"))
        assertEquals(listOf(3L), find("1.234,50"))
    }

    @Test fun textSearchIncludesBothAccountsAndIgnoresAccentsAndCase() {
        assertEquals(listOf(2L, 3L), find("YAPE"))
        assertEquals(listOf(1L), find("educacion UTILES"))
        assertEquals(listOf(1L), find("efectivo 12,50"))
        assertEquals(listOf(3L), find("yape efectivo"))
    }

    @Test fun filteringByAccountIncludesBothSidesOfTransfersAndCombinesWithTypeAndDate() {
        val dated = entries.mapIndexed { index, entry -> entry.copy(occurredAt = 10L + index) }
        assertEquals(listOf(1L, 3L), filterMovements(dated, accounts, categories, "", null, accountId = 1).map { it.id })
        assertEquals(listOf(2L, 3L), filterMovements(dated, accounts, categories, "", null, accountId = 2).map { it.id })
        assertEquals(listOf(3L), filterMovements(dated, accounts, categories, "Ahorro", EntryType.TRANSFER,
            accountId = 2, period = 11L..12L).map { it.id })
        assertEquals(emptyList<TransactionEntity>(), filterMovements(dated, accounts, categories, "Ahorro", EntryType.TRANSFER,
            accountId = 2, period = 10L..11L))
    }

    @Test fun periodsUseTheLocalDayAndIncludeTheFullLastDate() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 1)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val next = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(start until next, MovementDateFilter.CUSTOM.range(day, zone, day, day))
        assertEquals(25 * 60 * 60 * 1000L, next - start)
        assertEquals(start until next, MovementDateFilter.TODAY.range(day, zone, null, null))
        val previousMonth = MovementDateFilter.LAST_MONTH.range(day, zone, null, null)
        assertEquals(start - 1L, previousMonth?.last)
        val lastYear = MovementDateFilter.LAST_MONTH.range(LocalDate.of(2026, 1, 1), zone, null, null)
        assertEquals(LocalDate.of(2025, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli(), lastYear?.first)
    }

    @Test fun searchCombinesWithTypeAndPreservesOrder() {
        assertEquals(listOf(1L, 2L, 3L), find(""))
        assertEquals(listOf(3L), find("Yape", EntryType.TRANSFER))
        assertEquals(emptyList<Long>(), find("Educación", EntryType.INCOME))
    }
}
