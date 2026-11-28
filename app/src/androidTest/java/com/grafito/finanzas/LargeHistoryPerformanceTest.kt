package com.grafito.finanzas

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.BackupSnapshot
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.data.FinanceRepository
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.data.UserPreferences
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.filterMovements
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil

/** Opt in with am instrument -e francaLargeHistory true -e class com.grafito.finanzas.LargeHistoryPerformanceTest. */
class LargeHistoryPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun openSearchAndChangeMonthWithTwentyFiveThousandMovements() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("francaLargeHistory") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.grafito.finanzas.validation")
        val database = FinanceDatabase.get(context)
        val repository = FinanceRepository(database)
        val preferences = UserPreferences(context)
        val (original, openQuick) = runBlocking {
            database.initialize()
            repository.snapshot() to preferences.openQuickOnLaunch.first()
        }

        val zone = ZoneId.systemDefault()
        val current = YearMonth.now(zone)
        val currentRange = FinanceMath.monthRange(current, zone)
        val previousRange = FinanceMath.monthRange(current.minusMonths(1), zone)
        val accounts = listOf(AccountEntity(1, "Efectivo"), AccountEntity(2, "Yape"))
        val categories = listOf(
            CategoryEntity(101, "Gastos de referencia", "", EntryType.EXPENSE),
            CategoryEntity(102, "Comparación de gastos", "", EntryType.EXPENSE),
            CategoryEntity(103, "Historial de prueba", "", EntryType.EXPENSE)
        )
        val entries = List(24_998) { index ->
            val month = current.minusMonths(2L + index / 800)
            TransactionEntity(index + 1L, EntryType.EXPENSE, 1250L, index % 2 + 1L, categoryId = 103,
                note = "Consumo archivado número $index",
                occurredAt = FinanceMath.monthRange(month, zone).first + index % 800 * 60_000L)
        } + listOf(
            TransactionEntity(24_999, EntryType.EXPENSE, 2040, 1, categoryId = 102,
                note = "Compra focolima", occurredAt = previousRange.first + 60_000),
            TransactionEntity(25_000, EntryType.EXPENSE, 1550, 2, categoryId = 101,
                note = "Movimiento reciente de prueba", occurredAt = currentRange.first + 60_000)
        )

        try {
            runBlocking {
                repository.restore(BackupSnapshot(accounts, categories, entries, emptyList(), 1))
                preferences.setOpenQuick(false)
            }
            val dao = database.dao()
            val all = runBlocking { dao.allTransactions() }
            assertEquals(25_000, all.size)
            val accountMap = accounts.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }
            measure("room_read_all", 15) { check(runBlocking { dao.allTransactions() }.size == all.size) }
            measure("search_logic", 15) {
                check(filterMovements(all, accountMap, categoryMap, "focolima", null).single().id == 24_999L)
            }
            measure("report_logic", 15) {
                check(FinanceMath.monthlyReport(all, previousRange).totals.expenses == 2040L)
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                val history = compose.onNodeWithContentDescription("Historial")
                val reports = compose.onNodeWithContentDescription("Reportes")
                val home = compose.onNodeWithContentDescription("Inicio")
                val search = compose.onNode(hasSetTextAction() and hasText("Buscar movimiento"))
                fun awaitText(text: String) = compose.waitUntil(15_000) { compose.onNodeWithText(text).isDisplayed() }
                awaitText("Saldo total")
                measure("ui_open_history", 7, before = {
                    home.performClick()
                    awaitText("Saldo total")
                }) {
                    history.performClick()
                    awaitText("Movimiento reciente de prueba")
                }
                measure("ui_search", 7, before = {
                    search.performTextReplacement("")
                    awaitText("Movimiento reciente de prueba")
                }) {
                    search.performTextReplacement("focolima")
                    awaitText("Compra focolima")
                }
                measure("ui_switch_report_month", 7, before = {
                    reports.performClick()
                    if (compose.onNodeWithText("Comparación de gastos").isDisplayed()) {
                        compose.onNodeWithContentDescription("Mes siguiente").performClick()
                    }
                    awaitText("Gastos de referencia")
                }) {
                    compose.onNodeWithContentDescription("Mes anterior").performClick()
                    awaitText("Comparación de gastos")
                }
            }
        } finally {
            runBlocking {
                repository.restore(original)
                preferences.setOpenQuick(openQuick)
            }
        }
    }

    private fun measure(name: String, samples: Int, before: () -> Unit = {}, action: () -> Unit) {
        repeat(2) { before(); action() }
        val millis = List(samples) {
            before()
            val start = SystemClock.elapsedRealtimeNanos()
            action()
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }.sorted()
        Log.i("FrancaHistoryPerf", String.format(Locale.ROOT,
            "%s n=%d median=%.2fms p90=%.2fms", name, samples, millis[samples / 2], millis[ceil(samples * 0.9).toInt() - 1]))
    }
}
