package com.grafito.finanzas.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.categoryNameKey
import com.grafito.finanzas.ui.theme.Graphite
import java.text.Collator
import java.util.Locale

@Composable
internal fun CategorySelector(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    enabled: Boolean = true,
    suggestion: CategoryEntity? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = categories.find { it.id == selectedId }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun collapse() {
        expanded = false
        query = ""
        focus.clearFocus()
        keyboard?.hide()
    }

    BackHandler(enabled = expanded && enabled) { collapse() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Categoría (opcional)", style = MaterialTheme.typography.labelLarge, color = Graphite.Secondary)
        OutlinedButton(
            onClick = {
                if (expanded) collapse()
                else {
                    focus.clearFocus()
                    keyboard?.hide()
                    expanded = true
                }
            },
            enabled = enabled && categories.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                stateDescription = if (expanded) "Expandida" else "Contraída"
            },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Graphite.Primary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(current?.let(::categoryIcon) ?: Icons.Outlined.Category, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(current?.name ?: "Sin categoría", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }

        if (expanded && enabled && categories.isNotEmpty()) {
            CategoryOptions(categories, current, query, { query = it }) { id ->
                onSelect(id)
                collapse()
            }
        } else if (suggestion != null) {
            SuggestionChip(onClick = { onSelect(suggestion.id) }, enabled = enabled,
                label = { Text("Sugerida · ${suggestion.name}") },
                icon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(16.dp)) })
        }
    }
}

/** The same searchable options as CategorySelector, with their own bounded sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySelectionSheet(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    suggestion: CategoryEntity? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun close() {
        focus.clearFocus()
        keyboard?.hide()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Elegir categoría", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, "Cerrar categorías") }
            }
            if (suggestion != null && query.isBlank()) {
                SuggestionChip(onClick = { focus.clearFocus(); keyboard?.hide(); onSelect(suggestion.id) },
                    label = { Text("Sugerida · ${suggestion.name}") },
                    icon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(16.dp)) })
            }
            CategoryOptions(categories, categories.find { it.id == selectedId }, query, { query = it },
                modifier = Modifier.weight(1f), fillHeight = true) { id ->
                focus.clearFocus()
                keyboard?.hide()
                onSelect(id)
            }
        }
    }
}

@Composable
private fun CategoryOptions(
    categories: List<CategoryEntity>,
    current: CategoryEntity?,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    onSelect: (Long?) -> Unit
) {
    val search = categoryNameKey(query)
    val results = rememberCategoryMatches(categories, search, alphabetized = true)
    val listState = rememberLazyListState()
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!fillHeight) bringIntoView.bringIntoView()
    }
    LaunchedEffect(search) { listState.scrollToItem(0) }

    Surface(modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background) {
        Column((if (fillHeight) Modifier.fillMaxSize() else Modifier).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { onQueryChange(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar categoría") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, "Limpiar búsqueda") } }
                } else null,
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            if (results.isEmpty()) {
                Text("Sin resultados para esa búsqueda.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier.heightIn(max = 216.dp))
                    .selectableGroup(), state = listState) {
                    if (current != null && search.isEmpty()) {
                        item { CategoryOption("Quitar categoría", Icons.Outlined.LabelOff, false) { onSelect(null) } }
                    }
                    items(results, key = { it.id }) { category ->
                        CategoryOption(category.name, categoryIcon(category), category.id == current?.id) { onSelect(category.id) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberCategoryMatches(categories: List<CategoryEntity>, search: String, alphabetized: Boolean = false): List<CategoryEntity> {
    val indexed = remember(categories, alphabetized) {
        val ordered = if (alphabetized) {
            val ordering = Collator.getInstance(Locale.forLanguageTag("es-PE"))
            categories.sortedWith { a, b -> ordering.compare(a.name, b.name) }
        } else categories
        ordered.map { it to categoryNameKey(it.name) }
    }
    return remember(indexed, search) { indexed.filter { search in it.second }.map { it.first } }
}

@Composable
private fun CategoryOption(name: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

internal fun categoryIcon(category: CategoryEntity): ImageVector = when (categoryNameKey(category.name)) {
    "alimentacion" -> Icons.Outlined.RestaurantMenu
    "transporte" -> Icons.Outlined.DirectionsBus
    "compras" -> Icons.Outlined.ShoppingBag
    "servicios" -> Icons.Outlined.Bolt
    "salud" -> Icons.Outlined.MedicalServices
    "entretenimiento" -> Icons.Outlined.Movie
    "sueldo" -> Icons.Outlined.WorkOutline
    "ventas" -> Icons.Outlined.Storefront
    "otros ingresos" -> Icons.Outlined.Savings
    "supermercado" -> Icons.Outlined.ShoppingCart
    "vivienda y alquiler" -> Icons.Outlined.Home
    "internet y celular" -> Icons.Outlined.PhoneAndroid
    "educacion" -> Icons.Outlined.School
    "mascotas" -> Icons.Outlined.Pets
    "ropa y calzado" -> Icons.Outlined.Checkroom
    "cuidado personal" -> Icons.Outlined.Spa
    "suscripciones" -> Icons.Outlined.Subscriptions
    "deudas y cuotas" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "combustible y estacionamiento" -> Icons.Outlined.LocalGasStation
    "regalos" -> Icons.Outlined.CardGiftcard
    "viajes" -> Icons.Outlined.Luggage
    "mantenimiento del hogar" -> Icons.Outlined.HomeRepairService
    "seguros" -> Icons.Outlined.Shield
    "impuestos y tramites" -> Icons.Outlined.Description
    else -> Icons.Outlined.Category
}
