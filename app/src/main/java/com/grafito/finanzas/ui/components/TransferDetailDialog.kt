package com.grafito.finanzas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.theme.HomePalette

@Composable
internal fun TransferDetailDialog(
    amountCents: Long,
    from: String,
    to: String,
    note: String,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    MaterialTheme(colorScheme = HomePalette.panelScheme) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = HomePalette.Paper,
            title = { Text("Transferencia") },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        Money.format(amountCents),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        color = HomePalette.Navy
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = HomePalette.NavSelection
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TransferAccount("Desde", from, Icons.Outlined.NorthEast)
                            HorizontalDivider(color = HomePalette.Line)
                            TransferAccount("Hacia", to, Icons.Outlined.SouthWest)
                        }
                    }
                    if (note.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Concepto", style = MaterialTheme.typography.labelMedium, color = HomePalette.Muted)
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = HomePalette.Ink)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onClose) { Text("Cerrar") } },
            dismissButton = {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.semantics { contentDescription = "Eliminar transferencia" },
                    colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.Expense)
                ) { Text("Eliminar") }
            }
        )
    }
}

@Composable
private fun TransferAccount(label: String, accountName: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = HomePalette.Blue)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = HomePalette.Muted)
            Text(accountName, style = MaterialTheme.typography.titleSmall, color = HomePalette.Navy)
        }
    }
}
