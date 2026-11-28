package com.grafito.finanzas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.ui.theme.HomePalette

@Composable
internal fun MovementFeedbackHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Surface(
            modifier = Modifier.widthIn(max = 344.dp).fillMaxWidth(),
            shape = CircleShape,
            color = HomePalette.Navy,
            contentColor = HomePalette.Paper,
            shadowElevation = 3.dp
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = CircleShape,
                    color = HomePalette.Income,
                    contentColor = HomePalette.Paper
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(16.dp))
                    }
                }
                Text(data.visuals.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                data.visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.NavIcon)
                    ) { Text(label) }
                }
                IconButton(onClick = data::dismiss) {
                    Icon(Icons.Outlined.Close, "Cerrar aviso", Modifier.size(18.dp))
                }
            }
        }
    }
}
