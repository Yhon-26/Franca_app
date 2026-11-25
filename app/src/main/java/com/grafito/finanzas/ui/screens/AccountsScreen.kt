package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafito.finanzas.data.*
import com.grafito.finanzas.model.*
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.theme.HomePalette
import java.util.Locale

@Composable
fun AccountsScreen(
    data: FinanceState, modifier: Modifier = Modifier,
    onQuickAdd: (Long) -> Unit,
    onAddAccount: (String, Long) -> Unit,
    onTransfer: (Long, Long, Long, String) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit
) {
    var createDialog by rememberSaveable { mutableStateOf(false) }
    var transferSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val balances = remember(data.accounts, data.transactions) {
        FinanceMath.accountBalances(data.accounts, data.transactions)
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp)) {
        ScreenTitle("Mis cuentas")
        Spacer(Modifier.height(18.dp))
        Text("Saldo total", color = HomePalette.Muted, style = MaterialTheme.typography.labelLarge)
        Text(Money.format(balances.values.sum()), Modifier.fillMaxWidth(), color = HomePalette.Ink,
            style = MaterialTheme.typography.displayMedium, maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 44.sp))
        Spacer(Modifier.height(10.dp))
        Button(onClick = { createDialog = true }, modifier = Modifier.heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nueva cuenta")
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data.accounts.forEachIndexed { index, account ->
                key(account.id) {
                    WalletAccountCard(account, balances.getValue(account.id), walletStyle(account.name, index),
                        canTransfer = data.accounts.size >= 2, onRegister = { onQuickAdd(account.id) },
                        onTransfer = { transferSourceId = account.id }, onDelete = { deleteAccountId = account.id })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = HomePalette.Muted, modifier = Modifier.size(16.dp))
            Text("Las cuentas se registran manualmente en Franca.", color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (createDialog) {
        var submitted by remember { mutableStateOf(false) }
        var name by rememberSaveable { mutableStateOf("") }
        var opening by rememberSaveable { mutableStateOf("") }
        val parsed = if (opening.isBlank() || opening == "0" || opening == "0.00" || opening == "0,00") 0L else Money.parse(opening)
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Crear cuenta") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nombre · Ej.: Yape, BCP") }, singleLine = true)
                    OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Saldo inicial (S/)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("El saldo inicial no es un ingreso: representa el dinero que ya tienes.", color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = !submitted && name.isNotBlank() && parsed != null, onClick = { if (!submitted && parsed != null) { submitted = true; onAddAccount(name, parsed); createDialog = false } }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } }
        )
    }
    val transferSource = data.accounts.find { it.id == transferSourceId }
    if (transferSource != null && data.accounts.size >= 2) {
        var submitted by remember { mutableStateOf(false) }
        var from by rememberSaveable(transferSource.id) { mutableLongStateOf(transferSource.id) }
        var to by rememberSaveable(transferSource.id) { mutableLongStateOf(data.accounts.first { it.id != transferSource.id }.id) }
        var amount by rememberSaveable { mutableStateOf("") }
        var note by rememberSaveable { mutableStateOf("") }
        val cents = Money.parse(amount)
        AlertDialog(
            onDismissRequest = { transferSourceId = null },
            title = { Text("Transferir dinero") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Origen", color = HomePalette.Muted)
                    AccountChips(data.accounts, from) { from = it }
                    Text("Destino", color = HomePalette.Muted)
                    AccountChips(data.accounts, to) { to = it }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Monto S/") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = note, onValueChange = { note = it.take(160) }, label = { Text("Nota (opcional)") }, singleLine = true)
                    Text("La transferencia se refleja en ambas cuentas.", style = MaterialTheme.typography.bodySmall, color = HomePalette.Muted)
                }
            },
            confirmButton = { TextButton(enabled = !submitted && cents != null && from != to, onClick = { if (!submitted && cents != null) { submitted = true; onTransfer(cents, from, to, note); transferSourceId = null } }) { Text("Transferir") } },
            dismissButton = { TextButton(onClick = { transferSourceId = null }) { Text("Cancelar") } }
        )
    }
    data.accounts.find { it.id == deleteAccountId }?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteAccountId = null },
            title = { Text("Eliminar ${account.name}") },
            text = { Text("Solo se pueden eliminar cuentas sin movimientos y si existe otra cuenta.") },
            confirmButton = { TextButton(onClick = { onDeleteAccount(account); deleteAccountId = null }) { Text("Eliminar", color = HomePalette.Expense) } },
            dismissButton = { TextButton(onClick = { deleteAccountId = null }) { Text("Cancelar") } }
        )
    }
}

private enum class WalletStyle(val icon: ImageVector, val background: Color) {
    CASH(Icons.Outlined.Payments, Color(0xFFF6F8FD)),
    WALLET(Icons.Outlined.AccountBalanceWallet, Color(0xFFDCE9FF)),
    BANK(Icons.Outlined.AccountBalance, Color(0xFFE8EEFC))
}

private fun walletStyle(name: String, index: Int): WalletStyle = when (name.trim().lowercase(Locale.ROOT)) {
    "efectivo" -> WalletStyle.CASH
    "yape" -> WalletStyle.WALLET
    "bcp" -> WalletStyle.BANK
    else -> WalletStyle.entries[index % WalletStyle.entries.size]
}

@Composable
private fun WalletAccountCard(account: AccountEntity, balance: Long, style: WalletStyle, canTransfer: Boolean,
                              onRegister: () -> Unit, onTransfer: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val bank = style == WalletStyle.BANK
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = style.background,
        border = BorderStroke(1.dp, HomePalette.Line)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 116.dp).walletDecoration(style)) {
            Column(Modifier.fillMaxWidth(if (bank) 0.61f else 0.82f)
                .align(Alignment.CenterStart).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(style.icon, contentDescription = null, tint = HomePalette.Navy, modifier = Modifier.size(18.dp))
                    Text(account.name.uppercase(Locale.ROOT), color = HomePalette.Navy,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.7.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(Money.format(balance), Modifier.fillMaxWidth(), color = HomePalette.Ink,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp))
                Text("Saldo inicial · ${Money.format(account.openingBalanceCents)}", color = HomePalette.Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 6.dp)) {
                IconButton(onClick = { menuOpen = true }) {
                    Box(Modifier.size(36.dp).background(
                        if (bank) Color(0xFF1235AA) else Color.White.copy(alpha = 0.75f), MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Opciones de ${account.name}",
                            tint = if (bank) Color.White else HomePalette.Navy, modifier = Modifier.size(20.dp))
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Registrar movimiento") }, onClick = { menuOpen = false; onRegister() },
                        leadingIcon = { Icon(Icons.Outlined.PostAdd, null) })
                    DropdownMenuItem(text = { Text("Transferir") }, onClick = { menuOpen = false; onTransfer() },
                        enabled = canTransfer, leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) })
                    DropdownMenuItem(text = { Text("Eliminar cuenta", color = HomePalette.Expense) }, onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = HomePalette.Expense) })
                }
            }
        }
    }
}

private fun Modifier.walletDecoration(style: WalletStyle) = drawWithCache {
    val w = size.width
    val h = size.height
    fun polygon(points: List<Offset>) = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    val stripes = polygon(listOf(Offset(w, 0f), Offset(w, h * 0.40f), Offset(w * 0.84f, h), Offset(w * 0.71f, h)))
    val stripesEdge = polygon(listOf(Offset(w, h * 0.60f), Offset(w, h), Offset(w * 0.89f, h)))
    val bankPanel = polygon(listOf(Offset(w * 0.69f, 0f), Offset(w, 0f), Offset(w, h), Offset(w * 0.57f, h)))
    val bankFold = polygon(listOf(Offset(w, 0f), Offset(w, h), Offset(w * 0.70f, h)))
    onDrawBehind {
        when (style) {
            WalletStyle.CASH -> {
                drawPath(stripes, Color(0xFFDDE7F8))
                drawPath(stripesEdge, Color(0xFFDDE7F8))
            }
            WalletStyle.WALLET -> {
                drawOval(Color(0xFFABC9FC), topLeft = Offset(w * 0.61f, -h * 1.08f), size = Size(w * 0.73f, h * 2.08f))
                drawOval(Color(0xFF759FF2), topLeft = Offset(w * 0.83f, -h * 0.28f), size = Size(w * 0.39f, h * 1.32f))
            }
            WalletStyle.BANK -> {
                drawPath(bankPanel, HomePalette.Blue)
                drawPath(bankFold, Color(0xFF193EC6))
            }
        }
    }
}

@Composable
private fun AccountChips(accounts: List<AccountEntity>, selected: Long, onChange: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.forEach { account ->
            FilterChip(selected = selected == account.id, onClick = { onChange(account.id) }, label = { Text(account.name) })
        }
    }
}
