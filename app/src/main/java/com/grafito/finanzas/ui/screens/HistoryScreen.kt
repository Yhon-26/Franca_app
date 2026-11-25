package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.model.FinanceState
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.ReportExpenseSelection
import com.grafito.finanzas.model.MovementDateFilter
import com.grafito.finanzas.model.filterMovements
import com.grafito.finanzas.ui.components.MovementRow
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.theme.HomePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val historyLocale = Locale.forLanguageTag("es-PE")
private val shortDate = DateTimeFormatter.ofPattern("d MMM", historyLocale)
private val dateWithYear = DateTimeFormatter.ofPattern("d MMM yyyy", historyLocale)
private val movementTime = DateTimeFormatter.ofPattern("HH:mm", historyLocale)
private enum class HistoryFilterPanel { ACCOUNT, DATE, RANGE }

private val movementFilters = listOf(
    null to "Todos",
    EntryType.EXPENSE to "Gastos",
    EntryType.INCOME to "Ingresos",
    EntryType.TRANSFER to "Transferencias"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryScreen(
    data: FinanceState,
    modifier: Modifier = Modifier,
    highlightedEntryId: Long? = null,
    reportSelection: ReportExpenseSelection? = null,
    onBackToReport: () -> Unit = {},
    onSelect: (TransactionEntity) -> Unit,
    onQuickAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateFilter by rememberSaveable { mutableStateOf(MovementDateFilter.ALL) }
    var startDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var panel by rememberSaveable { mutableStateOf<HistoryFilterPanel?>(null) }
    val focus = LocalFocusManager.current
    val accounts = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val hasMovements = data.transactions.isNotEmpty()
    val selectedAccount = accounts[accountId]
    val startDate = startDay?.let(LocalDate::ofEpochDay)
    val endDate = endDay?.let(LocalDate::ofEpochDay)
    val period = if (reportSelection == null) dateFilter.range(today, zone, startDate, endDate) else null
    val hasExtraFilters = selectedAccount != null || period != null

    fun clearFilters() {
        type = null
        accountId = null
        dateFilter = MovementDateFilter.ALL
        startDay = null
        endDay = null
    }
    val grouped = key(data.transactions, accounts, categories, zone, reportSelection) {
        produceState<Map<LocalDate, List<TransactionEntity>>?>(null, query, type, selectedAccount?.id, period) {
            value = withContext(Dispatchers.Default) {
                val entries = if (reportSelection == null) data.transactions else FinanceMath.reportExpenses(
                    data.transactions, FinanceMath.monthRange(reportSelection.month, zone), reportSelection.categoryId
                )
                filterMovements(entries, accounts, categories, query, type, selectedAccount?.id, period).groupBy { entry ->
                    Instant.ofEpochMilli(entry.occurredAt).atZone(zone).toLocalDate()
                }
            }
        }
    }.value

    LazyColumn(
        modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)
    ) {
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (reportSelection == null) {
                    ScreenTitle("Movimientos", onQuickAdd.takeIf { hasMovements })
                } else {
                    val category = categories[reportSelection.categoryId]?.name ?: "Sin categoría"
                    ReportHistoryHeading(category, reportMonthLabel(reportSelection.month), onBackToReport)
                }
                if (hasMovements) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar movimiento") },
                        placeholder = { Text("Importe, cuenta o categoría") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = HomePalette.Blue) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpiar búsqueda") } }
                        } else null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = HomePalette.Paper,
                            unfocusedContainerColor = HomePalette.Paper,
                            unfocusedBorderColor = HomePalette.Line
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
                    )
                    HistoryFilterControls(
                        account = selectedAccount,
                        periodLabel = period?.let { movementPeriodLabel(dateFilter, startDate, endDate) },
                        showDateFilter = reportSelection == null,
                        onDate = { focus.clearFocus(); panel = HistoryFilterPanel.DATE },
                        onAccount = { focus.clearFocus(); panel = HistoryFilterPanel.ACCOUNT },
                        onClear = if (hasExtraFilters && grouped?.isEmpty() != true) { { clearFilters(); focus.clearFocus() } } else null
                    )
                    if (reportSelection == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        movementFilters.forEach { (filter, label) ->
                            val selected = type == filter
                            FilterChip(
                                selected = selected,
                                onClick = { type = filter; focus.clearFocus() },
                                label = { Text(label) },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = HomePalette.Paper,
                                    labelColor = HomePalette.Navy,
                                    selectedContainerColor = HomePalette.Navy,
                                    selectedLabelColor = HomePalette.Paper
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = HomePalette.Line
                                )
                            )
                        }
                    }
                }
            }
        }

        when {
            !hasMovements && reportSelection == null -> item(key = "first-movement") {
                HistoryMessage(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Aún no tienes movimientos",
                    description = "Registra un gasto o ingreso para verlo aquí.",
                    actionLabel = "Registrar movimiento",
                    onAction = onQuickAdd
                )
            }
            grouped == null -> item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
            grouped.isEmpty() -> item(key = "no-results") {
                val description = when {
                    hasExtraFilters -> "Prueba con otros filtros o quítalos para ampliar los resultados."
                    query.isNotBlank() -> "Prueba con otro importe, cuenta o concepto."
                    reportSelection != null -> "Ya no hay gastos de esta categoría en el mes seleccionado."
                    else -> "Aún no tienes movimientos de este tipo."
                }
                val actionLabel = when {
                    query.isNotBlank() && (reportSelection == null || hasExtraFilters) -> "Limpiar búsqueda y filtros"
                    hasExtraFilters -> "Limpiar filtros"
                    reportSelection == null -> "Ver todos"
                    query.isNotBlank() -> "Limpiar búsqueda"
                    else -> "Volver a Reportes"
                }
                HistoryMessage(
                    icon = Icons.Outlined.SearchOff,
                    title = "Sin resultados",
                    description = description,
                    actionLabel = actionLabel,
                    onAction = {
                        if (reportSelection != null && query.isBlank() && !hasExtraFilters) onBackToReport() else {
                            query = ""
                            clearFilters()
                            focus.clearFocus()
                        }
                    }
                )
            }
            else -> grouped.forEach { (day, entries) ->
                item(key = "day-$day") {
                    HistoryDayHeading(day, today)
                }
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    val time = movementTime.format(Instant.ofEpochMilli(entry.occurredAt).atZone(zone))
                    MovementRow(
                        entry, categories[entry.categoryId], accounts, time,
                        highlighted = highlightedEntryId == entry.id,
                        showCategory = true
                    ) { onSelect(entry) }
                    if (index != entries.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = HomePalette.Line)
                    }
                }
            }
        }
    }

    when (panel) {
        HistoryFilterPanel.ACCOUNT -> HistoryAccountPicker(data.accounts, selectedAccount?.id, onDismiss = { panel = null }) {
            accountId = it
            panel = null
        }
        HistoryFilterPanel.DATE -> HistoryPeriodPicker(dateFilter, onDismiss = { panel = null }) {
            if (it == MovementDateFilter.CUSTOM) panel = HistoryFilterPanel.RANGE else {
                dateFilter = it
                panel = null
            }
        }
        HistoryFilterPanel.RANGE -> HistoryRangePicker(startDate, endDate, today, onDismiss = { panel = HistoryFilterPanel.DATE }) { start, end ->
            startDay = start.toEpochDay()
            endDay = end.toEpochDay()
            dateFilter = MovementDateFilter.CUSTOM
            panel = null
        }
        null -> Unit
    }
}

@Composable
private fun ReportHistoryHeading(category: String, monthLabel: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalIconButton(onClick = onBack, shape = MaterialTheme.shapes.small) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a Reportes")
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Movimientos", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text("$category · $monthLabel", style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
        }
    }
}

@Composable
private fun HistoryDayHeading(day: LocalDate, today: LocalDate) {
    val date = day.format(if (day.year == today.year) shortDate else dateWithYear)
    val title = when (day) {
        today -> "Hoy · $date"
        today.minusDays(1) -> "Ayer · $date"
        else -> date
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(HomePalette.Blue, CircleShape))
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = HomePalette.Navy)
        HorizontalDivider(Modifier.weight(1f), color = HomePalette.Line)
    }
}

@Composable
private fun HistoryMessage(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).background(HomePalette.IconBackground, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = HomePalette.Navy, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = HomePalette.Navy)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = HomePalette.Muted)
            TextButton(onClick = onAction, contentPadding = PaddingValues(end = 12.dp)) { Text(actionLabel) }
        }
    }
}
