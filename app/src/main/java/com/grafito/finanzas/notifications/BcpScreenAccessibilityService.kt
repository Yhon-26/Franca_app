package com.grafito.finanzas.notifications

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.grafito.finanzas.BuildConfig
import com.grafito.finanzas.R
import com.grafito.finanzas.data.BcpMailInbox
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.receipts.BcpMailAlerts
import com.grafito.finanzas.receipts.BcpScreenParser
import com.grafito.finanzas.receipts.BcpScreenSettings
import com.grafito.finanzas.receipts.BcpScreenStatus
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

class BcpScreenAccessibilityService : AccessibilityService() {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sourcePackage by lazy { getString(R.string.bcp_screen_source) }
    private val textReader by lazy { AccessibilityReceiptReader(this, sourcePackage) }
    private val settings by lazy { BcpScreenSettings(applicationContext) }
    private data class Frame(
        val sequence: Long = 0,
        val event: AccessibilityEvent? = null,
        val systemWindowId: Int? = null,
        val windowResult: BcpScreenParser.Result? = null
    ) {
        val windowId get() = systemWindowId ?: event?.windowId
        @Suppress("DEPRECATION")
        fun release() { if (Build.VERSION.SDK_INT < 33) event?.recycle() }
    }
    private var nextSequence = 0L
    private var frames: Channel<Frame>? = null
    private var reader: Job? = null
    private var changedWindows: Channel<Frame>? = null
    private var windowReader: Job? = null

    override fun onServiceConnected() {
        stopReading()
        val pending = Channel<Frame>(Channel.CONFLATED, onUndeliveredElement = { frame ->
            trace { "frame=${frame.sequence} discarded" }
            frame.release()
        })
        frames = pending
        val windows = Channel<Frame>(Channel.CONFLATED, onUndeliveredElement = { frame ->
            trace { "frame=${frame.sequence} window_probe_discarded" }
        })
        changedWindows = windows
        windowReader = worker.launch {
            for (frame in windows) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val startedAt = SystemClock.uptimeMillis()
                        retryBcpWindow(elapsedMillis = { SystemClock.uptimeMillis() - startedAt }) { attempt ->
                            inspect(frame, attempt)
                        }
                    }
                    if (result.signals and AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE != 0) {
                        pending.trySend(frame.copy(windowResult = result))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    trace { "frame=${frame.sequence} window_probe_error" }
                }
            }
        }
        reader = worker.launch {
            var lastPaymentId: String? = null
            var lastStatus: BcpScreenStatus? = null
            pending.trySend(Frame())
            for (frame in pending) {
                try {
                    val result = withContext(Dispatchers.IO) { inspectWithRetry(frame) }
                    if (frame.systemWindowId != null &&
                        result.signals and AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE == 0) continue
                    trace { "frame=${frame.sequence} finished status=${result.status} fields=${result.fields} signals=${result.signals}" }
                    val payment = result.payment
                    if (payment != null) {
                        if (payment.id == lastPaymentId) {
                            trace { "frame=${frame.sequence} repeated_in_session" }
                            settings.report(result.copy(status = lastStatus ?: BcpScreenStatus.DETECTED))
                            continue
                        }
                        val inbox = BcpMailInbox(FinanceDatabase.get(applicationContext))
                        val recorded = inbox.record(payment)
                        trace { "frame=${frame.sequence} suggestion_created=$recorded" }
                        if (recorded) {
                            val submitted = BcpMailAlerts.show(applicationContext, payment)
                            trace { "frame=${frame.sequence} notification_submitted=$submitted" }
                            if (inbox.findPending(payment.id) == null) BcpMailAlerts.cancel(applicationContext, payment.id)
                        }
                        lastPaymentId = payment.id
                        lastStatus = if (recorded) BcpScreenStatus.DETECTED else BcpScreenStatus.DUPLICATE
                        settings.report(result.copy(status = lastStatus), validRead = true)
                    } else if (lastStatus !in setOf(BcpScreenStatus.DETECTED, BcpScreenStatus.DUPLICATE, BcpScreenStatus.INCOMPLETE) ||
                        result.status == BcpScreenStatus.INCOMPLETE) {
                        settings.report(result)
                        lastStatus = result.status
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    trace { "frame=${frame.sequence} processing_error" }
                    try {
                        settings.report(BcpScreenParser.Result(BcpScreenStatus.ERROR))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A failed diagnostic write must not stop the Android service.
                    }
                } finally {
                    frame.release()
                }
            }
        }
        connectedState.value = true
        trace { "reader_connected api=${Build.VERSION.SDK_INT}" }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (Build.VERSION.SDK_INT >= 28 && event.windowId >= 0 &&
                event.windowChanges and (AccessibilityEvent.WINDOWS_CHANGE_ADDED or
                    AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED) != 0) {
                val frame = Frame(++nextSequence, systemWindowId = event.windowId)
                trace {
                    "frame=${frame.sequence} event=TYPE_WINDOWS_CHANGED window=${frame.windowId} " +
                        "changes=${event.windowChanges} age_ms=${SystemClock.uptimeMillis() - event.eventTime}"
                }
                changedWindows?.trySend(frame)
            }
            return
        }
        if (event.packageName?.toString() == sourcePackage) {
            @Suppress("DEPRECATION")
            val copy = AccessibilityEvent.obtain(event)
            val frame = Frame(++nextSequence, copy)
            trace {
                "frame=${frame.sequence} event=${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                    "window=${event.windowId} changes=${event.contentChangeTypes} age_ms=${SystemClock.uptimeMillis() - event.eventTime}"
            }
            if (frames?.trySend(frame)?.isSuccess != true) frame.release()
        }
    }

    private suspend fun inspectWithRetry(frame: Frame): BcpScreenParser.Result {
        frame.windowResult?.let { return it }
        var result = inspect(frame, attempt = 1)
        val initialOrWindowTransition = frame.event == null ||
            frame.event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        for ((index, waitMillis) in listOf(250L, 500L, 1_000L).withIndex()) {
            val retry = when (result.status) {
                BcpScreenStatus.WINDOW_UNAVAILABLE, BcpScreenStatus.UNAVAILABLE, BcpScreenStatus.INCOMPLETE -> true
                BcpScreenStatus.VISIBLE -> index == 0 && initialOrWindowTransition &&
                    result.fields and (BcpScreenParser.HISTORY_SCREEN or BcpScreenParser.UNSUCCESSFUL_SCREEN) == 0
                else -> false
            }
            if (!retry) return result
            delay(waitMillis)
            val next = inspect(frame, attempt = index + 2)
            if (next.status == BcpScreenStatus.DETECTED ||
                result.status != BcpScreenStatus.INCOMPLETE || next.status == BcpScreenStatus.INCOMPLETE) {
                result = next
            }
        }
        return result
    }

    private fun inspect(frame: Frame, attempt: Int): BcpScreenParser.Result {
        val startedAt = SystemClock.uptimeMillis()
        var result = BcpScreenParser.Result(BcpScreenStatus.WINDOW_UNAVAILABLE)
        var signals = when {
            frame.systemWindowId != null -> AccessibilityReceiptReader.SYSTEM_WINDOW_EVENT
            frame.event != null -> AccessibilityReceiptReader.EVENT_RECEIVED
            else -> 0
        }
        fun read(parts: List<String>): BcpScreenParser.Result {
            val candidate = BcpScreenParser.read(parts)
            if (candidate.status != BcpScreenStatus.UNAVAILABLE) {
                signals = signals or AccessibilityReceiptReader.TEXT_AVAILABLE
            }
            if (candidate.status == BcpScreenStatus.INCOMPLETE || candidate.payment != null) {
                signals = signals or AccessibilityReceiptReader.CONFIRMATION_RECOGNIZED
            }
            return candidate
        }
        if (frame.event != null) {
            try {
                val source = if (Build.VERSION.SDK_INT >= 33) frame.event.getSource(0) else frame.event.source
                trace {
                    "frame=${frame.sequence} attempt=$attempt source_available=${source != null} " +
                        "package_matches=${source?.packageName?.toString() == sourcePackage} " +
                        "elapsed_ms=${SystemClock.uptimeMillis() - startedAt}"
                }
                if (source != null) {
                    try {
                        if (source.packageName?.toString() == sourcePackage) {
                            result = read(textReader.sourceSnapshot(source))
                            trace { "frame=${frame.sequence} attempt=$attempt source_status=${result.status} fields=${result.fields}" }
                            if (result.payment != null) return result.copy(
                                signals = signals or AccessibilityReceiptReader.READ_FROM_EVENT
                            )
                        }
                    } finally {
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT < 33) source.recycle()
                    }
                }
            } catch (_: SecurityException) {
                signals = signals or AccessibilityReceiptReader.QUERY_DENIED
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                signals = signals or AccessibilityReceiptReader.QUERY_FAILED
            }
        }
        val probe = textReader.probeWindows(
            frame.windowId,
            searchOtherApplicationWindows = frame.systemWindowId == null,
            onlyEventWindow = frame.systemWindowId != null
        )
        signals = signals or probe.signals
        trace {
            "frame=${frame.sequence} attempt=$attempt window_signals=${probe.signals} " +
                "bcp_roots=${probe.parts.size} root_children=${probe.rootChildren} text_nodes=${probe.parts.sumOf { it.size }} " +
                "elapsed_ms=${SystemClock.uptimeMillis() - startedAt}"
        }
        for (parts in probe.parts) {
            val candidate = read(parts)
            trace { "frame=${frame.sequence} attempt=$attempt window_status=${candidate.status} fields=${candidate.fields}" }
            if (candidate.payment != null) return candidate.copy(signals = signals)
            if (result.status == BcpScreenStatus.WINDOW_UNAVAILABLE || candidate.status == BcpScreenStatus.INCOMPLETE ||
                (result.status == BcpScreenStatus.UNAVAILABLE && candidate.status == BcpScreenStatus.VISIBLE) ||
                (result.status == BcpScreenStatus.VISIBLE && candidate.status == BcpScreenStatus.VISIBLE && result.fields == 0)) {
                result = candidate
            }
        }
        return result.copy(signals = signals)
    }

    override fun onInterrupt() {
        frames?.tryReceive()?.getOrNull()?.release()
        changedWindows?.tryReceive()
    }

    private fun stopReading() {
        changedWindows?.cancel()
        changedWindows = null
        windowReader?.cancel()
        windowReader = null
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
        super.onDestroy()
    }

    private inline fun trace(message: () -> String) {
        if (BuildConfig.DEBUG && Log.isLoggable(TRACE_TAG, Log.DEBUG)) Log.d(TRACE_TAG, message())
    }

    internal companion object {
        private const val TRACE_TAG = "FrancaBcpTrace"
        private val connectedState = MutableStateFlow(false)
        val connected = connectedState.asStateFlow()
        fun enabledInAndroid(context: Context) =
            AccessibilityReceiptReader.enabledInAndroid(context, BcpScreenAccessibilityService::class.java)
    }
}

internal suspend fun retryBcpWindow(
    elapsedMillis: () -> Long,
    wait: suspend (Long) -> Unit = { delay(it) },
    inspect: (attempt: Int) -> BcpScreenParser.Result
): BcpScreenParser.Result {
    var result = inspect(1)
    for ((index, targetMillis) in listOf(150L, 400L, 800L).withIndex()) {
        val retryableWindow = result.signals and (AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING or
            AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE) != 0
        val rejectedScreen = result.fields and (BcpScreenParser.HISTORY_SCREEN or BcpScreenParser.UNSUCCESSFUL_SCREEN) != 0
        if (!retryableWindow || rejectedScreen || result.payment != null ||
            result.signals and AccessibilityReceiptReader.QUERY_DENIED != 0) return result
        val remaining = targetMillis - elapsedMillis()
        if (remaining > 0) wait(remaining)
        result = inspect(index + 2)
    }
    return result
}
