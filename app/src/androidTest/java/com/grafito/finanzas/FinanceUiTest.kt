package com.grafito.finanzas

import android.app.Activity
import android.app.Application
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.screens.reportMonthLabel
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Runs only in the separate .validation application; never clears the user's debug installation. */
class FinanceUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var repository: FinanceRepository
    private val fixture = object : ExternalResource() {
        override fun before() = runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            check(app.packageName.endsWith(".validation"))
            repository = FinanceRepository(FinanceDatabase.get(app))
            repository.restore(BackupSnapshot(
                listOf(AccountEntity(1, "Efectivo"), AccountEntity(2, "Yape", 10_000)),
                listOf(CategoryEntity(1, "Transporte", "", EntryType.EXPENSE), CategoryEntity(2, "Ventas", "", EntryType.INCOME)),
                emptyList(), emptyList(), 2))
            UserPreferences(app).setOpenQuick(false)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun interfaceSoundSettingSurvivesRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferences(app)
        val original = runBlocking { prefs.interfaceSounds.first() }
        runBlocking { prefs.setInterfaceSounds(true) }
        try {
            compose.onNodeWithContentDescription("Ajustes").performClick()
            compose.onNodeWithText("Toques y tonos de Franca").performClick()
            val switch = compose.onNodeWithText("Sonidos en la app")
            switch.assertIsOn().performClick()
            compose.waitUntil(5_000) { !runBlocking { prefs.interfaceSounds.first() } }
            compose.activityRule.scenario.recreate()
            switch.assertIsOff()
            compose.onNodeWithText("Escuchar confirmación").assertDoesNotExist()
        } finally {
            runBlocking { prefs.setInterfaceSounds(original) }
        }
    }

    @Test fun firstMovementMascotLeavesUndoAvailableAndDoesNotReplay() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app.packageName.endsWith(".validation"))
        runBlocking { app.financePreferences.edit { it.remove(firstMovementReactionKey) } }
        try {
            fun saveExpense() {
                compose.onNodeWithContentDescription("Registrar movimiento").performClick()
                compose.onNodeWithContentDescription("Monto").performTextInput("1.50")
                compose.onNodeWithText("Guardar gasto").performClick()
            }

            saveExpense()
            val mascot = compose.onNodeWithTag("first_movement_mascot")
            compose.waitUntil(5_000) { mascot.isDisplayed() }
            compose.onNodeWithText("Gasto guardado").assertIsDisplayed()
            compose.onNodeWithText("Deshacer").assertIsDisplayed()
            compose.waitUntil(5_000) { !mascot.isDisplayed() }
            compose.onNodeWithText("Deshacer").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.isEmpty() } }

            saveExpense()
            val savedNotice = compose.onNodeWithText("Gasto guardado")
            compose.waitUntil(10_000) {
                savedNotice.isDisplayed()
            }
            compose.onNodeWithText("Deshacer").assertIsDisplayed()
            compose.onNodeWithContentDescription("Cerrar aviso").assertIsDisplayed()
            mascot.assertDoesNotExist()
            assertTrue(savedNotice.fetchSemanticsNode().boundsInWindow.bottom <
                compose.onNodeWithText("Saldo total").fetchSemanticsNode().boundsInWindow.top)
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { image ->
                try {
                    File(app.getExternalFilesDir(null), "franca-aviso-guardado.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    image.recycle()
                }
            }
            compose.waitUntil(6_000) { !savedNotice.isDisplayed() }
            compose.onNodeWithContentDescription("Cerrar aviso").assertDoesNotExist()
            assertEquals(1, runBlocking { repository.snapshot().transactions.size })
        } finally {
            runBlocking { app.financePreferences.edit { it.remove(firstMovementReactionKey) } }
        }
    }

    @Test fun amountOnlyUsesDefaultAccountAndUndoRestoresBalance() {
        compose.onNodeWithText("Aún no tienes movimientos").assertIsDisplayed()
        compose.onNodeWithText("Activa Yape en Ajustes para registrar tus pagos automáticamente.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Registrar movimiento").performClick()
        compose.onNodeWithText("Desde Yape").assertIsDisplayed()
        compose.onNodeWithText("Nota (opcional)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Monto").performTextInput("1.50")
        compose.onNodeWithText("Guardar gasto").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.size == 1 } }
        val entry = runBlocking { repository.snapshot().transactions.single() }
        assertEquals(150, entry.amountCents)
        assertEquals(2, entry.accountId)
        assertNull(entry.categoryId)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Deshacer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aún no tienes movimientos").assertDoesNotExist()
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.isEmpty() } }
        compose.onNodeWithText("Aún no tienes movimientos").assertIsDisplayed()
    }

    @Test fun incomeCanChooseAnotherAccountAndCategoryAndSaveANote() {
        compose.onNodeWithContentDescription("Registrar movimiento").performClick()
        compose.onNodeWithText("Ingreso").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Monto").performTextInput("12.50")
        compose.onNodeWithContentDescription("Elegir cuenta").performClick()
        compose.onNodeWithText("Yape").assertIsSelected()
        compose.onNodeWithText("Efectivo").performClick()
        compose.onNodeWithText("A Efectivo").assertIsDisplayed()
        compose.onNodeWithText("Categoría").performClick()
        compose.onNodeWithText("Registrar · Ingreso").assertIsDisplayed()
        compose.onNodeWithText("Sin categoría").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver al formulario").performClick()
        compose.onNodeWithContentDescription("Monto").assertTextContains("12.50")
        compose.onNodeWithText("Categoría").performClick()
        compose.onNodeWithContentDescription("Buscar categoría").performTextInput("VÉNT")
        compose.onNodeWithText("Ventas").performClick()
        compose.onNodeWithText("Categoría").performClick()
        compose.onNodeWithText("Sin categoría").performClick()
        compose.onNodeWithText("Opcional").assertIsDisplayed()
        compose.onNodeWithText("Categoría").performClick()
        compose.onNodeWithText("Ventas").performClick()
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithText("Nota (opcional)").performScrollTo().performTextInput("Pedido")
        compose.onNodeWithText("Guardar ingreso").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.size == 1 } }
        val entry = runBlocking { repository.snapshot().transactions.single() }
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals(1L, entry.accountId)
        assertEquals(2L, entry.categoryId)
        assertEquals(1250L, entry.amountCents)
        assertEquals("Pedido", entry.note)
    }

    @Test fun shortcutPrefillsADraftAndOnlySavesOnConfirmation() {
        runBlocking {
            val initial = repository.snapshot()
            val now = System.currentTimeMillis()
            repository.restore(initial.copy(
                accounts = listOf(AccountEntity(1, "Efectivo", 135_000), AccountEntity(2, "Yape", 796_208), AccountEntity(3, "BCP")),
                categories = listOf(CategoryEntity(1, "Transporte", ""), CategoryEntity(2, "Alimentación", ""),
                    CategoryEntity(3, "Sueldo", "", EntryType.INCOME)),
                transactions = listOf(
                    TransactionEntity(1, EntryType.EXPENSE, 1000, 2, categoryId = 2, note = "Menú", occurredAt = now),
                    TransactionEntity(2, EntryType.EXPENSE, 150, 1, categoryId = 1, note = "Pasaje", occurredAt = now - 60_000),
                    TransactionEntity(3, EntryType.INCOME, 320_000, 3, categoryId = 3, occurredAt = now - 120_000)
                ),
                favorites = listOf(
                    FavoriteEntity(1, "Café", 500, 2, 1),
                    FavoriteEntity(2, "Pasaje", 350, 1, 1)
                )
            ))
        }
        compose.onNodeWithContentDescription("Inicio").assertIsSelected()
        compose.onNodeWithText(Money.format(1_250_058)).assertIsDisplayed()
        compose.onNodeWithText("+ Nuevo").performClick()
        compose.onNodeWithText("Nuevo atajo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monto").performTextInput("15.00")
        compose.onNodeWithText("Categoría").performClick()
        compose.onNodeWithText("Alimentación").performClick()
        compose.onNodeWithText("Nombre del atajo (opcional)").performScrollTo().performTextInput("Almuerzo")
        compose.onNodeWithText("Guardar atajo").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().favorites.any { it.title == "Almuerzo" } } }
        val afterCreate = runBlocking { repository.snapshot() }
        assertEquals(3, afterCreate.transactions.size)
        assertEquals(2L, afterCreate.favorites.single { it.title == "Almuerzo" }.accountId)
        compose.onNodeWithText("Almuerzo").assertIsDisplayed()
        compose.onNodeWithText("${Money.format(1500)} · Yape").assertIsDisplayed()
        compose.onNodeWithContentDescription("Usar atajo Almuerzo").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Monto").assertTextContains("15.00")
        compose.onNodeWithText("Desde Yape").assertIsDisplayed()
        compose.onNodeWithText("Alimentación").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nota").assertTextContains("Almuerzo")
        compose.onNodeWithText("Ya es un atajo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar registro").performClick()
        assertEquals(3, runBlocking { repository.snapshot().transactions.size })
        compose.onNodeWithContentDescription("Usar atajo Almuerzo").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithContentDescription("Monto").performTextReplacement("17.50")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Monto").assertTextContains("17.50")
        compose.onNodeWithText("Guardar gasto").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.any { it.note == "Almuerzo" } } }
        val afterSave = runBlocking { repository.snapshot() }
        val saved = afterSave.transactions.single { it.note == "Almuerzo" }
        assertEquals(1750L, saved.amountCents)
        assertEquals(2L, saved.accountId)
        assertEquals(2L, saved.categoryId)
        assertEquals(afterCreate.favorites, afterSave.favorites)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Deshacer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.size == 3 } }
    }

    @Test fun backupPickerPasswordPreviewAndRestoreComplete() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "ui-roundtrip.grafito")
        val uri = Uri.fromFile(file)
        val secret = "contraseña-ficticia-123"
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            compose.onNodeWithContentDescription("Ajustes").performClick()
            compose.onNodeWithText("Copias de seguridad").performScrollTo().performClick()
            compose.onNodeWithText("Crear copia cifrada").performScrollTo().performClick()
            compose.onNodeWithText("Contraseña de la copia").performTextInput(secret)
            compose.onNodeWithText("Repetir contraseña").performTextInput(secret)
            compose.onNodeWithText("Elegir dónde guardar").performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Copia cifrada guardada.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(file.exists())
            assertFalse(file.readText().contains("Yape"))
            runBlocking { repository.addAccount("Temporal", 123) }
            compose.onNodeWithText("Restaurar una copia").performScrollTo().performClick()
            compose.onNodeWithText("Contraseña de la copia").performTextInput(secret)
            compose.onNodeWithText("Revisar copia").performClick()
            compose.waitUntil(60_000) { compose.onAllNodesWithText("Revisar restauración").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(3, runBlocking { repository.snapshot().accounts.size })
            compose.onNodeWithText("Reemplazar y restaurar").performClick()
            compose.waitUntil(10_000) { runBlocking { repository.snapshot().accounts.size == 2 } }
            assertEquals(2L, runBlocking { repository.snapshot().defaultAccountId })
            compose.onNodeWithContentDescription("Volver a Ajustes").performClick()
            compose.onNodeWithText("Detección de pagos").performScrollTo().assertIsDisplayed()
        } finally { Intents.release(); file.delete() }
    }

    @Test fun tabsRestoreScrollAndSettingsPageAcrossNavigationAndRecreation() {
        runBlocking {
            repeat(18) { index ->
                assertTrue(repository.addFavorite("Atajo de prueba ${index + 1}", 250, 2, null))
            }
        }
        val recent = compose.onNodeWithText("Últimos movimientos")
        val homeScroll = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                hasAnyDescendant(hasText("Últimos movimientos"))
        )
        fun homeOffset(): Float =
            homeScroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

        recent.performScrollTo().assertIsDisplayed()
        val offset = homeOffset()
        assertTrue(offset > 0f)
        compose.onNodeWithContentDescription("Cuentas").performClick().assertIsSelected()
        compose.onNodeWithText("Mis cuentas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Inicio").performClick().assertIsSelected()
        recent.assertIsDisplayed()
        assertEquals(offset, homeOffset(), 1f)

        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Pagos recibidos y enviados").performClick()
        compose.onNodeWithText("Yape recibido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reportes").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Ajustes").performClick().assertIsSelected()
        compose.onNodeWithText("Yape recibido").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Yape recibido").assertIsDisplayed()
        compose.onNodeWithContentDescription("Inicio").performClick().assertIsSelected()
        recent.assertIsDisplayed()
        assertEquals(offset, homeOffset(), 1f)
    }

    @Test fun navigationAndAmountDraftSurviveRecreation() {
        for (tab in listOf("Historial", "Reportes", "Ajustes", "Inicio", "Cuentas")) {
            val destination = compose.onNodeWithContentDescription(tab)
            destination.performClick().assertIsSelected()
            compose.onNode(hasText(tab) and hasAnyAncestor(hasContentDescription(tab)), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Mis cuentas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Opciones de Efectivo").performClick()
        compose.onNodeWithText("Registrar movimiento").performClick()
        compose.onNodeWithText("Desde Efectivo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monto").performTextInput("12.50")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Registrar").assertIsDisplayed()
        compose.onNodeWithText("Desde Efectivo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monto").assertTextContains("12.50")
        assertTrue(runBlocking { repository.snapshot().transactions.isEmpty() })
    }

    @Test fun reportDeskScenePlaysAndPausesInBackground() {
        compose.onNodeWithContentDescription("Reportes").performClick()
        val bounds = assertDeskSceneAdvances()
        compose.onNodeWithText("Sin gastos en este mes").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitForIdle()
        // Wait for the display to present the last frame that was queued before the pause.
        Thread.sleep(120)
        val pausedAt = deskPixels(bounds)
        Thread.sleep(350)
        assertArrayEquals(pausedAt, deskPixels(bounds))

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { !pausedAt.contentEquals(deskPixels(bounds)) }
    }

    @Test fun reportsSelectMonthAndShowOnlyExpensesInTheSelectedCategory() {
        val zone = ZoneId.systemDefault()
        val current = YearMonth.now(zone)
        val previous = current.minusMonths(1)
        val thisMonth = current.atDay(1).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
        val lastMonth = previous.atDay(1).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            val original = repository.snapshot()
            repository.restore(original.copy(transactions = listOf(
                TransactionEntity(1, EntryType.EXPENSE, 1250, 1, categoryId = 1, note = "Pasaje en bus", occurredAt = thisMonth),
                TransactionEntity(2, EntryType.EXPENSE, 3750, 1, categoryId = 1, note = "Movilidad al trabajo", occurredAt = thisMonth),
                TransactionEntity(3, EntryType.EXPENSE, 3000, 1, note = "Panadería", occurredAt = thisMonth),
                TransactionEntity(4, EntryType.INCOME, 100000, 2, categoryId = 2, note = "Venta del mes", occurredAt = thisMonth),
                TransactionEntity(5, EntryType.TRANSFER, 50000, 2, toAccountId = 1, occurredAt = thisMonth),
                TransactionEntity(6, EntryType.EXPENSE, 9000, 1, categoryId = 1, note = "Taxi del mes pasado", occurredAt = lastMonth),
                TransactionEntity(7, EntryType.INCOME, 20000, 2, categoryId = 2, occurredAt = lastMonth)
            )))
        }
        compose.onNodeWithContentDescription("Reportes").performClick()
        val balance = compose.onNodeWithText(Money.format(92000))
        compose.waitUntil(10_000) { balance.isDisplayed() }
        compose.onNodeWithText(reportMonthLabel(current)).assertIsDisplayed()
        compose.onNodeWithText(Money.format(100000)).assertIsDisplayed()
        compose.onNodeWithText(Money.format(8000)).assertIsDisplayed()
        val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag("es-PE")).apply { maximumFractionDigits = 1 }
        compose.onNodeWithText(percent.format(0.625)).assertIsDisplayed()
        compose.onNodeWithText(percent.format(0.375)).assertIsDisplayed()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(compose.activity.getExternalFilesDir(null), "franca-reportes.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }

        compose.onNodeWithText("Transporte").performScrollTo().performClick()
        val pasaje = compose.onNodeWithText("Pasaje en bus")
        compose.waitUntil(10_000) { pasaje.isDisplayed() }
        compose.onNodeWithText("Movilidad al trabajo").assertIsDisplayed()
        compose.onNodeWithText("Panadería").assertDoesNotExist()
        compose.onNodeWithText("Taxi del mes pasado").assertDoesNotExist()
        compose.onNodeWithText("Transferencia").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Transporte · ${reportMonthLabel(current)}").assertIsDisplayed()
        compose.waitUntil(10_000) { pasaje.isDisplayed() }
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Yape").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithText("Sin resultados").isDisplayed() }
        compose.onNodeWithText("Transporte · ${reportMonthLabel(current)}").assertIsDisplayed()
        compose.onNodeWithText("Limpiar filtros").performClick()
        compose.waitUntil(10_000) { pasaje.isDisplayed() }
        compose.onNodeWithText("Taxi del mes pasado").assertDoesNotExist()
        val drillDown = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(compose.activity.getExternalFilesDir(null), "franca-reportes-movimientos.png").outputStream().use {
                drillDown.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            drillDown.recycle()
        }
        compose.onNodeWithContentDescription("Volver a Reportes").performClick()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithText(reportMonthLabel(previous)).assertIsDisplayed()
        val previousBalance = compose.onNodeWithText(Money.format(11000))
        compose.waitUntil(10_000) { previousBalance.isDisplayed() }
        compose.onNodeWithText("Transporte").performScrollTo().performClick()
        val taxi = compose.onNodeWithText("Taxi del mes pasado")
        compose.waitUntil(10_000) { taxi.isDisplayed() }
        pasaje.assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver a Reportes").performClick()

        compose.onNodeWithText(reportMonthLabel(previous)).performClick()
        compose.onNodeWithText("Elegir mes").assertIsDisplayed()
        if (previous.year != current.year) compose.onNodeWithContentDescription("Año siguiente").performClick()
        val currentName = current.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-PE"))
        compose.onNodeWithContentDescription("$currentName ${current.year}").performClick()
        compose.waitUntil(10_000) { balance.isDisplayed() }
        compose.onNodeWithText("Sin categoría").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithText("Panadería").isDisplayed() }
        pasaje.assertDoesNotExist()
    }

    @Test fun historyOpensFromHomeAndFindsMovementsByAmountAccountAndCategory() {
        compose.onNodeWithText("Ver todos").performScrollTo().performClick()
        compose.onNodeWithText("Movimientos").assertIsDisplayed()
        compose.onNodeWithText("Aún no tienes movimientos").assertIsDisplayed()
        compose.onNodeWithText("Registrar movimiento").performClick()
        compose.onNodeWithText("Registrar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar registro").performClick()

        val zone = ZoneId.systemDefault()
        val today = System.currentTimeMillis()
        val yesterday = LocalDate.now(zone).minusDays(1).atTime(16, 30).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            val original = repository.snapshot()
            repository.restore(original.copy(transactions = listOf(
                TransactionEntity(1, EntryType.EXPENSE, 1250, 1, categoryId = 1, note = "Pasaje al trabajo", occurredAt = today),
                TransactionEntity(2, EntryType.INCOME, 11250, 2, categoryId = 2, note = "Yape recibido", occurredAt = today - 60_000),
                TransactionEntity(3, EntryType.TRANSFER, 3000, 2, toAccountId = 1, note = "Para gastos", occurredAt = yesterday),
                TransactionEntity(4, EntryType.EXPENSE, 4760, 1, note = "Mercado", occurredAt = yesterday - 60_000)
            )))
        }
        val expense = compose.onNodeWithText("Pasaje al trabajo")
        val income = compose.onNodeWithText("Yape recibido")
        val transfer = compose.onNodeWithText("Transferencia")
        compose.waitUntil(10_000) { expense.isDisplayed() && transfer.isDisplayed() }
        compose.onNodeWithText("Hoy ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ayer ·", substring = true).assertIsDisplayed()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(compose.activity.getExternalFilesDir(null), "franca-movimientos.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }

        val search = compose.onNodeWithText("Buscar movimiento")
        search.performTextInput("12,50")
        search.performImeAction()
        compose.waitUntil(10_000) { expense.isDisplayed() && !income.isDisplayed() }
        expense.performClick()
        compose.onNodeWithText("Editar movimiento").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar registro").performClick()

        search.performTextReplacement("Transporte")
        search.performImeAction()
        compose.waitUntil(10_000) { expense.isDisplayed() && !transfer.isDisplayed() }
        search.performTextReplacement("Yape")
        search.performImeAction()
        compose.waitUntil(10_000) { income.isDisplayed() && transfer.isDisplayed() && !expense.isDisplayed() }
        compose.onNodeWithText("Transferencias").performClick().assertIsSelected()
        compose.waitUntil(10_000) { transfer.isDisplayed() && !income.isDisplayed() }
        compose.activityRule.scenario.recreate()
        search.assertTextContains("Yape")
        compose.onNodeWithText("Transferencias").assertIsSelected()
        compose.waitUntil(10_000) { transfer.isDisplayed() }
        transfer.performClick()
        compose.onNodeWithText("Desde").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()

        search.performTextReplacement("sin coincidencias")
        search.performImeAction()
        compose.waitUntil(10_000) { compose.onNodeWithText("Sin resultados").isDisplayed() }
        compose.onNodeWithText("Limpiar búsqueda y filtros").performClick()
        compose.onNodeWithText("Todos").assertIsSelected()
        compose.waitUntil(10_000) { expense.isDisplayed() && income.isDisplayed() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun historyCombinesAccountTypeAndPeriodAndRestoresThemAfterRecreation() {
        val zone = ZoneId.systemDefault()
        val thisMonth = YearMonth.now(zone)
        val previous = thisMonth.minusMonths(1)
        fun time(month: YearMonth, day: Int) = month.atDay(day).atTime(22, 30).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            val original = repository.snapshot()
            repository.restore(original.copy(transactions = listOf(
                TransactionEntity(1, EntryType.EXPENSE, 1250, 2, categoryId = 1, note = "Taxi con Yape", occurredAt = time(previous, 12)),
                TransactionEntity(2, EntryType.EXPENSE, 3500, 1, note = "Compra en efectivo", occurredAt = time(previous, 12)),
                TransactionEntity(3, EntryType.EXPENSE, 1850, 2, note = "Café de este mes", occurredAt = time(thisMonth, 8)),
                TransactionEntity(4, EntryType.TRANSFER, 5000, 1, toAccountId = 2, occurredAt = time(previous, 10))
            )))
        }
        compose.onNodeWithContentDescription("Historial").performClick()
        val taxi = compose.onNodeWithText("Taxi con Yape")
        val cash = compose.onNodeWithText("Compra en efectivo")
        val cafe = compose.onNodeWithText("Café de este mes")
        val transfer = compose.onNodeWithText("Transferencia")
        compose.waitUntil(10_000) { cafe.isDisplayed() }

        compose.onNodeWithText("Fecha").performClick()
        compose.onNodeWithText("Mes pasado").performClick()
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Yape").performClick()
        compose.waitUntil(10_000) { taxi.isDisplayed() && transfer.isDisplayed() }
        cash.assertDoesNotExist()
        cafe.assertDoesNotExist()
        compose.onNodeWithText("Gastos").performClick()
        compose.waitUntil(10_000) { !transfer.isDisplayed() }

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Fecha · Mes pasado").assertIsSelected()
        compose.onNodeWithText("Cuenta · Yape").assertIsSelected()
        compose.onNodeWithText("Gastos").assertIsSelected()
        compose.waitUntil(10_000) { taxi.isDisplayed() }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(compose.activity.getExternalFilesDir(null), "franca-filtros-movimientos.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }

        compose.onNodeWithText("Fecha · Mes pasado").performClick()
        compose.onNodeWithText("Rango personalizado").performScrollTo().performClick()
        compose.onNodeWithText("Aplicar fechas").assertIsNotEnabled()
        val dateFormatter = DatePickerDefaults.dateFormatter()
        for (day in listOf(2, 8)) {
            val millis = thisMonth.atDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val label = requireNotNull(dateFormatter.formatDate(millis, Locale.getDefault(), forContentDescription = true))
            compose.onNode(hasText(label, substring = true) and hasClickAction()).performClick()
        }
        compose.onNodeWithText("Aplicar fechas").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { cafe.isDisplayed() }
        taxi.assertDoesNotExist()
        compose.onNodeWithText("Limpiar filtros").performClick()
        compose.onNodeWithText("Fecha").assertIsNotSelected()
        compose.onNodeWithText("Cuenta").assertIsNotSelected()
        compose.onNodeWithText("Todos").assertIsSelected()
    }

    private fun assertDeskSceneAdvances(): Rect {
        val scene = compose.onNodeWithContentDescription("Capibara de Franca trabajando frente a su laptop")
        compose.waitUntil(10_000) { scene.isDisplayed() }
        val bounds = scene.fetchSemanticsNode().boundsInWindow
        compose.waitUntil(5_000) {
            deskPixels(bounds).any { pixel ->
                Color.red(pixel) > Color.green(pixel) + 28 && Color.green(pixel) > Color.blue(pixel) + 20
            }
        }
        val firstFrame = deskPixels(bounds)
        compose.waitUntil(5_000) { !firstFrame.contentEquals(deskPixels(bounds)) }
        return bounds
    }

    private fun deskPixels(bounds: Rect): IntArray {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            return IntArray(20 * 15) { index ->
                val x = bounds.left + bounds.width * (index % 20 + 1) / 21
                val y = bounds.top + bounds.height * (index / 20 + 1) / 16
                screenshot.getPixel(x.toInt(), y.toInt())
            }
        } finally {
            screenshot.recycle()
        }
    }

    @Test fun walletCanCreateTransferAndRemoveAccountsFromTheCardMenu() {
        compose.onNodeWithContentDescription("Cuentas").performClick()
        compose.onNodeWithText("Nueva cuenta").performClick()
        compose.onNodeWithText("Nombre · Ej.: Yape, BCP").performTextInput("BCP")
        compose.onNodeWithText("Saldo inicial (S/)").performTextInput("3200")
        compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().accounts.size == 3 } }
        val before = runBlocking { repository.snapshot() }
        val bcp = before.accounts.single { it.name == "BCP" }
        compose.onNodeWithContentDescription("Opciones de BCP").performScrollTo().performClick()
        compose.onNodeWithText("Transferir").performClick()
        compose.onAllNodes(hasText("BCP") and hasAnyAncestor(isDialog())).onFirst().assertIsSelected()
        compose.onNodeWithText("Monto S/").performScrollTo().performTextInput("12.50")
        compose.onNodeWithText("Transferir").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.size == 1 } }
        val after = runBlocking { repository.snapshot() }
        val transfer = after.transactions.single()
        assertEquals(EntryType.TRANSFER, transfer.type)
        assertEquals(bcp.id, transfer.accountId)
        assertEquals(1L, transfer.toAccountId)
        assertEquals(1250L, transfer.amountCents)
        assertEquals(before.accounts.sumOf { it.openingBalanceCents },
            FinanceMath.accountBalances(after.accounts, after.transactions).values.sum())

        compose.onNodeWithContentDescription("Inicio").performClick()
        compose.waitUntil(6_000) { !compose.onNodeWithText("Transferencia registrada").isDisplayed() }
        compose.onNodeWithText("Transferencia").performClick()
        compose.onNodeWithText("Desde").assertIsDisplayed()
        compose.onNodeWithText("Hacia").assertIsDisplayed()
        compose.onNodeWithText("BCP").assertIsDisplayed()
        compose.onNodeWithText("Efectivo").assertIsDisplayed()
        val close = compose.onNodeWithText("Cerrar")
        val closePixels = close.captureToImage().toPixelMap()
        assertEquals(HomePalette.Blue, closePixels[closePixels.width / 5, closePixels.height / 2])
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(compose.activity.getExternalFilesDir(null), "franca-detalle-transferencia.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }
        close.performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Transferencia").performClick()
        compose.onNodeWithContentDescription("Eliminar transferencia").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.isEmpty() } }
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().transactions.size == 1 } }
        compose.onNodeWithContentDescription("Cuentas").performClick()

        compose.onNodeWithContentDescription("Opciones de Yape").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar cuenta").performClick()
        compose.onNodeWithText("Eliminar Yape").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(3, runBlocking { repository.snapshot().accounts.size })
        compose.onNodeWithContentDescription("Opciones de Yape").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar cuenta").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.snapshot().accounts.none { it.name == "Yape" } } }
    }
}
