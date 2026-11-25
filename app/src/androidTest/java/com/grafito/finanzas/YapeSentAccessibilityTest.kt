package com.grafito.finanzas

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Dialog
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.model.YapeSentParser
import com.grafito.finanzas.model.YapeSentStatus
import com.grafito.finanzas.notifications.YapeAlerts
import com.grafito.finanzas.notifications.FrancaNotices
import com.grafito.finanzas.notifications.YapeSentAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class YapeSentAccessibilityTest {
    @Test fun readsOnlyTheConfirmationAndKeepsItPendingOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.grafito.finanzas.validation")
        val db = FinanceDatabase.get(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val component = ComponentName(context, YapeSentAccessibilityService::class.java)
        val existing = shell("settings get secure enabled_accessibility_services")
        val accessibilityEnabled = shell("settings get secure accessibility_enabled")
        val otherServices = existing.takeUnless { it.isBlank() || it == "null" }?.split(':').orEmpty()
            .filterNot { ComponentName.unflattenFromString(it) == component }
        fun setReaderEnabled(enabled: Boolean) {
            val services = if (enabled) otherServices + component.flattenToString() else otherServices
            if (services.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else {
                val setting = services.joinToString(":")
                shell("settings put secure enabled_accessibility_services $setting")
            }
        }
        val operation = System.nanoTime().toString().takeLast(12)
        val receiptDate = LocalDate.now(ZoneId.of("America/Lima")).format(
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.forLanguageTag("es-PE"))
        )
        val confirmation = listOf(
            "Cerrar", "¡Yapeaste!", "12.50", "12.50 soles", receiptDate, "08:40 a. m.",
            "Nro. de operación", operation
        )
        val payment = requireNotNull(YapeSentParser.read(confirmation).payment)
        suspend fun awaitStatus(status: YapeSentStatus) {
            val diagnostics = YapeSentAccessibilityService.diagnostics
            val observed = withTimeoutOrNull(10_000) { diagnostics.first { it == status } }
            assertEquals("Último estado del lector: ${diagnostics.value}", status, observed)
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            setReaderEnabled(true)
            shell("settings put secure accessibility_enabled 1")
            assertEquals(true, YapeSentAccessibilityService.enabledInAndroid(context))
            withTimeout(15_000) { YapeSentAccessibilityService.connected.first { it } }
            ActivityScenario.launch(MainActivity::class.java).use { franca ->
                franca.onActivity { it.finishAndRemoveTask() }
                withTimeout(10_000) {
                    while (franca.state != androidx.lifecycle.Lifecycle.State.DESTROYED) delay(50)
                }
            }
            // In the validation build this separate fixture activity supplies the simulated Yape window.
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                fun display(lines: List<String>) = scenario.onActivity { activity ->
                    val column = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        if (Build.VERSION.SDK_INT >= 34) {
                            setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
                        }
                        val inset = (96 * resources.displayMetrics.density).toInt()
                        setPadding(inset / 3, inset, inset / 3, 0)
                    }
                    lines.forEach { line ->
                        column.addView(TextView(activity).apply {
                            text = line
                            textSize = 22f
                            importantForAccessibility = TextView.IMPORTANT_FOR_ACCESSIBILITY_YES
                        })
                    }
                    activity.setContentView(column)
                }
                display(emptyList())
                awaitStatus(YapeSentStatus.UNAVAILABLE)
                display(listOf("Yapear", "S/ 12.50", "Confirmar"))
                awaitStatus(YapeSentStatus.YAPE_VISIBLE)
                assertNull(db.yapeSuggestions().findPending(payment.id))
                val ledgerBefore = db.dao().allTransactions()
                val paymentWindow = requireNotNull(automation.rootInActiveWindow).windowId
                lateinit var overlay: Dialog
                scenario.onActivity { activity ->
                    overlay = Dialog(activity).apply {
                        setContentView(TextView(activity).apply { text = "Ventana secundaria" })
                        window?.apply {
                            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                            setGravity(Gravity.BOTTOM)
                        }
                        show()
                        window?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
                    }
                }
                try {
                    automation.waitForIdle(250, 5_000)
                    val activeWindow = requireNotNull(automation.rootInActiveWindow).windowId
                    assertNotEquals(paymentWindow, activeWindow)
                    val windows = automation.windows.map { it.id }
                    assertTrue("Ventana del pago $paymentWindow en ventanas interactivas $windows", paymentWindow in windows)

                    val event = automation.executeAndWaitForEvent({ display(confirmation) }, {
                        it.windowId == paymentWindow && it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    }, 5_000)
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < 33) event.recycle()
                    automation.waitForIdle(200, 5_000)
                    val visibleHeading = automation.windows.first { it.id == paymentWindow }.root
                        ?.findAccessibilityNodeInfosByText(confirmation.first()).orEmpty().any { it.isVisibleToUser }
                    assertTrue("El encabezado de prueba sigue visible en la ventana del pago", visibleHeading)
                    awaitStatus(YapeSentStatus.DETECTED)
                    val candidate = withTimeout(15_000) {
                        db.yapeSuggestions().pending().first { pending -> pending.any { it.id == payment.id } }
                            .single { it.id == payment.id }
                    }
                    assertEquals(activeWindow, automation.rootInActiveWindow?.windowId)
                    assertTrue(candidate.outgoing)
                    assertEquals(1250L, candidate.amountCents)
                    assertEquals(payment.occurredAt, candidate.receivedAt)
                    withTimeout(5_000) {
                        while (notifications.activeNotifications.none {
                            it.tag == payment.id && FrancaNotices.yapeSent.matches(it.notification.channelId) &&
                                it.notification.contentIntent != null
                        }) delay(50)
                    }
                } finally {
                    scenario.onActivity { overlay.dismiss() }
                }
                display(confirmation + "Compartir")
                delay(500)
                assertEquals(1, db.yapeSuggestions().pending().first().count { it.id == payment.id })
                assertEquals(ledgerBefore, db.dao().allTransactions())

                setReaderEnabled(false)
                assertEquals(false, YapeSentAccessibilityService.enabledInAndroid(context))
                withTimeout(15_000) { YapeSentAccessibilityService.connected.first { !it } }
                display(listOf("Yapear"))
                setReaderEnabled(true)
                withTimeout(15_000) { YapeSentAccessibilityService.connected.first { it } }
                display(confirmation)
                awaitStatus(YapeSentStatus.DUPLICATE)
                assertEquals(1, db.yapeSuggestions().pending().first().count { it.id == payment.id })
            }
        } finally {
            YapeAlerts.cancel(context, payment.id)
            db.yapeSuggestions().dismiss(payment.id)
            if (existing.isBlank() || existing == "null") shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $existing")
            if (accessibilityEnabled.isBlank() || accessibilityEnabled == "null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $accessibilityEnabled")
        }
        Unit
    }
}
