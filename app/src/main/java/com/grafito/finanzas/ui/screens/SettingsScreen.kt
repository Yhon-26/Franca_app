package com.grafito.finanzas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.BuildConfig
import com.grafito.finanzas.data.FavoriteEntity
import com.grafito.finanzas.data.YapeConfirmation
import com.grafito.finanzas.model.FinanceState
import com.grafito.finanzas.model.FinanceViewModel
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.ui.components.ScreenTitle
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.theme.HomePalette

private enum class SettingsPage(val title: String) {
    YAPE("Yape"), BCP("Pagos BCP"), SOUND("Sonido y avisos"), DEFAULT_ACCOUNT("Cuenta predeterminada"),
    FAVORITES("Mis atajos"), BACKUP("Copias de seguridad"), PRIVACY("Privacidad")
}

@Composable
internal fun SettingsScreen(
    data: FinanceState,
    openQuickOnLaunch: Boolean,
    modifier: Modifier = Modifier,
    onOpenQuickChanged: (Boolean) -> Unit,
    onRemoveFavorite: (FavoriteEntity) -> Unit,
    onDefaultAccountChanged: (Long) -> Unit,
    onFavoriteAccountChanged: (FavoriteEntity, Long) -> Unit,
    backupViewModel: FinanceViewModel,
    onYapeSaved: (YapeConfirmation) -> Unit
) {
    var selectedPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val overviewScroll = rememberScrollState()
    val page = selectedPage
    BackHandler(enabled = page != null) { selectedPage = null }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (page == null) {
            Box(Modifier.padding(vertical = 18.dp)) { ScreenTitle("Ajustes") }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { selectedPage = null }) {
                    Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver a Ajustes",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
                Text(page.title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
        }
        key(page) {
            val scroll = if (page == null) overviewScroll else rememberScrollState()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == null) 18.dp else 12.dp)) {
                when (page) {
                    null -> {
                        val defaultAccount = data.accounts.find { it.id == data.defaultAccountId }?.name
                        val favoriteCount = data.favorites.size
                        SettingsGroup("Detección de pagos") {
                            SettingsLink(Icons.Outlined.QrCode2, SettingsPage.YAPE.title, "Pagos recibidos y enviados") {
                                selectedPage = SettingsPage.YAPE
                            }
                            SettingsDivider()
                            SettingsLink(Icons.Outlined.AccountBalance, "BCP", "En pantalla y por correo") {
                                selectedPage = SettingsPage.BCP
                            }
                            SettingsDivider()
                            SettingsLink(Icons.Outlined.NotificationsNone, SettingsPage.SOUND.title, "Toques y tonos de Franca") {
                                selectedPage = SettingsPage.SOUND
                            }
                        }
                        SettingsGroup("Registro rápido") {
                            SettingsRow(Icons.Outlined.Keyboard, "Abrir el registro al iniciar", modifier = Modifier.toggleable(
                                value = openQuickOnLaunch, role = Role.Switch, onValueChange = onOpenQuickChanged
                            )) {
                                Switch(checked = openQuickOnLaunch, onCheckedChange = null, modifier = Modifier.scale(0.84f),
                                    colors = SwitchDefaults.colors(uncheckedTrackColor = HomePalette.NavSelection,
                                        uncheckedBorderColor = HomePalette.NavIcon, uncheckedThumbColor = HomePalette.Muted))
                            }
                            SettingsDivider()
                            SettingsLink(Icons.Outlined.AccountBalanceWallet, SettingsPage.DEFAULT_ACCOUNT.title, defaultAccount) {
                                selectedPage = SettingsPage.DEFAULT_ACCOUNT
                            }
                            SettingsDivider()
                            SettingsLink(Icons.Outlined.StarOutline, SettingsPage.FAVORITES.title, when (favoriteCount) {
                                0 -> "Sin atajos guardados"
                                1 -> "1 atajo guardado"
                                else -> "$favoriteCount atajos guardados"
                            }) { selectedPage = SettingsPage.FAVORITES }
                        }
                        SettingsGroup("Datos") {
                            SettingsLink(Icons.Outlined.Folder, SettingsPage.BACKUP.title, "Crear o restaurar una copia cifrada") {
                                selectedPage = SettingsPage.BACKUP
                            }
                            SettingsDivider()
                            SettingsLink(Icons.Outlined.Lock, SettingsPage.PRIVACY.title, "Almacenamiento de tus movimientos") {
                                selectedPage = SettingsPage.PRIVACY
                            }
                        }
                        val version = "Franca · ${BuildConfig.VERSION_NAME}"
                        Text(if (BuildConfig.DEBUG) "$version (pruebas)" else version, Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsPage.YAPE -> YapePanel(data.accounts, data.categories, data.defaultAccountId, data.transactions, onYapeSaved)
                    SettingsPage.BCP -> BcpPanel()
                    SettingsPage.SOUND -> FrancaSoundSettings()
                    SettingsPage.DEFAULT_ACCOUNT -> DefaultAccountSettings(data, onDefaultAccountChanged)
                    SettingsPage.FAVORITES -> FavoriteSettings(data, onRemoveFavorite, onFavoriteAccountChanged)
                    SettingsPage.BACKUP -> BackupPanel(backupViewModel)
                    SettingsPage.PRIVACY -> SettingsCard(Modifier.fillMaxWidth()) {
                        Text("Datos locales", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Franca guarda tus movimientos en el teléfono. Si conectas Gmail, consulta las confirmaciones de BCP a través de Google.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        SettingsRows(content = content)
    }
}

@Composable
internal fun SettingsRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SettingsRows(modifier) { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun SettingsLink(icon: ImageVector, title: String, summary: String?, onClick: () -> Unit) {
    val playSound = LocalFrancaSounds.current
    SettingsRow(icon, title, Modifier.clickable(role = Role.Button) {
        playSound(FrancaCue.TAP)
        onClick()
    }, summary) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun DefaultAccountSettings(data: FinanceState, onSelect: (Long) -> Unit) {
    Text("Franca propone esta cuenta para los nuevos movimientos y para los atajos sin cuenta propia.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    SettingsRows(Modifier.selectableGroup()) {
        data.accounts.forEachIndexed { index, account ->
            if (index > 0) SettingsDivider()
            val selected = data.defaultAccountId == account.id
            SettingsRow(Icons.Outlined.AccountBalanceWallet, account.name, modifier = Modifier.selectable(
                selected = selected, role = Role.RadioButton, onClick = { onSelect(account.id) }
            )) {
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

@Composable
private fun FavoriteSettings(data: FinanceState, onRemove: (FavoriteEntity) -> Unit, onAccountChange: (FavoriteEntity, Long) -> Unit) {
    if (data.favorites.isEmpty()) {
        SettingsRows {
            SettingsRow(Icons.Outlined.StarOutline, "No hay atajos guardados", summary = "Crea uno desde Atajos en Inicio.") {}
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.favorites.forEach { favorite ->
                SettingsCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(favorite.title, style = MaterialTheme.typography.titleSmall)
                            Text(Money.format(favorite.amountCents), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(favorite) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Eliminar ${favorite.title}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("Registrar en", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.accounts.forEach { account ->
                            FilterChip(selected = (favorite.accountId ?: data.defaultAccountId) == account.id,
                                onClick = { onAccountChange(favorite, account.id) }, label = { Text(account.name) })
                        }
                    }
                }
            }
        }
    }
}
