package com.grafito.finanzas.ui.screens

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grafito.finanzas.receipts.*
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BcpGmailPanel() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val application = context.applicationContext
    val settings = remember(application) { BcpGmailSettings(application) }
    val client = remember(activity, application) {
        if (activity == null) Identity.getAuthorizationClient(application) else Identity.getAuthorizationClient(activity)
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val connection by remember(settings) { settings.connection.catch { emit(GmailConnection(status = "error")) } }
        .collectAsStateWithLifecycle(GmailConnection())
    val notifications = remember(application) { NotificationManagerCompat.from(application) }
    var canNotify by remember { mutableStateOf(notifications.areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = notifications.areNotificationsEnabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = notifications.areNotificationsEnabled() }

    suspend fun connected(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || GMAIL_READ_SCOPE !in result.grantedScopes) {
            message = "Selecciona el permiso de lectura de Gmail en la pantalla de Google y confirma."
            return
        }
        val email = BcpGmailClient(token).email()
        settings.connect(email)
        BcpGmailSync.schedule(application)
    }

    fun runAction(action: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: GmailHttpError) {
                message = if (error.status == 403) "Gmail rechazó el acceso. Revisa la API y el usuario de prueba en Google Cloud."
                else "No pudimos consultar Gmail. Vuelve a intentarlo."
            } catch (error: ApiException) {
                val unregistered = error.statusMessage?.contains("UNREGISTERED_ON_API_CONSOLE") == true
                Log.w("FrancaGmailAuth", "Google authorization status=${error.statusCode}, unregistered=$unregistered")
                message = when {
                    unregistered || error.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
                        "Google aún no reconoce el cliente Android de Franca. Revisa el registro en Google Cloud (código ${error.statusCode})."
                    error.statusCode == CommonStatusCodes.CANCELED ->
                        "La autorización se cerró. Vuelve a conectar Gmail y confirma el permiso en Google."
                    error.statusCode == CommonStatusCodes.NETWORK_ERROR ->
                        "Google no pudo conectarse. Revisa internet e intenta de nuevo."
                    else -> "Google no pudo autorizar Gmail (código ${error.statusCode})."
                }
            } catch (_: Exception) {
                message = "No pudimos completar la operación. Vuelve a intentarlo."
            } finally {
                busy = false
            }
        }
    }

    val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data = result.data
        if (data != null) {
            runAction { connected(client.getAuthorizationResultFromIntent(data)) }
        } else {
            Log.w("FrancaGmailAuth", "Google returned no authorization data, result=${result.resultCode}")
            message = "Google cerró la autorización. Toca Conectar Gmail y completa los pasos de la pantalla de Google."
        }
    }

    SettingsCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Correo electrónico", style = MaterialTheme.typography.titleSmall)
                Text(if (connection.email == null) "Respaldo desactivado" else "Respaldo activado",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar" else "Configurar")
            }
        }
        if (connection.email != null) {
            OutlinedButton(onClick = { runAction { BcpGmailSync.disconnect(application) } }, enabled = !busy) {
                Text("Desactivar correo")
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Busca pagos BCP de los últimos 7 días, incluidos pagos a celular y QR. Google solicita acceso de lectura a Gmail. Tú confirmas cada gasto.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val needsAuthorization = connection.email == null || connection.status == "authorization"
                Text("Respaldo opcional. Desactivarlo detiene las revisiones de Gmail y mantiene disponible el lector de pantalla de BCP.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (needsAuthorization) {
                    Button(enabled = !busy, onClick = {
                        runAction {
                            val result = client.authorize(gmailAuthorizationRequest(connection.email)).await()
                            val resolution = result.pendingIntent
                            if (resolution != null) authorization.launch(IntentSenderRequest.Builder(resolution.intentSender).build())
                            else connected(result)
                        }
                    }) { Text(if (connection.email == null) "Conectar Gmail" else "Volver a autorizar Gmail") }
                } else {
                    Text(when (connection.status) {
                        "ok" -> "Gmail conectado"
                        "network" -> "Esperando conexión para revisar Gmail"
                        "error" -> "No se pudo revisar Gmail. Puedes volver a buscar."
                        else -> "Primera revisión de Gmail pendiente"
                    }, style = MaterialTheme.typography.bodyMedium)
                    connection.lastSync?.let { time ->
                        Text("Última revisión · ${DateFormat.getDateTimeInstance().format(Date(time))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { BcpGmailSync.checkNow(application); message = "Búsqueda programada." }, enabled = !busy) {
                        Text("Buscar ahora")
                    }
                }
                if (connection.email != null) {
                    if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Permitir avisos de Franca")
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        message?.takeIf { !expanded }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
