package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.model.PendingYapeState
import com.grafito.finanzas.ui.theme.HomePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val yapeDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))
private val yapeTime = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun YapePendingScreen(
    pending: PendingYapeState,
    modifier: Modifier,
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onRetry: () -> Unit
) {
    val notices = (pending as? PendingYapeState.Available)?.notices.orEmpty()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item(key = "title") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onBack, shape = MaterialTheme.shapes.small,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = HomePalette.NavSelection, contentColor = HomePalette.Navy
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a Inicio")
                }
                Text("Yapeos por revisar", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = HomePalette.Navy)
            }
        }
        if (pending == PendingYapeState.Loading) item(key = "loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (pending == PendingYapeState.Failed) item(key = "error") {
            YapePendingLoadError(onRetry)
        }
        if (pending is PendingYapeState.Available && notices.isEmpty()) {
            item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Estás al día", style = MaterialTheme.typography.titleMedium, color = HomePalette.Navy)
                    Text("Los próximos avisos de Yape aparecerán en Inicio.",
                        style = MaterialTheme.typography.bodyMedium, color = HomePalette.Muted)
                }
            }
        }
        itemsIndexed(notices, key = { _, notice -> notice.id }) { index, notice ->
            val date = remember(notice.receivedAt, zone, today) {
                val time = Instant.ofEpochMilli(notice.receivedAt).atZone(zone)
                val day = if (time.toLocalDate() == today) "Hoy" else yapeDate.format(time)
                "$day · ${yapeTime.format(time)}"
            }
            PendingYapeRow(notice, date) { onReview(notice.id) }
            if (index != notices.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun YapePendingLoadError(onRetry: () -> Unit) {
    SettingsCard {
        Text("No se pudieron cargar los avisos de Yape.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun PendingYapeRow(notice: YapeSuggestion, date: String, onReview: () -> Unit) {
    val amount = remember(notice.amountCents) { Money.format(notice.amountCents) }
    Surface(
        onClick = onReview, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = HomePalette.Paper, border = BorderStroke(1.dp, HomePalette.Line)
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).background(HomePalette.IconBackground, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(if (notice.outgoing) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest,
                    contentDescription = null, tint = HomePalette.Blue, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (notice.outgoing) "Yape enviado" else "Yape recibido", style = MaterialTheme.typography.titleSmall,
                    color = HomePalette.Navy)
                Text(date, style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
            }
            Text(amount, Modifier.widthIn(max = 122.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = if (notice.outgoing) HomePalette.Ink else HomePalette.Income)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = HomePalette.Blue, modifier = Modifier.size(18.dp))
        }
    }
}
