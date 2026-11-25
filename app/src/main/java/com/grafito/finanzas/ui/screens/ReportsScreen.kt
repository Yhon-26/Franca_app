package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.*
import com.grafito.finanzas.ui.components.*
import com.grafito.finanzas.ui.theme.Graphite
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

@Composable
fun ReportsScreen(data: FinanceState, modifier: Modifier = Modifier, onQuickAdd: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val month = FinanceMath.monthRange(YearMonth.now(zone), zone)
    val monthly = data.transactions.filter { it.occurredAt in month }
    val expenses = monthly.filter { it.type == EntryType.EXPENSE }
    val incomes = monthly.filter { it.type == EntryType.INCOME }
    val expenseTotal = expenses.sumOf { it.amountCents }
    val incomeTotal = incomes.sumOf { it.amountCents }
    val byCategory = expenses.groupBy { it.categoryId }.map { (id, entries) ->
        (data.categories.find { it.id == id }?.name ?: "Sin categoría") to entries.sumOf { it.amountCents }
    }.sortedByDescending { it.second }
    val lastDays = (6 downTo 0).map { before ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -before) }
        val day = cal.get(Calendar.YEAR) to cal.get(Calendar.DAY_OF_YEAR)
        val daily = data.transactions.filter { item ->
            if (item.type != EntryType.EXPENSE) false else {
                val c = Calendar.getInstance().apply { timeInMillis = item.occurredAt }
                (c.get(Calendar.YEAR) to c.get(Calendar.DAY_OF_YEAR)) == day
            }
        }.sumOf { it.amountCents }
        SimpleDateFormat("EEE", Locale.forLanguageTag("es-PE")).format(cal.time).replaceFirstChar { it.uppercase() } to daily
    }
    val max = lastDays.maxOf { it.second }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenTitle("Reportes", onQuickAdd)
        GraphiteCard(Modifier.fillMaxWidth()) {
            Text("Balance del mes", color = Graphite.Secondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(5.dp))
            Text(Money.format(incomeTotal - expenseTotal), style = MaterialTheme.typography.displaySmall)
            Text("Ingresos menos gastos.", color = Graphite.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Graphite.Divider)
            Spacer(Modifier.height(12.dp))
            FlowTotals(incomeTotal, expenseTotal)
        }
        GraphiteCard(Modifier.fillMaxWidth()) {
            SectionTitle("Gastos de los últimos 7 días")
            Spacer(Modifier.height(4.dp))
            Text(if (max > 0) "Máximo diario · ${Money.format(max)}" else "Sin gastos en estos días.",
                style = MaterialTheme.typography.bodySmall, color = Graphite.Muted)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                lastDays.forEach { (label, cents) ->
                    val amount = Money.format(cents)
                    Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$label, $amount en gastos" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(102.dp), contentAlignment = Alignment.BottomCenter
                        ) {
                            if (cents > 0) {
                                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight((cents.toFloat() / max).coerceAtLeast(0.035f))
                                    .background(Graphite.Expense, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                            }
                            HorizontalDivider(color = Graphite.Divider)
                        }
                        Text(label.take(3), style = MaterialTheme.typography.labelSmall, color = Graphite.Muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Gastos por categoría")
            if (byCategory.isEmpty()) {
                EmptyState(Icons.Outlined.BarChart, "Sin gastos este mes", "Tus gastos aparecerán aquí agrupados por categoría.")
            } else {
                byCategory.forEach { (name, cents) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(Money.format(cents), Modifier.weight(1f), textAlign = TextAlign.End,
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                        }
                        LinearProgressIndicator(
                            progress = { if (expenseTotal > 0) (cents.toFloat() / expenseTotal) else 0f },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = Graphite.Expense,
                            trackColor = Graphite.Raised
                        )
                    }
                }
            }
        }
    }
}
