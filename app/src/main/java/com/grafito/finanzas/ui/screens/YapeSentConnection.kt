package com.grafito.finanzas.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grafito.finanzas.notifications.YapeSentAccessibilityService

internal enum class YapeSentConnection {
    DISABLED, UNAVAILABLE, DISCONNECTED, CONNECTED
}

@Composable
internal fun rememberYapeSentConnection(): YapeSentConnection {
    val application = LocalContext.current.applicationContext
    val connected by YapeSentAccessibilityService.connected.collectAsStateWithLifecycle()
    fun enabled() = YapeSentAccessibilityService.enabledInAndroid(application)
    var enabledInAndroid by remember(application) { mutableStateOf(enabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabledInAndroid = enabled() }
    DisposableEffect(application) {
        val resolver = application.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabledInAndroid = enabled()
            }
        }
        try {
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer
            )
        } catch (_: SecurityException) {
            enabledInAndroid = enabled()
        }
        enabledInAndroid = enabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return when {
        connected -> YapeSentConnection.CONNECTED
        enabledInAndroid == true -> YapeSentConnection.DISCONNECTED
        enabledInAndroid == false -> YapeSentConnection.DISABLED
        else -> YapeSentConnection.UNAVAILABLE
    }
}

@Composable
internal fun YapeSentConnectionNotice() {
    if (rememberYapeSentConnection() != YapeSentConnection.DISCONNECTED) return
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text("Yape enviado está desconectado", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(YAPE_RECONNECT_GUIDE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            YapeAccessibilityButton("Restablecer lector")
        }
    }
}

@Composable
internal fun YapeAccessibilityButton(label: String) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        unavailable = try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            false
        } catch (_: ActivityNotFoundException) {
            true
        } catch (_: SecurityException) {
            true
        }
    }) { Text(label) }
    if (unavailable) {
        Text("Abre Ajustes de Android y busca «Accesibilidad».", style = MaterialTheme.typography.bodySmall)
    }
}

internal const val YAPE_RECONNECT_GUIDE =
    "En Accesibilidad, abre «Franca · Yape enviado», apágalo y vuelve a encenderlo. Luego regresa a Franca."
