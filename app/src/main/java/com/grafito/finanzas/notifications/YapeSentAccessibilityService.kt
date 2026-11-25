package com.grafito.finanzas.notifications

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.grafito.finanzas.R
import com.grafito.finanzas.BuildConfig
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.data.YapeInbox
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.YapeSentParser
import com.grafito.finanzas.model.YapeSentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YapeSentAccessibilityService : AccessibilityService() {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var frames: Channel<Int>? = null
    private var reader: Job? = null
    private val sourcePackage by lazy { getString(R.string.yape_notification_source) }
    private val textReader by lazy { AccessibilityReceiptReader(this, sourcePackage) }

    override fun onServiceConnected() {
        stopReading()
        diagnosticsState.value = null
        if (BuildConfig.DEBUG) Log.d("FrancaYapeSent", "reader_connected")
        val pending = Channel<Int>(Channel.CONFLATED)
        frames = pending
        reader = worker.launch {
            var lastPaymentId: String? = null
            val initialWindow = try {
                withContext(Dispatchers.IO) {
                    textReader.initialWindowId()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (initialWindow != null) pending.trySend(initialWindow)
            for (windowId in pending) {
                try {
                    // Window queries can wait on another app. Keep them off Franca's UI thread.
                    val result = withContext(Dispatchers.IO) { inspectWithRetry(windowId) }
                    if (BuildConfig.DEBUG && result.status != diagnosticsState.value) {
                        Log.d("FrancaYapeSent", "window=$windowId status=${result.status}")
                    }
                    val payment = result.payment
                    if (payment == null) {
                        when (diagnosticsState.value) {
                            YapeSentStatus.DETECTED, YapeSentStatus.DUPLICATE, YapeSentStatus.INCOMPLETE ->
                                if (result.status == YapeSentStatus.INCOMPLETE) diagnosticsState.value = result.status
                            else -> diagnosticsState.value = result.status
                        }
                    } else if (payment.id != lastPaymentId) {
                        val suggestion = YapeSuggestion(
                            payment.id, payment.amountCents, payment.occurredAt ?: System.currentTimeMillis(), outgoing = true
                        )
                        val recorded = YapeInbox(FinanceDatabase.get(applicationContext)).record(suggestion)
                        if (recorded) YapeAlerts.show(applicationContext, suggestion)
                        lastPaymentId = payment.id
                        diagnosticsState.value = if (recorded) YapeSentStatus.DETECTED else YapeSentStatus.DUPLICATE
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (BuildConfig.DEBUG) Log.w("FrancaYapeSent", "read_failed type=${error.javaClass.simpleName}")
                    diagnosticsState.value = YapeSentStatus.ERROR
                }
            }
        }
        connectedState.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != sourcePackage) return
        frames?.trySend(event.windowId)
    }

    private suspend fun inspectWithRetry(windowId: Int): YapeSentParser.Result {
        var result = inspect(windowId)
        for (waitMillis in listOf(250L, 500L, 1_000L)) {
            if (result.status !in setOf(
                    YapeSentStatus.WINDOW_UNAVAILABLE, YapeSentStatus.UNAVAILABLE, YapeSentStatus.INCOMPLETE
                )) return result
            delay(waitMillis)
            result = inspect(windowId)
        }
        return result
    }

    private fun inspect(windowId: Int): YapeSentParser.Result {
        var result = YapeSentParser.Result(YapeSentStatus.WINDOW_UNAVAILABLE)
        for (parts in textReader.snapshots(windowId)) {
            val candidate = if (parts.isEmpty()) YapeSentParser.Result(YapeSentStatus.UNAVAILABLE)
                else YapeSentParser.read(parts)
            if (candidate.payment != null) return candidate
            if (result.status == YapeSentStatus.WINDOW_UNAVAILABLE || candidate.status == YapeSentStatus.INCOMPLETE) {
                result = candidate
            }
        }
        return result
    }

    override fun onInterrupt() {
        frames?.tryReceive()
    }

    private fun stopReading() {
        frames?.cancel()
        frames = null
        reader?.cancel()
        reader = null
        connectedState.value = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopReading()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopReading()
        worker.cancel()
        diagnosticsState.value = null
        super.onDestroy()
    }

    internal companion object {
        private val connectedState = MutableStateFlow(false)
        private val diagnosticsState = MutableStateFlow<YapeSentStatus?>(null)
        val connected = connectedState.asStateFlow()
        val diagnostics = diagnosticsState.asStateFlow()

        fun enabledInAndroid(context: Context): Boolean? {
            return AccessibilityReceiptReader.enabledInAndroid(context, YapeSentAccessibilityService::class.java)
        }
    }
}
