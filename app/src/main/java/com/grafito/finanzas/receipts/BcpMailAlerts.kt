package com.grafito.finanzas.receipts

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
import com.grafito.finanzas.data.BcpMailSuggestion
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.notifications.FrancaNotices

internal object BcpMailAlerts {
    private const val ALERT_ID = 2

    fun reviewIntent(context: Context, id: String) = Intent(context, BcpMailReviewActivity::class.java)
        .setData(Uri.fromParts("franca-bcp", id, null))

    fun show(context: Context, payment: BcpMailSuggestion): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        val channel = FrancaNotices.bcpExpense.ensure(context)
        val review = PendingIntent.getActivity(context, 0, reviewIntent(context, payment.id), PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_franca_monochrome)
            .setContentTitle("Tienes un gasto por revisar").build()
        val alert = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_franca_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.franca_mint))
            .setContentTitle("Pago BCP · ${Money.format(payment.amountCents)}")
            .setContentText("Elige cómo registrar este pago")
            .setWhen(payment.occurredAt).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(review).addAction(0, "Registrar", review)
            .setOnlyAlertOnce(true).build()
        return try {
            manager.notify(payment.id, ALERT_ID, alert)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, id: String) = NotificationManagerCompat.from(context).cancel(id, ALERT_ID)
}
