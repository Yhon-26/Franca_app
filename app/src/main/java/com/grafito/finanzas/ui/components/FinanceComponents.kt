package com.grafito.finanzas.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.theme.Graphite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@Composable
fun GraphiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.background(Graphite.Surface, MaterialTheme.shapes.medium).padding(16.dp),
        content = content
    )
}

@Composable
fun FlowTotals(income: Long, expense: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowAmount("Ingresos", income, Icons.Outlined.SouthWest, Graphite.Income, Modifier.weight(1f))
        FlowAmount("Gastos", expense, Icons.Outlined.NorthEast, Graphite.Expense, Modifier.weight(1f))
    }
}

@Composable
private fun FlowAmount(label: String, cents: Long, icon: ImageVector, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, color = Graphite.Secondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(Money.format(cents), color = color, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
fun EntryRow(
    entry: TransactionEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    showDate: Boolean = true,
    onClick: () -> Unit
) {
    val category = categories.find { it.id == entry.categoryId }
    val account = accounts.find { it.id == entry.accountId }?.name.orEmpty()
    val destination = accounts.find { it.id == entry.toAccountId }?.name.orEmpty()
    val title = when (entry.type) {
        EntryType.TRANSFER -> "Transferencia"
        else -> entry.note.ifBlank { category?.name ?: if (entry.type == EntryType.EXPENSE) "Gasto sin categoría" else "Ingreso" }
    }
    val icon = when (entry.type) {
        EntryType.TRANSFER -> Icons.Outlined.SwapHoriz
        EntryType.INCOME -> category?.let(::categoryIcon) ?: Icons.Outlined.SouthWest
        else -> category?.let(::categoryIcon) ?: Icons.Outlined.NorthEast
    }
    val amountColor = if (entry.type == EntryType.EXPENSE) Graphite.Expense else if (entry.type == EntryType.INCOME) Graphite.Income else Graphite.Primary
    val date = SimpleDateFormat(if (showDate) "dd MMM · HH:mm" else "HH:mm", Locale.forLanguageTag("es-PE")).format(Date(entry.occurredAt))
    val context = if (entry.type == EntryType.TRANSFER) "$account → $destination" else account
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(40.dp).background(Graphite.Surface, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (category != null) Graphite.Primary else amountColor,
                modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(context, date).filter(String::isNotBlank).joinToString(" · "), color = Graphite.Muted,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            (if (entry.type == EntryType.EXPENSE) "−" else if (entry.type == EntryType.INCOME) "+" else "") + Money.format(entry.amountCents),
            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
            color = amountColor, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(Graphite.Surface, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Graphite.Muted, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = Graphite.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
