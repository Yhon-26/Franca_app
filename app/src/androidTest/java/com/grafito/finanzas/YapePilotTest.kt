package com.grafito.finanzas

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.*
import com.grafito.finanzas.ui.theme.HomePalette
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.notifications.YapeAlerts
import com.grafito.finanzas.notifications.FrancaNotices
import com.grafito.finanzas.notifications.YapeNotificationListener
import com.grafito.finanzas.notifications.YapeSentAccessibilityService
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class YapePilotTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Application
    private lateinit var db: FinanceDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var notifications: NotificationManager
    private val listener = "com.grafito.finanzas.validation/com.grafito.finanzas.notifications.YapeNotificationListener"

    private fun assertSaveButtonUsesFrancaBlue(label: String) {
        val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
        assertEquals(HomePalette.Blue, pixels[pixels.width / 8, pixels.height / 2])
    }

    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
    }

    private val fixture = object : ExternalResource() {
        override fun before(): Unit = runBlocking {
            app = ApplicationProvider.getApplicationContext()
            check(app.packageName == "com.grafito.finanzas.validation")
            db = FinanceDatabase.get(app)
            prefs = UserPreferences(app)
            notifications = app.getSystemService(NotificationManager::class.java)
            FinanceRepository(db).restore(BackupSnapshot(
                accounts = listOf(AccountEntity(1, "Efectivo"), AccountEntity(2, "Yape")),
                categories = listOf(CategoryEntity(21, "Ventas", "", EntryType.INCOME), CategoryEntity(22, "Transporte", "", EntryType.EXPENSE)),
                transactions = emptyList(), favorites = emptyList(), defaultAccountId = 1
            ))
            prefs.setOpenQuick(false)
            prefs.setYapeCapture(false)
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
            shell("cmd notification disallow_listener $listener")
            notifications.createNotificationChannel(NotificationChannel("fixture", "Avisos de prueba", NotificationManager.IMPORTANCE_DEFAULT))
            withTimeout(15_000) { YapeNotificationListener.connected.first { !it } }
            Unit
        }

        override fun after() {
            notifications.cancelAll()
            shell("cmd notification disallow_listener $listener")
            runBlocking { prefs.setYapeCapture(false) }
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun yapeReadyOnlyGreetsOnceAfterBothReadersConnect() {
        val reader = ComponentName(app, YapeSentAccessibilityService::class.java)
        val original = shell("settings get secure enabled_accessibility_services")
        val accessibilityEnabled = shell("settings get secure accessibility_enabled")
        val originalReaders = original.split(':').filter { it.isNotBlank() && it != "null" }
        val others = originalReaders.filter { ComponentName.unflattenFromString(it) != reader }
        fun setReaders(readers: List<String>) {
            if (readers.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services ${readers.joinToString(":")}")
        }

        runBlocking { app.financePreferences.edit { it.remove(yapeReadyReactionKey) } }
        try {
            setReaders(others)
            runBlocking { withTimeout(15_000) { YapeSentAccessibilityService.connected.first { !it } } }
            runBlocking { prefs.setYapeCapture(true) }
            shell("cmd notification allow_listener $listener")
            runBlocking { withTimeout(15_000) { YapeNotificationListener.connected.first { it } } }

            compose.onNodeWithContentDescription("Ajustes").performClick()
            compose.onNodeWithText("Pagos recibidos y enviados").performClick()
            val ready = compose.onNodeWithText("Yape está listo")
            compose.onNodeWithText("Activo").assertIsDisplayed()
            compose.onNodeWithText("Activar en Accesibilidad").assertIsDisplayed()
            ready.assertDoesNotExist()

            setReaders(others + reader.flattenToString())
            shell("settings put secure accessibility_enabled 1")
            runBlocking { withTimeout(15_000) { YapeSentAccessibilityService.connected.first { it } } }
            compose.waitUntil(5_000) { ready.isDisplayed() }
            assertEquals(true, runBlocking { app.financePreferences.data.first()[yapeReadyReactionKey] })
            compose.waitUntil(6_000) { !ready.isDisplayed() }

            compose.onNodeWithContentDescription("Volver a Ajustes").performClick()
            compose.onNodeWithText("Pagos recibidos y enviados").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Activo").fetchSemanticsNodes().size == 2 }
            ready.assertDoesNotExist()
        } finally {
            setReaders(originalReaders)
            if (accessibilityEnabled in listOf("", "null")) shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $accessibilityEnabled")
            runBlocking { app.financePreferences.edit { it.remove(yapeReadyReactionKey) } }
        }
    }

    @Test fun aSinglePendingYapeOpensTheReviewDirectly() {
        val notice = sentSuggestion().copy(amountCents = 4250, outgoing = false)
        assertTrue(runBlocking { YapeInbox(db).record(notice) })
        val pending = compose.onNodeWithText("1 yapeo por revisar")
        compose.waitUntil(10_000) { pending.isDisplayed() }
        pending.performClick()
        compose.onNodeWithText("Revisar ingreso de Yape").assertIsDisplayed()
        compose.onNodeWithText("Monto en soles").performTextReplacement("36.50")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Revisar ingreso de Yape").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("36.50")).assertIsDisplayed()
        assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
        compose.onNodeWithContentDescription("Cerrar revisión").performClick()
        pending.assertIsDisplayed()
    }

    @Test fun homeLetsYouChooseYapeAndUndoRestoresTheOriginalNotice() {
        val inbox = YapeInbox(db)
        val newest = sentSuggestion()
        val older = sentSuggestion().copy(
            amountCents = 500,
            receivedAt = newest.receivedAt - 60_000,
            outgoing = false
        )
        runBlocking {
            assertTrue(inbox.record(older))
            assertTrue(inbox.record(newest))
        }
        val pending = compose.onNodeWithText("2 yapeos por revisar")
        compose.waitUntil(10_000) { pending.isDisplayed() }
        pending.performClick()

        compose.onNodeWithText("Yapeos por revisar").assertIsDisplayed()
        val received = compose.onNode(hasText("Yape recibido") and hasText(Money.format(older.amountCents)))
        val sent = compose.onNode(hasText("Yape enviado") and hasText(Money.format(newest.amountCents)))
        received.assertIsDisplayed()
        sent.assertIsDisplayed()
        assertTrue(sent.fetchSemanticsNode().boundsInWindow.top < received.fetchSemanticsNode().boundsInWindow.top)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(app.getExternalFilesDir(null), "franca-bandeja-yape.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }

        received.performClick()
        compose.onNodeWithText("Revisar ingreso de Yape").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar revisión").performClick()
        received.assertIsDisplayed()

        sent.performClick()
        compose.onNodeWithText("Revisar Yape enviado").assertIsDisplayed()
        assertSaveButtonUsesFrancaBlue("Guardar gasto")
        compose.onNodeWithText("Monto en soles").performTextReplacement("15.20")
        compose.onNodeWithText("Guardar gasto").performClick()

        val savedNotice = compose.onNodeWithText("Gasto guardado")
        compose.waitUntil(10_000) { savedNotice.isDisplayed() && received.isDisplayed() }
        sent.assertDoesNotExist()
        runBlocking {
            val saved = db.dao().allTransactions().single()
            assertEquals(newest.id, saved.receiptFingerprint)
            assertEquals(1520L, saved.amountCents)
            assertEquals(older, inbox.pending.first().single())
        }
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(10_000) { sent.isDisplayed() }
        runBlocking {
            assertTrue(db.dao().allTransactions().isEmpty())
            assertEquals(listOf(newest, older), inbox.pending.first())
            assertFalse(inbox.record(newest))
        }
        compose.onNodeWithContentDescription("Volver a Inicio").performClick()
        pending.assertIsDisplayed()
    }

    @Test fun receivesNewPaymentAfterRemovingFrancaTask() {
        runBlocking { prefs.setYapeCapture(true) }
        shell("cmd notification allow_listener $listener")
        runBlocking { withTimeout(15_000) { YapeNotificationListener.connected.first { it } } }
        val scheduler = androidx.work.WorkManager.getInstance(app)
        compose.waitUntil(10_000) {
            scheduler.getWorkInfosForUniqueWork("yape_notification_listener_recovery").get().any { !it.state.isFinished }
        }
        compose.activityRule.scenario.onActivity { it.finishAndRemoveTask() }
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (compose.activityRule.scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "La actividad de Franca sigue abierta" }
            Thread.sleep(50)
        }
        notifications.notify(734, Notification.Builder(app, "fixture")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Confirmación de pago").setContentText("Persona de prueba te envió un pago por S/12,50.").build())
        val pending = runBlocking { withTimeout(15_000) { db.yapeSuggestions().pending().first { it.isNotEmpty() } } }
        val lastRead = runBlocking { withTimeout(10_000) { prefs.yapeCapture.first { it.lastStatus == "detected" } } }
        assertEquals(pending.single().receivedAt, lastRead.lastIncomeReadAt)
        runBlocking { prefs.reportYapeStatus(lastRead.enabledSince, "unrecognized", pending.single().receivedAt + 1) }
        assertEquals(lastRead.lastIncomeReadAt, runBlocking { prefs.yapeCapture.first().lastIncomeReadAt })
        assertEquals(1250L, pending.single().amountCents)
        assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
    }

    @Test fun activeNotificationIsRecoveredOnConnectionAndSavedOnlyOnce() {
        val channel = FrancaNotices.yapeIncome.ensure(app)
        val originalImportance = notifications.getNotificationChannel(channel)?.importance
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Yape").performClick()
        val help = compose.onNodeWithText("Ayuda y diagnóstico")
        help.assertIsDisplayed()
        compose.onNodeWithText("Ajustes de batería").assertDoesNotExist()
        help.performClick()
        compose.onNodeWithText("Ajustes de batería").performScrollTo().assertIsDisplayed()
        help.performScrollTo().performClick()
        compose.onNodeWithText("Ajustes de batería").assertDoesNotExist()
        compose.onNodeWithContentDescription("Detectar ingresos de Yape").performClick()
        runBlocking { withTimeout(10_000) { prefs.yapeCapture.first { it.enabledSince > 0 } } }
        val time = System.currentTimeMillis()
        fun publish(message: String) {
            notifications.notify(731, Notification.Builder(app, "fixture")
                .setSmallIcon(android.R.drawable.stat_notify_more).setWhen(time)
                .setContentTitle("Confirmación de pago").setContentText(message).build())
        }
        publish("Persona de prueba te envió un pago por S/12,50. El cód. de seguridad es:000.")
        compose.waitUntil(10_000) { notifications.activeNotifications.any { it.id == 731 } }
        assertTrue(runBlocking { db.yapeSuggestions().pending().first().isEmpty() })
        shell("cmd notification allow_listener $listener")
        runBlocking { withTimeout(15_000) { YapeNotificationListener.connected.first { it } } }
        val pending = runBlocking { withTimeout(10_000) { db.yapeSuggestions().pending().first { it.isNotEmpty() } } }
        val suggestion = pending.single()
        assertEquals(1250L, suggestion.amountCents)
        compose.onNodeWithText("Yapeos por revisar").assertIsDisplayed()
        compose.onNodeWithText("Revisar").performClick()
        compose.onNodeWithText("Revisar ingreso de Yape").assertIsDisplayed()
        assertSaveButtonUsesFrancaBlue("Guardar ingreso")
        compose.onNodeWithContentDescription("Cerrar revisión").performClick()
        assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
        compose.waitUntil(10_000) { incomeAlerts().isNotEmpty() }
        val alert = incomeAlerts().single()
        assertEquals(originalImportance ?: NotificationManager.IMPORTANCE_HIGH,
            notifications.getNotificationChannel(channel).importance)
        assertEquals(suggestion.id, alert.tag)
        assertEquals(Notification.VISIBILITY_PRIVATE, alert.notification.visibility)
        assertEquals("Yape recibido · S/ 12.50", alert.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(app.getColor(R.color.franca_mint), alert.notification.color)
        assertTrue(alert.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val review = alert.notification.actions.single()
        assertEquals("Registrar", review.title.toString())
        assertFalse(runBlocking { YapeInbox(db).confirm(suggestion.id, 2, 1250, 22) })
        alert.notification.contentIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Revisar ingreso de Yape").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cerrar revisión").performClick()
        assertEquals(suggestion.id, runBlocking { db.yapeSuggestions().pending().first() }.single().id)

        review.actionIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Revisar ingreso de Yape").fetchSemanticsNodes().isNotEmpty() }
        assertSaveButtonUsesFrancaBlue("Guardar ingreso")
        compose.onNode(hasText("Cuenta de destino") and hasText("Yape")).assertIsDisplayed()
        compose.onNodeWithText("Categoría").performScrollTo().performClick()
        compose.onNodeWithText("Transporte").assertDoesNotExist()
        compose.onNodeWithContentDescription("Buscar categoría").performTextInput("venta")
        compose.onNode(hasText("Ventas") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performScrollTo().performClick()
        compose.onNodeWithText("Guardar ingreso").performClick()
        val saved = runBlocking { withTimeout(10_000) { db.dao().transactions().first { it.isNotEmpty() } } }.single()
        assertEquals(1250L, saved.amountCents)
        assertEquals(2L, saved.accountId)
        assertEquals(EntryType.INCOME, saved.type)
        assertEquals(21L, saved.categoryId)
        compose.waitUntil(10_000) { incomeAlerts().isEmpty() }
        assertFalse(runBlocking { YapeInbox(db).confirm(pending.single().id, 2, 1250) })
        publish("Persona de prueba te envió un pago por S/12,50. Aviso actualizado.")
        runBlocking { withTimeout(10_000) { prefs.yapeCapture.first { it.lastStatus == "duplicate" } } }
        publish("Enviaste un yapeo por S/12,50.")
        runBlocking { withTimeout(10_000) { prefs.yapeCapture.first { it.lastStatus == "unrecognized" } } }
        assertTrue(runBlocking { db.yapeSuggestions().pending().first().isEmpty() })
        assertEquals(1, runBlocking { db.dao().allTransactions().size })
        assertTrue(incomeAlerts().isEmpty())
    }

    @Test fun tappingAlertCanSaveAnInternalTransferWithoutChangingTotalBalance() {
        val suggestion = YapeSuggestion("transfer-test-" + System.nanoTime(), 1250, System.currentTimeMillis())
        val inbox = YapeInbox(db)
        runBlocking {
            assertTrue(inbox.record(suggestion))
            assertFalse(inbox.confirm(suggestion.id, 2, 1250, otherAccountId = 2))
        }
        YapeAlerts.show(app, suggestion)
        compose.waitUntil(10_000) { incomeAlerts().any { it.tag == suggestion.id } }
        incomeAlerts().single { it.tag == suggestion.id }.notification.contentIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Revisar ingreso de Yape").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Entre mis cuentas").performScrollTo().performClick()
        compose.onNodeWithText("Guardar transferencia").assertIsNotEnabled()
        compose.onNodeWithText("Elige la otra cuenta").assertIsDisplayed()
        compose.onNodeWithText("Sin categoría").assertDoesNotExist()
        compose.onNodeWithText("Cuenta de origen").performScrollTo().performClick()
        compose.onNodeWithText("Elegir cuenta de origen").assertIsDisplayed()
        compose.onNodeWithText("Efectivo").performClick()
        compose.onNodeWithText("Guardar transferencia").performClick()
        val saved = runBlocking { withTimeout(10_000) { db.dao().transactions().first { it.isNotEmpty() } } }.single()
        assertEquals(EntryType.TRANSFER, saved.type)
        assertEquals(1L, saved.accountId)
        assertEquals(2L, saved.toAccountId)
        assertEquals(1250L, saved.amountCents)
        assertNull(saved.categoryId)
        assertEquals(0L, FinanceMath.accountBalances(runBlocking { db.dao().allAccounts() }, listOf(saved)).values.sum())
        assertFalse(runBlocking { inbox.confirm(suggestion.id, 2, 1250, otherAccountId = 1) })
    }

    @Test fun sentAlertOpensExpenseReviewAndSavesOnce() {
        val channel = FrancaNotices.yapeSent.ensure(app)
        val originalImportance = notifications.getNotificationChannel(channel)?.importance
        val suggestion = sentSuggestion()
        val inbox = YapeInbox(db)
        runBlocking {
            assertTrue(inbox.record(suggestion))
            assertFalse(inbox.record(suggestion))
            assertFalse(inbox.confirm(suggestion.id, 2, 1250, categoryId = 21))
        }
        YapeAlerts.show(app, suggestion)
        compose.waitUntil(10_000) { notifications.activeNotifications.any { it.tag == suggestion.id } }
        val alert = notifications.activeNotifications.single { it.tag == suggestion.id }
        assertEquals(channel, alert.notification.channelId)
        assertEquals(originalImportance ?: NotificationManager.IMPORTANCE_HIGH,
            notifications.getNotificationChannel(channel).importance)
        assertEquals("Yape enviado · S/ 12.50", alert.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Registrar", alert.notification.actions.single().title.toString())
        assertEquals(app.getColor(R.color.franca_mint), alert.notification.color)
        assertTrue(runBlocking { db.dao().allTransactions().isEmpty() })
        alert.notification.contentIntent.send()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Revisar Yape enviado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Cuenta de origen") and hasText("Yape")).assertIsDisplayed()
        compose.onNodeWithText("Guardar gasto").performClick()
        val saved = runBlocking { withTimeout(10_000) { db.dao().transactions().first { it.isNotEmpty() } } }.single()
        assertEquals(EntryType.EXPENSE, saved.type)
        assertEquals(2L, saved.accountId)
        assertEquals(1250L, saved.amountCents)
        assertEquals(suggestion.id, saved.receiptFingerprint)
        assertFalse(runBlocking { inbox.confirm(suggestion.id, 2, 1250) })
    }

    @Test fun sentSuggestionMovesMoneyFromYapeToMyOtherAccount() = runBlocking {
        val suggestion = sentSuggestion()
        val inbox = YapeInbox(db)
        assertTrue(inbox.record(suggestion))
        assertTrue(inbox.confirm(suggestion.id, 2, 1250, otherAccountId = 1))
        val saved = db.dao().allTransactions().single()
        assertEquals(EntryType.TRANSFER, saved.type)
        assertEquals(2L, saved.accountId)
        assertEquals(1L, saved.toAccountId)
        assertEquals(0L, FinanceMath.accountBalances(db.dao().allAccounts(), listOf(saved)).values.sum())
    }

    private fun sentSuggestion() =
        YapeSuggestion(java.util.UUID.randomUUID().toString().replace("-", "").repeat(2), 1250, System.currentTimeMillis(), outgoing = true)

    private fun incomeAlerts() = notifications.activeNotifications.filter {
        FrancaNotices.yapeIncome.matches(it.notification.channelId) &&
            it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }
}
