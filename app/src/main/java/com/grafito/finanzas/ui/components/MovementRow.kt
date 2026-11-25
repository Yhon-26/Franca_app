package com.grafito.finanzas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.theme.HomePalette

@Composable
internal fun MovementRow(
    entry: TransactionEntity,
    category: CategoryEntity?,
    accounts: Map<Long, AccountEntity>,
    dateLabel: String,
    highlighted: Boolean = false,
    showCategory: Boolean = false,
    onClick: () -> Unit
) {
    val account = accounts[entry.accountId]?.name.orEmpty()
    val isTransfer = entry.type == EntryType.TRANSFER
    val destination = entry.toAccountId?.let { accounts[it]?.name }.orEmpty()
    val (sign, amountColor, defaultIcon) = when (entry.type) {
        EntryType.INCOME -> Triple("+", HomePalette.Income, Icons.Outlined.Payments)
        EntryType.TRANSFER -> Triple("", HomePalette.Ink, Icons.Outlined.SwapHoriz)
        else -> Triple("−", HomePalette.Expense, Icons.AutoMirrored.Outlined.ReceiptLong)
    }
    val title = when (entry.type) {
        EntryType.TRANSFER -> "Transferencia"
        EntryType.INCOME -> entry.note.ifBlank { category?.name ?: "Ingreso" }
        else -> entry.note.ifBlank { category?.name ?: "Gasto sin categoría" }
    }
    val accountLabel = if (isTransfer) listOf(account, destination).filter(String::isNotBlank).joinToString(" → ") else account
    val categoryLabel = category?.name?.takeIf { showCategory && !isTransfer && it != title }
    val detail = listOfNotNull(accountLabel.takeIf(String::isNotBlank), categoryLabel, dateLabel).joinToString(" · ")
    val amount = remember(entry.amountCents) { Money.format(entry.amountCents) }
    val highlight by animateColorAsState(
        if (highlighted) HomePalette.IconBackground else Color.Transparent,
        animationSpec = tween(220), label = "Movimiento destacado"
    )

    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .background(highlight).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).background(HomePalette.IconBackground, CircleShape), contentAlignment = Alignment.Center) {
            Icon(category?.takeUnless { isTransfer }?.let(::categoryIcon) ?: defaultIcon, contentDescription = null,
                tint = HomePalette.Muted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = HomePalette.Ink, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(sign + amount, Modifier.widthIn(max = 132.dp), color = amountColor, textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
    }
}
