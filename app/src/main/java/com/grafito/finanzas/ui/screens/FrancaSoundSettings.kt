package com.grafito.finanzas.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grafito.finanzas.data.UserPreferences
import com.grafito.finanzas.notifications.FrancaNotices
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
internal fun FrancaSoundSettings() {
    val context = LocalContext.current
    val app = context.applicationContext
    val prefs = remember(app) { UserPreferences(app) }
    val sounds = remember(prefs) {
        prefs.interfaceSounds.catch { error -> if (error is IOException) emit(false) else throw error }
    }
    val enabled by sounds.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val playSound = LocalFrancaSounds.current
    var message by remember { mutableStateOf<String?>(null) }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName))
        } catch (_: ActivityNotFoundException) {
            message = "Abre Notificaciones de Franca desde Ajustes de Android."
        } catch (_: SecurityException) {
            message = "Abre Notificaciones de Franca desde Ajustes de Android."
        }
    }

    SettingsRows {
        SettingsRow(Icons.Outlined.VolumeUp, "Sonidos en la app",
            modifier = Modifier.toggleable(value = enabled, role = Role.Switch) { next ->
                scope.launch {
                    try {
                        prefs.setInterfaceSounds(next)
                    } catch (_: IOException) {
                        message = "No se pudo guardar el ajuste de sonido."
                    }
                }
            },
            summary = "Toques y confirmaciones"
        ) {
            Switch(checked = enabled, onCheckedChange = null)
        }
        if (enabled) {
            SettingsDivider()
            SettingsRow(Icons.Outlined.PlayArrow, "Escuchar confirmación",
                modifier = Modifier.clickable(role = Role.Button) { playSound(FrancaCue.SAVED) }) {}
        }
    }
    SettingsRows {
        FrancaNotices.channels.forEachIndexed { index, channel ->
            if (index > 0) SettingsDivider()
            SettingsLink(Icons.Outlined.NotificationsNone, channel.title, null) {
                openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channel.ensure(app)))
            }
        }
        SettingsDivider()
        SettingsLink(Icons.Outlined.NotificationsNone, "Permisos de avisos", null) {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
