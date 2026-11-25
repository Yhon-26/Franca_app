package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.*
import com.grafito.finanzas.ui.components.*
import com.grafito.finanzas.ui.theme.Graphite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLabel = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(data: FinanceState, modifier: Modifier = Modifier, onSelect: (TransactionEntity) -> Unit, onQuickAdd: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("TODOS") }
    val filtered = remember(search, filter, data.transactions, data.categories) {
        data.transactions.filter { entry ->
            val category = data.categories.find { it.id == entry.categoryId }?.name.orEmpty()
            (filter == "TODOS" || entry.type == filter) &&
                (search.isBlank() || (entry.note + category + Money.format(entry.amountCents)).contains(search, ignoreCase = true))
        }
    }
    val zone = ZoneId.systemDefault()
    val byDay = remember(filtered, zone) { filtered.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() } }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Movimientos", onQuickAdd)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar movimiento") },
            placeholder = { Text("Importe o concepto") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("TODOS" to "Todos", EntryType.EXPENSE to "Gastos", EntryType.INCOME to "Ingresos", EntryType.TRANSFER to "Transferencias").forEach { (type, title) ->
                FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(title) })
            }
        }
        if (filtered.isNotEmpty()) {
            Text("${filtered.size} ${if (filtered.size == 1) "movimiento" else "movimientos"}",
                color = Graphite.Muted, style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (filtered.isEmpty()) {
                item {
                    if (data.transactions.isEmpty()) {
                        EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Tu historial está vacío", "Registra un ingreso o gasto desde el encabezado.")
                    } else {
                        EmptyState(Icons.Outlined.SearchOff, "Sin resultados", "Prueba con otro importe, concepto o filtro.")
                    }
                }
            }
            byDay.forEach { (day, entries) ->
                item(key = "day-$day") {
                    Text(day.format(dayLabel), Modifier.padding(top = 12.dp, bottom = 7.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelLarge, color = Graphite.Secondary)
                    HorizontalDivider(color = Graphite.Divider)
                }
                items(entries, key = { it.id }) { entry ->
                    EntryRow(entry, data.categories, data.accounts, showDate = false) { onSelect(entry) }
                    HorizontalDivider(Modifier.padding(start = 50.dp), color = Graphite.Divider)
                }
            }
        }
    }
}
