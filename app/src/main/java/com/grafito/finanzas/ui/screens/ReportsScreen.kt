package com.grafito.finanzas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.model.FinanceMath
import com.grafito.finanzas.model.FinanceState
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.model.MonthlyReport
import com.grafito.finanzas.model.MonthlyTotals
import com.grafito.finanzas.model.ReportExpenseSelection
import com.grafito.finanzas.ui.components.DeskMascotAnimation
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.components.categoryIcon
import com.grafito.finanzas.ui.components.rememberDeskMascotScene
import com.grafito.finanzas.ui.theme.HomePalette
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val reportLocale = Locale.forLanguageTag("es-PE")
private val reportMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", reportLocale)

internal fun reportMonthLabel(month: YearMonth): String =
    month.format(reportMonthFormat).replaceFirstChar { it.titlecase(reportLocale) }

@Composable
internal fun ReportsScreen(
    data: FinanceState,
    modifier: Modifier = Modifier,
    highlightedEntryId: Long? = null,
    onSelect: (TransactionEntity) -> Unit,
    onQuickAdd: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zone)
    val latestEntryMonth = remember(data.transactions, zone) {
        data.transactions.maxOfOrNull { it.occurredAt }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }
    }
    val latestMonth = maxOf(currentMonth, latestEntryMonth ?: currentMonth)
    var savedMonth by rememberSaveable { mutableStateOf(currentMonth.toString()) }
    var showMonths by rememberSaveable { mutableStateOf(false) }
    var showMovements by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val month = YearMonth.parse(savedMonth)
    val scroll = rememberLazyListState()

    if (showMovements) {
        BackHandler { showMovements = false }
        HistoryScreen(
            data = data,
            modifier = modifier,
            highlightedEntryId = highlightedEntryId,
            reportSelection = ReportExpenseSelection(month, categoryId),
            onBackToReport = { showMovements = false },
            onSelect = onSelect,
            onQuickAdd = onQuickAdd
        )
        return
    }

    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val range = remember(month, zone) { FinanceMath.monthRange(month, zone) }
    val report = key(data.transactions, range) {
        produceState<MonthlyReport?>(null) {
            value = withContext(Dispatchers.Default) { FinanceMath.monthlyReport(data.transactions, range) }
        }.value
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = scroll,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenTitle("Reportes", onQuickAdd)
                MonthControl(
                    month, canAdvance = month < latestMonth,
                    onPrevious = { savedMonth = month.minusMonths(1).toString() },
                    onNext = { savedMonth = month.plusMonths(1).toString() },
                    onChoose = { showMonths = true }
                )
            }
        }
        if (report == null) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 18.dp)) }
        } else {
            item(key = "summary") {
                MonthlyBalance(report.totals, Modifier.padding(top = 14.dp))
            }
            item(key = "categories-heading") {
                Text(
                    "Gastos por categoría", Modifier.padding(top = 26.dp, bottom = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, color = HomePalette.Navy
                )
            }
            if (report.expensesByCategory.isEmpty()) {
                item(key = "empty-expenses") { EmptySpending(showMascot = data.transactions.isEmpty()) }
            }
            items(report.expensesByCategory, key = { it.categoryId ?: "uncategorized" }) { spending ->
                val category = categories[spending.categoryId]
                CategoryReportRow(
                    name = category?.name ?: "Sin categoría",
                    cents = spending.cents,
                    share = spending.cents.toDouble() / report.totals.expenses,
                    icon = category?.let(::categoryIcon) ?: Icons.AutoMirrored.Outlined.ReceiptLong
                ) {
                    categoryId = spending.categoryId
                    showMovements = true
                }
                HorizontalDivider(Modifier.padding(start = 50.dp), color = HomePalette.Line)
            }
        }
    }

    if (showMonths) {
        MonthPicker(
            month, latestMonth,
            onChoose = { savedMonth = it.toString(); showMonths = false },
            onClose = { showMonths = false }
        )
    }
}

@Composable
private fun MonthControl(
    month: YearMonth,
    canAdvance: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChoose: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = HomePalette.Paper,
        border = BorderStroke(1.dp, HomePalette.Line)
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Mes anterior", tint = HomePalette.Blue)
            }
            TextButton(onClick = onChoose, modifier = Modifier.weight(1f)) {
                Text(reportMonthLabel(month), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onNext, enabled = canAdvance,
                colors = IconButtonDefaults.iconButtonColors(contentColor = HomePalette.Blue)
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Mes siguiente")
            }
        }
    }
}

@Composable
private fun MonthPicker(month: YearMonth, latestMonth: YearMonth, onChoose: (YearMonth) -> Unit, onClose: () -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(month.year) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Elegir mes") },
        containerColor = HomePalette.Paper,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Año anterior")
                    }
                    Text(year.toString(), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium, color = HomePalette.Navy)
                    IconButton(onClick = { year++ }, enabled = year < latestMonth.year) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Año siguiente")
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        months.forEach { number ->
                            val item = YearMonth.of(year, number)
                            val name = item.month.getDisplayName(TextStyle.FULL, reportLocale)
                            val shortName = item.month.getDisplayName(TextStyle.SHORT, reportLocale)
                                .removeSuffix(".").replaceFirstChar { it.titlecase(reportLocale) }
                            FilterChip(
                                selected = item == month,
                                onClick = { onChoose(item) },
                                enabled = item <= latestMonth,
                                label = { Text(shortName) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "$name $year" }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun MonthlyBalance(totals: MonthlyTotals, modifier: Modifier = Modifier) {
    val balance = totals.income - totals.expenses
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = HomePalette.Paper,
        border = BorderStroke(1.dp, HomePalette.Line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Balance del mes", style = MaterialTheme.typography.labelLarge, color = HomePalette.Muted)
                Text(Money.format(balance), style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (balance < 0) HomePalette.Expense else HomePalette.Navy)
            }
            HorizontalDivider(color = HomePalette.Line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthlyAmount("Ingresos", totals.income, Icons.Outlined.SouthWest, HomePalette.Income, Modifier.weight(1f))
                MonthlyAmount("Gastos", totals.expenses, Icons.Outlined.NorthEast, HomePalette.Expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthlyAmount(label: String, cents: Long, icon: ImageVector, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
        }
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = color)
    }
}

@Composable
private fun CategoryReportRow(name: String, cents: Long, share: Double, icon: ImageVector, onClick: () -> Unit) {
    val shareLabel = remember(share) {
        NumberFormat.getPercentInstance(reportLocale).apply { maximumFractionDigits = 1 }.let { format ->
            if (share < 0.001) "< ${format.format(0.001)}" else format.format(share)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "Ver movimientos de $name", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(40.dp).background(HomePalette.IconBackground, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = HomePalette.Navy, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = HomePalette.Navy)
                Text(Money.format(cents), Modifier.widthIn(max = 124.dp), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = HomePalette.Ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(HomePalette.Line)) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(HomePalette.Blue))
                }
                Text(shareLabel, style = MaterialTheme.typography.labelSmall, color = HomePalette.Muted)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmptySpending(showMascot: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sin gastos en este mes", style = MaterialTheme.typography.titleSmall, color = HomePalette.Navy)
        Text("Los gastos de este periodo aparecerán aquí por categoría.",
            style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
        if (showMascot) {
            Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 206.dp).fillMaxWidth()) {
                DeskMascotAnimation(rememberDeskMascotScene())
            }
        }
    }
}
