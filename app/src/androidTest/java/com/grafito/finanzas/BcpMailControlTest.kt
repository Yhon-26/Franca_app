package com.grafito.finanzas

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.notifications.BcpScreenAccessibilityService
import com.grafito.finanzas.receipts.BcpGmailSettings
import com.grafito.finanzas.receipts.BcpGmailSync
import com.grafito.finanzas.receipts.BcpGmailWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class BcpMailControlTest {
    @Test fun disablingMailCancelsChecksAndKeepsTheDirectReaderEnabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.grafito.finanzas.validation")
        val settings = BcpGmailSettings(context)
        check(settings.connection.first().email == null)
        val manager = WorkManager.getInstance(context)
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val original = shell("settings get secure enabled_accessibility_services")
        val originallyEnabled = shell("settings get secure accessibility_enabled")
        val services = original.takeUnless { it.isBlank() || it == "null" }?.split(':').orEmpty()
        val component = ComponentName(context, BcpScreenAccessibilityService::class.java)
        val ledger = FinanceDatabase.get(context).dao().allTransactions()
        try {
            BcpGmailSync.stop(context)
            shell("settings put secure enabled_accessibility_services ${(services + component.flattenToString()).distinct().joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
            withTimeout(15_000) { BcpScreenAccessibilityService.connected.first { it } }
            settings.connect("validation@example.invalid")
            val manual = OneTimeWorkRequestBuilder<BcpGmailWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
            val periodic = PeriodicWorkRequestBuilder<BcpGmailWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS).build()
            manager.enqueueUniqueWork(BcpGmailSync.MANUAL, ExistingWorkPolicy.REPLACE, manual)
            manager.enqueueUniquePeriodicWork(BcpGmailSync.PERIODIC, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, periodic)
            withTimeout(15_000) {
                while (manager.getWorkInfoById(manual.id).get(10, TimeUnit.SECONDS)?.state != WorkInfo.State.ENQUEUED ||
                    manager.getWorkInfoById(periodic.id).get(10, TimeUnit.SECONDS)?.state != WorkInfo.State.ENQUEUED) delay(50)
            }
            BcpGmailSync.disconnect(context)
            withTimeout(15_000) {
                while (manager.getWorkInfoById(manual.id).get(10, TimeUnit.SECONDS)?.state != WorkInfo.State.CANCELLED ||
                    manager.getWorkInfoById(periodic.id).get(10, TimeUnit.SECONDS)?.state != WorkInfo.State.CANCELLED) delay(50)
            }
            assertNull(settings.connection.first().email)
            assertTrue(BcpScreenAccessibilityService.connected.value)
            assertEquals(true, BcpScreenAccessibilityService.enabledInAndroid(context))
            assertEquals(ledger, FinanceDatabase.get(context).dao().allTransactions())
        } finally {
            withContext(NonCancellable) {
                BcpGmailSync.disconnect(context)
                if (original.isBlank() || original == "null") shell("settings delete secure enabled_accessibility_services")
                else shell("settings put secure enabled_accessibility_services $original")
                if (originallyEnabled.isBlank() || originallyEnabled == "null") shell("settings delete secure accessibility_enabled")
                else shell("settings put secure accessibility_enabled $originallyEnabled")
            }
        }
        Unit
    }
}
