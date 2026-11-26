package com.grafito.finanzas

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.app.Dialog
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.grafito.finanzas.data.BcpMailInbox
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.notifications.BcpScreenAccessibilityService
import com.grafito.finanzas.notifications.AccessibilityReceiptReader
import com.grafito.finanzas.receipts.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BcpScreenAccessibilityTest {
    @Test fun respectsProtectedContentAndQueuesAnEligibleReceiptOnlyOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.grafito.finanzas.validation")
        val db = FinanceDatabase.get(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = BcpScreenSettings(context)
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val component = ComponentName(context, BcpScreenAccessibilityService::class.java)
        val existing = shell("settings get secure enabled_accessibility_services")
        val enabledBefore = shell("settings get secure accessibility_enabled")
        val others = existing.takeUnless { it.isBlank() || it == "null" }?.split(':').orEmpty()
            .filterNot { ComponentName.unflattenFromString(it) == component }
        fun setReaderEnabled(enabled: Boolean) {
            val services = if (enabled) others + component.flattenToString() else others
            if (services.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services ${services.joinToString(":")}")
        }
        val operation = System.nanoTime().toString().takeLast(12)
        val receipt = listOf(
            "¡Operación", "exitosa!", "S/ 12.50", "Martes 17 Noviembre 2026 - 11:06 am.",
            "Enviado a", "Persona de prueba", "Desde", "Cuenta de prueba",
            "Número de operación", operation,
            "Asegúrala desde S/0.20 al día"
        )
        val payment = requireNotNull(BcpScreenParser.read(receipt).payment)
        val ledgerBefore = db.dao().allTransactions()
        try {
            if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            setReaderEnabled(false)
            settings.report(BcpScreenParser.Result(BcpScreenStatus.ERROR))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var receiptView: View
                fun display(parts: List<String>, protected: Boolean = false, paneTitle: String? = null) = scenario.onActivity { activity ->
                    val column = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        val inset = (48 * resources.displayMetrics.density).toInt()
                        setPadding(inset, inset, inset, 0)
                        if (Build.VERSION.SDK_INT >= 28) accessibilityPaneTitle = paneTitle
                        if (Build.VERSION.SDK_INT >= 34) setAccessibilityDataSensitive(
                            if (protected) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_NO
                        )
                    }
                    parts.forEach { part ->
                        column.addView(TextView(activity).apply {
                            text = part
                            textSize = 15f
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        })
                    }
                    column.addView(EditText(activity).apply {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        setText("Número de operación 00998877")
                    })
                    receiptView = column
                    activity.setContentView(column)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    display(receipt, protected = true)
                    setReaderEnabled(true)
                    shell("settings put secure accessibility_enabled 1")
                    withTimeout(15_000) { BcpScreenAccessibilityService.connected.first { it } }
                    withTimeout(15_000) {
                        settings.report.first {
                            it.status in setOf(BcpScreenStatus.VISIBLE, BcpScreenStatus.UNAVAILABLE, BcpScreenStatus.WINDOW_UNAVAILABLE)
                        }
                    }
                    assertNull(db.bcpMailSuggestions().find(payment.id))
                    setReaderEnabled(false)
                    withTimeout(15_000) { BcpScreenAccessibilityService.connected.first { !it } }
                }
                display(emptyList())
                setReaderEnabled(true)
                shell("settings put secure accessibility_enabled 1")
                withTimeout(15_000) { BcpScreenAccessibilityService.connected.first { it } }
                val empty = withTimeout(15_000) { settings.report.first { it.status == BcpScreenStatus.UNAVAILABLE } }
                assertTrue(empty.signals and AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE != 0)
                assertEquals(0, empty.signals and AccessibilityReceiptReader.TEXT_AVAILABLE)
                display(listOf("Detalle de Movimiento", "Monto enviado", "S/ 12.50", "17 Noviembre 2026",
                    "Enviado a", "Persona de prueba", "Desde", "Cuenta de prueba", "Número de operación", operation))
                val history = withTimeout(15_000) {
                    settings.report.first { it.status == BcpScreenStatus.VISIBLE &&
                        it.signals and AccessibilityReceiptReader.EVENT_RECEIVED != 0 }
                }
                assertTrue(history.signals and AccessibilityReceiptReader.TEXT_AVAILABLE != 0)
                assertEquals(0, history.signals and AccessibilityReceiptReader.CONFIRMATION_RECOGNIZED)
                assertEquals(BcpScreenParser.HISTORY_SCREEN, history.fields)
                assertNull(db.bcpMailSuggestions().find(payment.id))
                assertTrue(manager.activeNotifications.none { it.tag == payment.id })
                display(receipt - operation)
                withTimeout(15_000) { settings.report.first { it.status == BcpScreenStatus.INCOMPLETE } }
                display(listOf("Inicio BCP"))
                delay(700)
                assertEquals(BcpScreenStatus.INCOMPLETE, settings.report.first().status)
                assertNull(db.bcpMailSuggestions().find(payment.id))
                if (Build.VERSION.SDK_INT >= 28) display(receipt.drop(2), paneTitle = "¡Operación exitosa!")
                else display(receipt)
                withTimeout(15_000) { settings.report.first { it.status == BcpScreenStatus.DETECTED } }
                val lastReadAt = requireNotNull(settings.report.first().lastReadAt)
                val candidate = requireNotNull(db.bcpMailSuggestions().find(payment.id))
                assertEquals(1250L, candidate.amountCents)
                assertEquals(payment.occurredAt, candidate.occurredAt)
                scenario.onActivity { receiptView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
                val eventRead = withTimeout(15_000) {
                    settings.report.first { it.signals and AccessibilityReceiptReader.READ_FROM_EVENT != 0 }
                }
                assertEquals(lastReadAt, eventRead.lastReadAt)
                assertEquals(0, eventRead.signals and AccessibilityReceiptReader.WINDOW_QUERY)
                val eventSignals = AccessibilityReceiptReader.EVENT_RECEIVED or AccessibilityReceiptReader.TEXT_AVAILABLE or
                    AccessibilityReceiptReader.CONFIRMATION_RECOGNIZED
                assertEquals(eventSignals, eventRead.signals and eventSignals)
                assertEquals(15 or BcpScreenParser.SUCCESS_TITLE, eventRead.fields)
                withTimeout(10_000) {
                    while (manager.activeNotifications.none { it.tag == payment.id }) delay(50)
                }
                assertFalse(BcpMailInbox(db).record(payment))
                display(listOf("¡Operación exitosa! Monto: S/ 12.50") + receipt.drop(3))
                delay(500)
                assertEquals(1, db.bcpMailSuggestions().pending().first().count { it.id == payment.id })
                assertEquals(ledgerBefore, db.dao().allTransactions())
                setReaderEnabled(false)
                withTimeout(15_000) { BcpScreenAccessibilityService.connected.first { !it } }
                val receiptWindow = requireNotNull(automation.rootInActiveWindow).windowId
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
                    assertNotEquals(receiptWindow, activeWindow)
                    assertTrue(automation.windows.any { it.id == receiptWindow })
                    settings.report(BcpScreenParser.Result(BcpScreenStatus.ERROR))
                    setReaderEnabled(true)
                    withTimeout(15_000) { settings.report.first { it.status == BcpScreenStatus.DUPLICATE } }
                    assertEquals(activeWindow, automation.rootInActiveWindow?.windowId)
                    assertEquals(1, db.bcpMailSuggestions().pending().first().count { it.id == payment.id })
                    assertEquals(ledgerBefore, db.dao().allTransactions())
                } finally {
                    scenario.onActivity { overlay.dismiss() }
                }
            }
        } finally {
            BcpMailAlerts.cancel(context, payment.id)
            db.bcpMailSuggestions().dismiss(payment.id)
            if (existing.isBlank() || existing == "null") shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $existing")
            if (enabledBefore.isBlank() || enabledBefore == "null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $enabledBefore")
        }
        Unit
    }
}
