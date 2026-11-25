package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.AccountEntity
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.FavoriteEntity
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.model.FinanceState
import com.grafito.finanzas.model.Money
import com.grafito.finanzas.model.suggestCategory
import com.grafito.finanzas.ui.components.categoryIcon
import com.grafito.finanzas.ui.components.FrancaCue
import com.grafito.finanzas.ui.components.LocalFrancaSounds
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.flow.first

private enum class RegisterPicker { ACCOUNT, CATEGORY }

private val registerColors = HomePalette.scheme.copy(
    primaryContainer = HomePalette.NavSelection,
    onPrimaryContainer = HomePalette.Navy,
    secondaryContainer = HomePalette.NavSelection,
    onSecondaryContainer = HomePalette.Navy,
    surfaceContainer = HomePalette.Background,
    surfaceContainerHigh = HomePalette.Background,
    surfaceTint = HomePalette.Blue
)

private val amountCharacters = Regex("[0-9.,]*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    data: FinanceState,
    editing: TransactionEntity?,
    sourceShortcut: FavoriteEntity? = null,
    initialAccountId: Long? = null,
    saving: Boolean = false,
    favoriteOnly: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity?, String, Long, Long, Long?, String, Boolean) -> Unit,
    onDelete: (TransactionEntity) -> Unit
) {
    var type by rememberSaveable(editing?.id, favoriteOnly) {
        mutableStateOf(if (editing?.type == EntryType.INCOME) EntryType.INCOME else EntryType.EXPENSE)
    }
    var amount by rememberSaveable(editing?.id, sourceShortcut?.id) {
        val initial = editing?.amountCents ?: sourceShortcut?.amountCents
        mutableStateOf(initial?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }.orEmpty())
    }
    var accountId by rememberSaveable(editing?.id, sourceShortcut?.id, initialAccountId) {
        mutableStateOf(editing?.accountId ?: sourceShortcut?.accountId ?: initialAccountId ?: data.defaultAccountId)
    }
    var categoryId by rememberSaveable(editing?.id, sourceShortcut?.id) { mutableStateOf(editing?.categoryId ?: sourceShortcut?.categoryId) }
    var note by rememberSaveable(editing?.id, sourceShortcut?.id) { mutableStateOf((editing?.note ?: sourceShortcut?.title).orEmpty()) }
    var addFavorite by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable(editing?.id, sourceShortcut?.id) { mutableStateOf(editing != null || sourceShortcut != null) }
    var amountEditable by rememberSaveable(sourceShortcut?.id) { mutableStateOf(sourceShortcut == null) }
    var picker by rememberSaveable { mutableStateOf<RegisterPicker?>(null) }
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !saving || it != SheetValue.Hidden })
    val cents = Money.parse(amount)
    val account = data.accounts.find { it.id == accountId }
        ?: data.accounts.find { it.id == data.defaultAccountId } ?: data.accounts.firstOrNull()
    val categories = remember(data.categories, type) { data.categories.filter { it.type == type } }
    val category = categories.find { it.id == categoryId }
    val suggestion = remember(data.transactions, categories, cents, type, categoryId, editing) {
        if (editing != null || categoryId != null) null
        else suggestCategory(data.transactions, categories, type, cents)
    }
    val canAddFavorite = !favoriteOnly && editing == null && sourceShortcut == null && type == EntryType.EXPENSE
    val title = when {
        editing != null -> "Editar movimiento"
        favoriteOnly -> "Nuevo atajo"
        sourceShortcut != null -> sourceShortcut.title
        else -> "Registrar"
    }
    val saveLabel = when {
        saving -> "Guardando…"
        favoriteOnly -> "Guardar atajo"
        editing != null -> "Guardar cambios"
        type == EntryType.INCOME -> "Guardar ingreso"
        else -> "Guardar gasto"
    }
    val playSound = LocalFrancaSounds.current

    MaterialTheme(colorScheme = registerColors) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = HomePalette.Background,
            scrimColor = HomePalette.Navy.copy(alpha = 0.28f),
            dragHandle = { RegisterSheetHandle() }
        ) {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(sheetState, amountEditable) {
                snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
                if (amountEditable && picker == null && !confirmDeletion && !saving) {
                    amountFocus.requestFocus()
                    keyboard?.show()
                }
            }

            fun openPicker(next: RegisterPicker) {
                playSound(FrancaCue.TAP)
                focus.clearFocus()
                keyboard?.hide()
                picker = next
            }

            val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val sheetHeight = if (keyboardOpen) Modifier else Modifier.fillMaxHeight(0.72f)
            Column(Modifier.fillMaxWidth().then(sheetHeight).imePadding()) {
                RegisterHeader(title, "Cerrar registro", onDismiss, enabled = !saving)
                Column(
                    Modifier.weight(1f, fill = !keyboardOpen).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!favoriteOnly) {
                        MovementTypes(type, enabled = !saving) { next ->
                            if (type != next) {
                                playSound(FrancaCue.TAP)
                                type = next
                                categoryId = null
                            }
                        }
                    }
                    RegisterAmount(amount, { input -> if (input.length <= 12 && amountCharacters.matches(input)) amount = input },
                        amountFocus, invalid = amount.isNotBlank() && cents == null, enabled = !saving,
                        editable = amountEditable, onEdit = { amountEditable = true })
                    RegisterRow(
                        title = when {
                            account == null -> "Elegir cuenta"
                            type == EntryType.INCOME -> "A ${account.name}"
                            else -> "Desde ${account.name}"
                        },
                        icon = Icons.Outlined.AccountBalanceWallet,
                        actionLabel = "Elegir cuenta",
                        enabled = !saving && data.accounts.isNotEmpty(),
                        onClick = { openPicker(RegisterPicker.ACCOUNT) }
                    )
                    RegisterCategoryField(
                        category = category,
                        enabled = !saving,
                        onClick = { openPicker(RegisterPicker.CATEGORY) }
                    )
                    if (suggestion != null) {
                        SuggestionChip(
                            onClick = { categoryId = suggestion.id }, enabled = !saving,
                            label = { Text("Sugerida · ${suggestion.name}") },
                            icon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(16.dp)) }
                        )
                    }
                    if (sourceShortcut != null) {
                        ShortcutDetails(note, { note = it.take(160) }, detailsOpen, enabled = !saving) {
                            detailsOpen = !detailsOpen
                        }
                    } else if (!favoriteOnly) {
                        RegisterRow(
                            title = "Más detalles",
                            icon = Icons.AutoMirrored.Outlined.Notes,
                            description = if (canAddFavorite) "Nota y Guardar como atajo" else "Nota",
                            expanded = detailsOpen,
                            enabled = !saving,
                            onClick = { detailsOpen = !detailsOpen }
                        )
                    }
                    if (sourceShortcut == null && (favoriteOnly || detailsOpen)) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(if (favoriteOnly) 35 else 160) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (favoriteOnly) "Nombre del atajo (opcional)" else "Nota (opcional)") },
                            placeholder = { Text("Ej.: pasaje, desayuno, mercado") },
                            singleLine = true,
                            enabled = !saving,
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = HomePalette.Paper,
                                unfocusedContainerColor = HomePalette.Paper)
                        )
                        if (canAddFavorite) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Guardar como atajo", style = MaterialTheme.typography.titleSmall)
                                    Text("Para reutilizar los datos de este gasto", style = MaterialTheme.typography.bodySmall,
                                        color = HomePalette.Muted)
                                }
                                Switch(checked = addFavorite, onCheckedChange = { addFavorite = it }, enabled = !saving,
                                    modifier = Modifier.semantics { contentDescription = "Guardar como atajo" })
                            }
                        }
                    }
                    if (editing != null) {
                        TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); confirmDeletion = true },
                            enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = HomePalette.Expense)
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar movimiento", color = HomePalette.Expense)
                        }
                    }
                }
                HorizontalDivider(color = HomePalette.Line)
                Button(
                    onClick = {
                        if (cents != null && account != null) {
                            onSave(editing, type, cents, account.id, category?.id, note, addFavorite && canAddFavorite)
                        }
                    },
                    enabled = cents != null && account != null && !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(saveLabel) }
            }
        }

        when (picker) {
            RegisterPicker.ACCOUNT -> RegisterAccountSheet(data.accounts, account?.id, onDismiss = { picker = null }) { id ->
                accountId = id
                picker = null
            }
            RegisterPicker.CATEGORY -> RegisterCategorySheet(categories, categoryId,
                onSelect = { categoryId = it; picker = null }, onDismiss = { picker = null }, suggestion = suggestion,
                registrationContext = "Registrar · ${if (type == EntryType.INCOME) "Ingreso" else "Gasto"}")
            null -> Unit
        }
        if (confirmDeletion && editing != null) {
            AlertDialog(
                onDismissRequest = { confirmDeletion = false },
                title = { Text("Eliminar movimiento") },
                text = { Text("Puedes deshacer esta acción durante unos segundos.") },
                confirmButton = {
                    TextButton(onClick = { confirmDeletion = false; onDelete(editing) }) {
                        Text("Eliminar", color = HomePalette.Expense)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
internal fun RegisterSheetHandle() {
    Box(Modifier.padding(top = 9.dp, bottom = 8.dp).size(width = 34.dp, height = 4.dp)
        .background(HomePalette.NavIcon, CircleShape))
}

@Composable
private fun RegisterHeader(title: String, closeLabel: String, onClose: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = HomePalette.Navy)
        IconButton(onClick = onClose, enabled = enabled) {
            Box(Modifier.size(38.dp).background(HomePalette.Paper, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, closeLabel, tint = HomePalette.Navy, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MovementTypes(type: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(HomePalette.NavSelection, CircleShape).padding(3.dp).selectableGroup()) {
        listOf(EntryType.EXPENSE to "Gasto", EntryType.INCOME to "Ingreso").forEach { (value, label) ->
            val selected = type == value
            val color = if (selected) Color.White else HomePalette.Navy
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape)
                    .background(if (selected) HomePalette.Blue else Color.Transparent)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RegisterAmount(amount: String, onChange: (String) -> Unit, focus: FocusRequester, invalid: Boolean, enabled: Boolean,
                           editable: Boolean, onEdit: () -> Unit) {
    val textStyle = MaterialTheme.typography.displayMedium.copy(color = HomePalette.Ink)
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Monto", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = HomePalette.Muted)
            if (!editable) {
                TextButton(onClick = onEdit, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Editar")
                }
            }
        }
        BasicTextField(
            value = amount, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Monto" },
            enabled = enabled, readOnly = !editable, singleLine = true, textStyle = textStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(HomePalette.Blue),
            decorationBox = { field ->
                Row(Modifier.fillMaxWidth().heightIn(min = 53.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("S/", color = HomePalette.Navy, style = MaterialTheme.typography.titleLarge)
                    Box(Modifier.weight(1f)) {
                        if (amount.isEmpty()) Text("0.00", style = textStyle, color = HomePalette.Muted)
                        field()
                    }
                }
            }
        )
        val dividerColor = when {
            invalid -> HomePalette.Expense
            focused && editable -> HomePalette.Blue
            else -> HomePalette.Line
        }
        HorizontalDivider(thickness = if (focused && editable) 2.dp else 1.dp, color = dividerColor)
        if (invalid) {
            Text("Escribe un monto mayor que cero, con hasta dos decimales.", Modifier.padding(top = 4.dp),
                color = HomePalette.Expense, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShortcutDetails(note: String, onNoteChange: (String) -> Unit, expanded: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = HomePalette.Paper,
        border = BorderStroke(1.dp, HomePalette.Line)) {
        Column {
            RegisterRow(title = "Más detalles", icon = Icons.AutoMirrored.Outlined.Notes, enabled = enabled,
                description = "Nota y Guardar como atajo", expanded = expanded, framed = false, onClick = onToggle)
            if (expanded) {
                HorizontalDivider(color = HomePalette.Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nota", color = HomePalette.Navy, style = MaterialTheme.typography.titleSmall)
                    BasicTextField(note, onNoteChange, Modifier.weight(1f).semantics { contentDescription = "Nota" },
                        enabled = enabled, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HomePalette.Muted, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(HomePalette.Blue))
                }
                HorizontalDivider(color = HomePalette.Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Guardar como atajo", Modifier.weight(1f), color = HomePalette.Navy, style = MaterialTheme.typography.titleSmall)
                    Text("Ya es un atajo", color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun RegisterCategoryField(category: CategoryEntity?, enabled: Boolean, onClick: () -> Unit) {
    RegisterRow(
        title = "Categoría",
        icon = category?.let(::categoryIcon) ?: Icons.Outlined.Category,
        value = category?.name ?: "Opcional",
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun RegisterRow(
    title: String, icon: ImageVector, enabled: Boolean,
    value: String? = null, description: String? = null, expanded: Boolean? = null, actionLabel: String? = null, framed: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics {
            if (expanded != null) stateDescription = if (expanded) "Expandido" else "Contraído"
            if (actionLabel != null) contentDescription = actionLabel
        },
        shape = MaterialTheme.shapes.small, color = HomePalette.Paper,
        border = if (framed) BorderStroke(1.dp, HomePalette.Line) else null
    ) {
        Row(Modifier.heightIn(min = 49.dp).padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(HomePalette.NavSelection, MaterialTheme.shapes.extraSmall),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = HomePalette.Blue, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = HomePalette.Navy, style = MaterialTheme.typography.titleSmall)
                if (description != null) Text(description, color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (value != null) {
                Text(value, Modifier.widthIn(max = 124.dp), color = HomePalette.Muted,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val arrow = when (expanded) {
                true -> Icons.Outlined.ExpandLess
                false -> Icons.Outlined.ExpandMore
                null -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
            }
            Icon(arrow, contentDescription = null, tint = HomePalette.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterAccountSheet(accounts: List<AccountEntity>, selectedId: Long?, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HomePalette.Background) {
        Column(Modifier.fillMaxWidth()) {
            RegisterHeader("Elegir cuenta", "Cerrar cuentas", onDismiss)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).selectableGroup(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(accounts, key = { it.id }) { account ->
                    val selected = account.id == selectedId
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
                        .background(if (selected) HomePalette.NavSelection else HomePalette.Paper)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(account.id) })
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
                        Text(account.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (selected) Icon(Icons.Outlined.Check, null, tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
