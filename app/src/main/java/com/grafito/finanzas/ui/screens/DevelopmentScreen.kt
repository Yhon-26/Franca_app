package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.ui.components.DeskMascotAnimation
import com.grafito.finanzas.ui.components.DeskMascotScene
import com.grafito.finanzas.ui.components.ScreenTitle

internal enum class DevelopmentSection(val title: String, val hint: String) {
    REPORTS("Reportes", "Puedes consultar tus ingresos y gastos del mes en Inicio.")
}

@Composable
internal fun DevelopmentScreen(section: DevelopmentSection, scene: DeskMascotScene?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        ScreenTitle(section.title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 342.dp).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                key(section) { DeskMascotAnimation(scene) }
                Spacer(Modifier.height(16.dp))
                Text("En desarrollo", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(section.hint, Modifier.widthIn(max = 288.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
