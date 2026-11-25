package com.grafito.finanzas.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.data.CategoryEntity
import com.grafito.finanzas.data.categoryNameKey
import com.grafito.finanzas.ui.components.categoryIcon
import com.grafito.finanzas.ui.components.rememberCategoryMatches
import com.grafito.finanzas.ui.theme.HomePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegisterCategorySheet(
    categories: List<CategoryEntity>, selectedId: Long?, registrationContext: String,
    onSelect: (Long?) -> Unit, onDismiss: () -> Unit, suggestion: CategoryEntity? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = categoryNameKey(query)
    val results = rememberCategoryMatches(categories, search)
    val listState = rememberLazyListState()
    LaunchedEffect(search) { listState.scrollToItem(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HomePalette.Background, scrimColor = Color.Transparent, dragHandle = { RegisterSheetHandle() }) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        fun finish(action: () -> Unit) {
            focus.clearFocus()
            keyboard?.hide()
            action()
        }

        Column(Modifier.fillMaxWidth().fillMaxHeight(if (keyboardOpen) 1f else 0.76f).imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { finish(onDismiss) }) {
                    Box(Modifier.size(40.dp).background(HomePalette.Paper, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a Registrar", tint = HomePalette.Navy,
                            modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Elegir categoría", Modifier.semantics { heading() }, color = HomePalette.Navy,
                        style = MaterialTheme.typography.titleLarge)
                    Text(registrationContext, color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(value = query, onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar categoría" },
                placeholder = { Text("Buscar categoría") }, leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = HomePalette.Blue, modifier = Modifier.size(20.dp))
                }, trailingIcon = if (query.isEmpty()) null else {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpiar búsqueda") } }
                }, shape = MaterialTheme.shapes.medium, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = HomePalette.Paper,
                    unfocusedContainerColor = HomePalette.Paper, unfocusedBorderColor = HomePalette.Line))
            Text("Categorías", Modifier.semantics { heading() }, color = HomePalette.Muted, style = MaterialTheme.typography.labelMedium)
            if (suggestion != null && search.isEmpty()) {
                SuggestionChip(onClick = { finish { onSelect(suggestion.id) } },
                    label = { Text("Sugerida · ${suggestion.name}") }, icon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(16.dp)) })
            }
            if (results.isEmpty() && search.isNotEmpty()) {
                Text("Sin resultados para esa búsqueda.", color = HomePalette.Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Surface(Modifier.fillMaxWidth().weight(1f, fill = false), shape = MaterialTheme.shapes.medium,
                    color = HomePalette.Paper, border = BorderStroke(1.dp, HomePalette.Line)) {
                    Column(Modifier.selectableGroup()) {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = listState) {
                            itemsIndexed(results, key = { _, category -> category.id }) { index, category ->
                                if (index > 0) HorizontalDivider(color = HomePalette.Line)
                                RegisterCategoryRow(category.name, categoryIcon(category), category.id == selectedId) {
                                    finish { onSelect(category.id) }
                                }
                            }
                        }
                        if (search.isEmpty()) {
                            if (results.isNotEmpty()) HorizontalDivider(color = HomePalette.Line)
                            RegisterCategoryRow("Sin categoría", Icons.Outlined.RemoveCircleOutline, selectedId == null, highlight = false) {
                                finish { onSelect(null) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterCategoryRow(name: String, icon: ImageVector, selected: Boolean, highlight: Boolean = true, onClick: () -> Unit) {
    val marked = selected && highlight
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
        .background(if (marked) HomePalette.NavSelection else HomePalette.Paper)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(HomePalette.NavSelection, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = HomePalette.Blue, modifier = Modifier.size(18.dp))
        }
        Text(name, Modifier.weight(1f), color = HomePalette.Navy, style = MaterialTheme.typography.titleSmall)
        Icon(if (marked) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null, tint = if (marked) HomePalette.Blue else HomePalette.Muted, modifier = Modifier.size(18.dp))
    }
}
