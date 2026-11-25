package com.grafito.finanzas.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.grafito.finanzas.R
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.data.UserPreferences
import com.grafito.finanzas.data.YapeInbox
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.YapeReceiptParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

class YapeNotificationListener : NotificationListenerService() {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { UserPreferences(applicationContext) }

    override fun onListenerConnected() {
        connection.value = true
        val visible = try {
            activeNotifications.orEmpty()
        } catch (_: SecurityException) {
            // Notification access may have been revoked while Android was binding the service.
            return
        }
        visible.forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        connection.value = false
        requestConnection(this)
    }

    override fun onDestroy() {
        connection.value = false
        worker.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(event: StatusBarNotification?) {
        if (event == null || event.packageName != getString(R.string.yape_notification_source)) return
        val notification = event.notification
        if (event.packageName == packageName &&
            (FrancaNotices.yapeIncome.matches(notification.channelId) ||
                FrancaNotices.yapeSent.matches(notification.channelId))) return
        if (notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return

        worker.launch {
            val enabledSince = try {
                preferences.yapeCaptureSince()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (enabledSince <= 0 || event.postTime < enabledSince) return@launch

            val outcome = try {
                val extras = notification.extras
                val parts = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
                    .mapNotNull { extras.getCharSequence(it)?.take(600)?.toString() }.distinct()
                val cents = YapeReceiptParser.amountCents(parts)
                if (cents == null) {
                    "unrecognized"
                } else {
                    // Android's identifier plus its event timestamp. The text and security code are discarded.
                    val eventTime = notification.`when`.takeIf { it in enabledSince..event.postTime } ?: event.postTime
                    val id = MessageDigest.getInstance("SHA-256")
                        .digest("${event.key}:$eventTime".toByteArray())
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (preferences.yapeCaptureSince() != enabledSince) return@launch
                    val inbox = YapeInbox(FinanceDatabase.get(applicationContext))
                    val suggestion = YapeSuggestion(id, cents, event.postTime)
                    if (inbox.record(suggestion)) {
                        YapeAlerts.show(applicationContext, suggestion)
                        "detected"
                    } else "duplicate"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                "error"
            }
            try {
                preferences.reportYapeStatus(enabledSince, outcome, event.postTime)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A diagnostic write must not crash the notification listener.
            }
        }
    }

    companion object {
        private val connection = MutableStateFlow(false)
        internal val connected = connection.asStateFlow()

        internal fun requestConnection(context: Context) {
            if (connection.value) return
            val component = ComponentName(context, YapeNotificationListener::class.java)
            try {
                val granted = if (android.os.Build.VERSION.SDK_INT >= 27) {
                    context.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(component)
                } else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
                if (granted) requestRebind(component)
            } catch (_: SecurityException) {
                // Access may change in Android settings between the check and the request.
            }
        }
    }
}
