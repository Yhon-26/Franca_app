package com.grafito.finanzas

import android.app.Activity
import android.app.Application
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.edit
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
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

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
        compose.onNodeWithContentDescription("Volver a Registrar").performClick()
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

    @Test fun sameDeskScenePlaysInMovementsAndReportsAndPausesInBackground() {
        compose.onNodeWithContentDescription("Historial").performClick()
        compose.onNodeWithText("Movimientos").assertIsDisplayed()
        assertDeskSceneAdvances()

        compose.onNodeWithContentDescription("Reportes").performClick()
        val bounds = assertDeskSceneAdvances()
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

    private fun assertDeskSceneAdvances(): Rect {
        val scene = compose.onNodeWithContentDescription("Capibara de Franca trabajando frente a su laptop")
        scene.assertIsDisplayed()
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
