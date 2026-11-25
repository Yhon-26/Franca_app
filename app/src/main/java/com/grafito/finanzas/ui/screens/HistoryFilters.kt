package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.model.MovementDateFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val filterDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

internal fun movementPeriodLabel(filter: MovementDateFilter, start: LocalDate?, end: LocalDate?): String = when (filter) {
    MovementDateFilter.ALL -> "Todas las fechas"
    MovementDateFilter.TODAY -> "Hoy"
    MovementDateFilter.THIS_MONTH -> "Este mes"
    MovementDateFilter.LAST_MONTH -> "Mes pasado"
    MovementDateFilter.CUSTOM -> if (start != null && end != null) {
        val startLabel = start.format(filterDateFormat)
        if (start == end) startLabel else "$startLabel – ${end.format(filterDateFormat)}"
    } else "Rango personalizado"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryFilterControls(
    account: AccountEntity?,
    periodLabel: String?,
    showDateFilter: Boolean,
    onDate: () -> Unit,
    onAccount: () -> Unit,
    onClear: (() -> Unit)?
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showDateFilter) {
            HistoryFilterChip(periodLabel?.let { "Fecha · $it" } ?: "Fecha", Icons.Outlined.DateRange, periodLabel != null, onDate)
        }
        val label = account?.let { "Cuenta · ${it.name}" } ?: "Cuenta"
        HistoryFilterChip(label, Icons.Outlined.AccountBalanceWallet, account != null, onAccount)
        if (onClear != null) {
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text("Limpiar filtros")
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.primary,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
internal fun HistoryAccountPicker(
    accounts: List<AccountEntity>,
    selected: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    HistoryFilterDialog("Elegir cuenta", onDismiss) {
        item(key = "all") { HistoryFilterOption("Todas las cuentas", selected == null) { onSelect(null) } }
        items(accounts, key = { it.id }) { account ->
            HistoryFilterOption(account.name, selected == account.id) { onSelect(account.id) }
        }
    }
}

@Composable
internal fun HistoryPeriodPicker(
    selected: MovementDateFilter,
    onDismiss: () -> Unit,
    onSelect: (MovementDateFilter) -> Unit
) {
    HistoryFilterDialog("Elegir fecha", onDismiss) {
        items(MovementDateFilter.entries, key = { it.name }) { filter ->
            HistoryFilterOption(movementPeriodLabel(filter, null, null), selected == filter) { onSelect(filter) }
        }
    }
}

@Composable
private fun HistoryFilterDialog(title: String, onDismiss: () -> Unit, options: LazyListScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).selectableGroup(), content = options)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun HistoryFilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryRangePicker(
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.toPickerMillis(),
        initialSelectedEndDateMillis = end?.toPickerMillis(),
        initialDisplayedMonthMillis = (start ?: today).toPickerMillis()
    )
    val selectedStart = state.selectedStartDateMillis?.toPickerDate()
    val selectedEnd = state.selectedEndDateMillis?.toPickerDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = selectedStart != null && selectedEnd != null,
                onClick = { if (selectedStart != null && selectedEnd != null) onApply(selectedStart, selectedEnd) }
            ) { Text("Aplicar fechas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.heightIn(max = 480.dp),
            title = { Text("Rango de fechas", Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = false
        )
    }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
