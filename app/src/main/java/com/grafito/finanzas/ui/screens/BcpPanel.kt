package com.grafito.finanzas.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grafito.finanzas.data.FinanceDatabase
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.notifications.BcpScreenAccessibilityService
import com.grafito.finanzas.notifications.AccessibilityReceiptReader
import com.grafito.finanzas.receipts.*
import com.grafito.finanzas.ui.components.SectionTitle
import kotlinx.coroutines.flow.catch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BcpPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Lectura del comprobante")
        BcpScreenPanel()
        BcpPendingPanel()
        SectionTitle("Respaldo opcional")
        BcpGmailPanel()
    }
}

@Composable
private fun BcpScreenPanel() {
    val context = LocalContext.current
    val application = context.applicationContext
    val settings = remember(application) { BcpScreenSettings(application) }
    val report by remember(settings) {
        settings.report.catch { emit(BcpScreenReport(status = BcpScreenStatus.ERROR)) }
    }.collectAsStateWithLifecycle(BcpScreenReport())
    val connected by BcpScreenAccessibilityService.connected.collectAsStateWithLifecycle()
    var enabledInAndroid by remember {
        mutableStateOf(BcpScreenAccessibilityService.enabledInAndroid(application))
    }
    val notifications = remember(application) { NotificationManagerCompat.from(application) }
    var canNotify by remember { mutableStateOf(notifications.areNotificationsEnabled()) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = notifications.areNotificationsEnabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabledInAndroid = BcpScreenAccessibilityService.enabledInAndroid(application)
        canNotify = notifications.areNotificationsEnabled()
    }

    SettingsCard(Modifier.fillMaxWidth()) {
        Text(when {
            connected -> "Lector BCP activo"
            enabledInAndroid == true -> "Lector BCP desconectado"
            enabledInAndroid == false -> "Falta permiso de accesibilidad"
            else -> "Revisa el permiso de accesibilidad"
        }, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        ReaderLastRead(connected, report.lastReadAt, "El lector espera la confirmación «¡Operación exitosa!» de tu próximo pago habitual.")
        if (!connected || report.status != null) Text(when {
            !connected && enabledInAndroid == true ->
                "En Accesibilidad, apaga «Franca · Pagos BCP» y vuelve a encenderlo."
            !connected -> "Busca «Franca · Pagos BCP» en Accesibilidad de Android."
            else -> when (report.status) {
                null -> "Esperando la confirmación de un pago enviado en BCP."
                BcpScreenStatus.VISIBLE -> when {
                    report.fields and BcpScreenParser.HISTORY_SCREEN != 0 -> "Detalle de un movimiento anterior. Franca espera la confirmación de un pago nuevo."
                    report.fields and BcpScreenParser.UNSUCCESSFUL_SCREEN != 0 -> "El texto incluye un estado incompatible con un envío exitoso."
                    else -> "Franca recibió texto de BCP, pero no encontró «Operación exitosa» en el contenido accesible."
                }
                BcpScreenStatus.WINDOW_UNAVAILABLE -> when {
                    report.signals and AccessibilityReceiptReader.QUERY_DENIED != 0 ->
                        "Android rechazó la consulta del comprobante en el último intento."
                    report.signals and AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING != 0 ->
                        "Android enumeró la ventana del evento de BCP, pero no entregó su contenido accesible."
                    report.signals and AccessibilityReceiptReader.ACTIVE_ROOT_AVAILABLE != 0 &&
                        report.signals and AccessibilityReceiptReader.ACTIVE_ROOT_MATCHES == 0 ->
                        "La ventana activa ya no era BCP y no se pudo recuperar el comprobante desde su evento."
                    else -> "Android no entregó una ventana ni un origen de BCP consultables en el último intento."
                }
                BcpScreenStatus.UNAVAILABLE -> "Android entregó la pantalla de BCP sin texto legible."
                BcpScreenStatus.INCOMPLETE -> "Comprobante incompleto. Revisa los campos reconocidos."
                BcpScreenStatus.DETECTED -> "Pago BCP detectado. Toca el aviso para revisarlo."
                BcpScreenStatus.DUPLICATE -> "Esta operación BCP ya aparece en Franca."
                BcpScreenStatus.ERROR -> "No se pudo revisar la última pantalla de BCP."
            }
        }, style = MaterialTheme.typography.labelLarge,
            color = if (connected && report.status in setOf(BcpScreenStatus.DETECTED, BcpScreenStatus.DUPLICATE))
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (!connected) OutlinedButton(onClick = {
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: Exception) { error = "Abre Accesibilidad desde los Ajustes de Android." }
        }) { Text(if (enabledInAndroid == true) "Restablecer lector" else "Abrir Accesibilidad de Android") }
        if (report.checkedAt > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Última comprobación · ${DateFormat.getDateTimeInstance().format(Date(report.checkedAt))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.signals != 0) {
                TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }) {
                    Text(if (diagnosticsExpanded) "Ocultar estado de lectura" else "Ver estado de lectura")
                }
                if (diagnosticsExpanded) {
                    listOf(
                        AccessibilityReceiptReader.EVENT_RECEIVED to "Evento de BCP recibido",
                        AccessibilityReceiptReader.SYSTEM_WINDOW_EVENT to "Lectura desde un cambio de ventana del sistema",
                        AccessibilityReceiptReader.EVENT_WINDOW_LISTED to "Ventana del evento enumerada",
                        AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE to "Ventana BCP consultable",
                        AccessibilityReceiptReader.TEXT_AVAILABLE to "Texto accesible de BCP",
                        AccessibilityReceiptReader.CONFIRMATION_RECOGNIZED to "Confirmación reconocida",
                        AccessibilityReceiptReader.READ_FROM_EVENT to "Comprobante leído desde el evento",
                        AccessibilityReceiptReader.QUERY_DENIED to "Consulta denegada por Android",
                        AccessibilityReceiptReader.QUERY_FAILED to "Otro error de consulta"
                    ).forEach { (flag, label) ->
                        val state = when {
                            report.signals and flag != 0 -> "Sí"
                            flag == AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE &&
                                report.signals and AccessibilityReceiptReader.WINDOW_QUERY == 0 -> "Sin consultar"
                            else -> "No"
                        }
                        Text("$label · $state",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (report.signals and AccessibilityReceiptReader.TEXT_AVAILABLE != 0) {
                        listOf(
                            BcpScreenParser.SUCCESS_TITLE to "Título de operación exitosa",
                            BcpScreenParser.HISTORY_SCREEN to "Texto de historial",
                            BcpScreenParser.UNSUCCESSFUL_SCREEN to "Texto incompatible con un envío exitoso"
                        ).forEach { (flag, label) ->
                            Text("$label · ${if (report.fields and flag != 0) "Sí" else "No"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (report.fields and (BcpScreenParser.SUCCESS_TITLE or BcpScreenParser.AMOUNT or
                    BcpScreenParser.DATE_TIME or BcpScreenParser.OPERATION or BcpScreenParser.OUTGOING) != 0) {
                val fields = listOf(
                    BcpScreenParser.AMOUNT to "Importe del pago",
                    BcpScreenParser.DATE_TIME to "Fecha y hora",
                    BcpScreenParser.OPERATION to "Número de operación",
                    BcpScreenParser.OUTGOING to "Envío confirmado"
                )
                fields.forEach { (flag, label) ->
                    val found = report.fields and flag != 0
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (found) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (found) "Reconocido" else "Pendiente",
                            tint = if (found) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (!canNotify) {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName))
            }) { Text("Permitir avisos de Franca") }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BcpPendingPanel() {
    val context = LocalContext.current
    val application = context.applicationContext
    val pending by remember(application) {
        FinanceDatabase.get(application).bcpMailSuggestions().pending().catch { emit(emptyList()) }
    }.collectAsStateWithLifecycle(emptyList())
    if (pending.isNotEmpty()) {
        SettingsCard(Modifier.fillMaxWidth()) {
            Text("Gastos por revisar", style = MaterialTheme.typography.titleSmall)
            pending.forEach { item ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.format(item.amountCents), color = MaterialTheme.colorScheme.error)
                        Text(DateFormat.getDateTimeInstance().format(Date(item.occurredAt)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { context.startActivity(BcpMailAlerts.reviewIntent(context, item.id)) }) {
                        Text("Revisar")
                    }
                }
            }
        }
    }
}
