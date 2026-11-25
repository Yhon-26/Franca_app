package com.grafito.finanzas.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RawRes
import com.grafito.finanzas.R

internal class FrancaNoticeChannel(
    val legacyId: String,
    val title: String,
    val tonePath: String,
    @RawRes val tone: Int
) {
    private val soundChannelId = "${legacyId}_franca"

    fun soundUri(context: Context): Uri = Uri.Builder().scheme("content")
        .authority(context.packageName + ".tones").appendPath(tonePath).build()

    fun matches(id: String?): Boolean = id == legacyId || id == soundChannelId

    fun enabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(soundChannelId) ?: manager.getNotificationChannel(legacyId)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @Synchronized
    fun ensure(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(soundChannelId) != null) return soundChannelId

        val legacy = manager.getNotificationChannel(legacyId)
        if (legacy != null && !legacyUsesDefaults(legacy)) return legacyId

        val channel = NotificationChannel(soundChannelId, title, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(soundUri(context), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
        manager.createNotificationChannel(channel)
        if (legacy != null) {
            legacy.name = "$title (anterior)"
            manager.createNotificationChannel(legacy)
        }
        return soundChannelId
    }

    private fun legacyUsesDefaults(channel: NotificationChannel): Boolean {
        if (Build.VERSION.SDK_INT < 30 || channel.hasUserSetSound() || channel.hasUserSetImportance()) return false
        val defaults = NotificationChannel(legacyId, title, NotificationManager.IMPORTANCE_HIGH)
        return channel.importance == defaults.importance &&
            channel.sound == Settings.System.DEFAULT_NOTIFICATION_URI &&
            channel.shouldVibrate() == defaults.shouldVibrate() &&
            channel.shouldShowLights() == defaults.shouldShowLights() &&
            channel.canShowBadge() == defaults.canShowBadge() &&
            channel.lockscreenVisibility == defaults.lockscreenVisibility &&
            channel.group == null && !channel.canBypassDnd()
    }
}

internal object FrancaNotices {
    val yapeIncome = FrancaNoticeChannel("yape_income", "Ingresos de Yape", "yape-recibido", R.raw.franca_yape_received)
    val yapeSent = FrancaNoticeChannel("yape_sent", "Yapeos enviados", "yape-enviado", R.raw.franca_yape_sent)
    val bcpExpense = FrancaNoticeChannel("bcp_mail_expenses", "Gastos BCP", "gasto-bcp", R.raw.franca_bcp_expense)
    val channels = listOf(yapeIncome, yapeSent, bcpExpense)
}
