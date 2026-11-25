package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.*
import com.grafito.finanzas.ui.components.FrancaBrand
import com.grafito.finanzas.ui.components.MovementRow
import com.grafito.finanzas.ui.components.SectionTitle
import com.grafito.finanzas.ui.components.categoryIcon
import com.grafito.finanzas.ui.theme.HomePalette
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HomeScreen(
    data: FinanceState,
    pendingYape: List<YapeSuggestion>,
    highlightedEntryId: Long?,
    modifier: Modifier = Modifier,
    onReviewYape: (String) -> Unit,
    onNewFavorite: () -> Unit,
    onSeeAll: () -> Unit,
    onSelect: (TransactionEntity) -> Unit,
    onFavorite: (FavoriteEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val month = YearMonth.now(zone)
    val (incomes, expenses) = remember(data.transactions, month, zone) {
        FinanceMath.monthlyTotals(data.transactions, FinanceMath.monthRange(month, zone))
    }
    val balance = remember(data.accounts, data.transactions) {
        FinanceMath.accountBalances(data.accounts, data.transactions).values.sum()
    }
    val accounts = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FrancaBrand()
            Spacer(Modifier.width(12.dp))
            Text("Hola", Modifier.weight(1f), color = HomePalette.Muted, textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceCard(balance, data.accounts.size, incomes, expenses)
            ThreeDotRule(Modifier.fillMaxWidth().height(6.dp))
            pendingYape.firstOrNull()?.let { latest ->
                HomePendingYape(latest, pendingYape.size) { onReviewYape(latest.id) }
            }
            YapeSentConnectionNotice()
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Atajos", "+ Nuevo", onNewFavorite)
            if (data.favorites.isEmpty()) {
                HomeEmpty(Icons.Outlined.StarOutline, "Aún no tienes atajos", "Crea uno para tus gastos frecuentes.")
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = HomePalette.Paper,
                    border = BorderStroke(1.dp, HomePalette.Line)
                ) {
                    Column {
                        data.favorites.forEachIndexed { index, favorite ->
                            val account = accounts[favorite.accountId ?: data.defaultAccountId] ?: data.accounts.firstOrNull()
                            FavoriteRow(favorite, account, categories[favorite.categoryId]) { onFavorite(favorite) }
                            if (index != data.favorites.lastIndex) {
                                HorizontalDivider(color = HomePalette.Line.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            val recent = data.transactions.take(5)
            SectionTitle("Últimos movimientos", "Ver todos", onSeeAll)
            Spacer(Modifier.height(6.dp))
            if (recent.isEmpty()) {
                EmptyRecentMovements()
            } else {
                val now = System.currentTimeMillis()
                recent.forEachIndexed { index, entry ->
                    key(entry.id) {
                        MovementRow(entry, categories[entry.categoryId], accounts, homeDate(entry.occurredAt, now),
                            highlighted = highlightedEntryId == entry.id) { onSelect(entry) }
                    }
                    if (index != recent.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp), color = HomePalette.Line)
                }
            }
        }
    }
}

@Composable
private fun HomePendingYape(latest: YapeSuggestion, count: Int, onClick: () -> Unit) {
    val title = when {
        count == 1 -> "1 yapeo por revisar"
        count < 20 -> "$count yapeos por revisar"
        else -> "Yapeos por revisar"
    }
    val direction = if (latest.outgoing) "Enviado" else "Recibido"
    val detail = "$direction · ${Money.format(latest.amountCents)}"
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = HomePalette.Paper,
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, HomePalette.Line)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeIcon(if (latest.outgoing) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest,
                Modifier.size(40.dp), 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (count > 1) "Último · $detail" else detail, style = MaterialTheme.typography.bodySmall,
                    color = HomePalette.Muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyRecentMovements() {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThreeDotRule(Modifier.padding(top = 7.dp).size(width = 44.dp, height = 6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Aún no tienes movimientos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Activa Yape en Ajustes para registrar tus pagos automáticamente.",
                Modifier.fillMaxWidth(),
                color = HomePalette.Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun HomeRegisterButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Registrar movimiento" },
        icon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(22.dp)) },
        text = { Text("Registrar", style = MaterialTheme.typography.labelLarge) },
        shape = CircleShape,
        containerColor = HomePalette.Blue,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    )
}

@Composable
private fun BalanceCard(balance: Long, accounts: Int, income: Long, expense: Long) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).balanceBackground().padding(24.dp)) {
        Text("Saldo total", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(Money.format(balance), color = Color.White, modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium.copy(lineHeight = 47.sp), maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 40.sp))
        Text("En $accounts ${if (accounts == 1) "cuenta" else "cuentas"}",
            color = HomePalette.NavSelection, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.23f))
        Text("Este mes", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceTotal("Ingresos", income, Icons.Outlined.SouthWest, Modifier.weight(1f))
            BalanceTotal("Gastos", expense, Icons.Outlined.NorthEast, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BalanceTotal(label: String, amount: Long, icon: ImageVector, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = HomePalette.NavSelection, modifier = Modifier.size(15.dp))
            Text(label, color = HomePalette.NavSelection, style = MaterialTheme.typography.labelLarge)
        }
        Text(Money.format(amount), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}

private fun Modifier.balanceBackground() = drawWithCache {
    val surface = Brush.horizontalGradient(listOf(HomePalette.Navy, HomePalette.GradientEnd))
    val ink = Brush.horizontalGradient(0.44f to Color.Transparent, 1f to Color.White.copy(alpha = 0.19f))
    val line = Stroke(0.8.dp.toPx())
    onDrawBehind {
        drawRect(surface)
        rotate(-28f, pivot = Offset(size.width, 0f)) {
            repeat(8) { index ->
                drawOval(
                    ink,
                    topLeft = Offset(size.width * (0.47f + index * 0.045f), size.height * (-0.20f + index * 0.034f)),
                    size = Size(size.width * 0.72f, size.height * 0.88f),
                    style = line
                )
            }
        }
    }
}

@Composable
private fun ThreeDotRule(modifier: Modifier) {
    Spacer(modifier.drawWithCache {
        val radius = 3.dp.toPx()
        onDrawBehind {
            drawLine(HomePalette.Line, Offset(radius, center.y), Offset(size.width - radius, center.y), 1.dp.toPx())
            for (x in listOf(radius, center.x, size.width - radius)) {
                drawCircle(HomePalette.GradientEnd, radius = radius, center = Offset(x, center.y))
            }
        }
    })
}

@Composable
private fun FavoriteRow(favorite: FavoriteEntity, account: AccountEntity?, category: CategoryEntity?, onRegister: () -> Unit) {
    val amount = Money.format(favorite.amountCents)
    val detail = account?.let { "$amount · ${it.name}" } ?: amount
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(HomePalette.NavSelection, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center) {
            Icon(category?.let(::categoryIcon) ?: Icons.Outlined.StarOutline, contentDescription = null,
                tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(favorite.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRegister, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(38.dp).background(HomePalette.Blue, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = "Usar atajo ${favorite.title}",
                    tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
    }
}

private val homeDayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))
private val homeTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun homeDate(time: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val entry = Instant.ofEpochMilli(time).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (entry.toLocalDate() == today) "Hoy ${homeTimeFormat.format(entry)}" else homeDayFormat.format(entry)
}

@Composable
private fun HomeIcon(icon: ImageVector, modifier: Modifier, iconSize: Dp) {
    Box(modifier.background(HomePalette.IconBackground, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = HomePalette.Muted, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun HomeEmpty(icon: ImageVector, title: String, description: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = HomePalette.Paper,
        border = BorderStroke(1.dp, HomePalette.Line)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeIcon(icon, Modifier.size(40.dp), 20.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
