package com.grafito.finanzas.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.EntryType

internal fun movementSavedMessage(type: String): String = when (type) {
    EntryType.INCOME -> "Ingreso guardado"
    EntryType.EXPENSE -> "Gasto guardado"
    else -> "Transferencia guardada"
}

@Composable
fun ScreenTitle(title: String, onQuickAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        if (onQuickAdd != null) {
            FilledTonalIconButton(onClick = onQuickAdd, modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.small) {
                Icon(Icons.Outlined.PostAdd, contentDescription = "Registrar nuevo gasto o ingreso")
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
