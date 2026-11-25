package com.grafito.finanzas.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grafito.finanzas.R
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.Money

internal object YapeAlerts {
    private const val NOTIFICATION_ID = 1

    fun show(context: Context, suggestion: YapeSuggestion) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return

        val channel = (if (suggestion.outgoing) FrancaNotices.yapeSent else FrancaNotices.yapeIncome).ensure(context)
        val review = PendingIntent.getActivity(context, 0, Intent(context, YapeReviewActivity::class.java)
            .setData(Uri.fromParts("franca-yape", suggestion.id, null)), PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_franca_monochrome)
            .setContentTitle("Tienes un movimiento por revisar")
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_franca)
            .setColor(ContextCompat.getColor(context, R.color.franca_mint))
            .setContentTitle("${if (suggestion.outgoing) "Yape enviado" else "Yape recibido"} · ${Money.format(suggestion.amountCents)}")
            .setContentText("Elige cómo registrar este movimiento")
            .setWhen(suggestion.receivedAt)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(review)
            .addAction(0, "Registrar", review)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(24 * 60 * 60 * 1000L)
            .build()
        try {
            manager.notify(suggestion.id, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The user can revoke notifications between the permission check and delivery.
        }
    }

    fun cancel(context: Context, id: String) = NotificationManagerCompat.from(context).cancel(id, NOTIFICATION_ID)
}
