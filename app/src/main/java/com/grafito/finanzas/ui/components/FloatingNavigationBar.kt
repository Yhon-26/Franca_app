package com.grafito.finanzas.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafito.finanzas.ui.theme.HomePalette
import kotlin.math.abs

private const val SELECTED_TAB_WEIGHT = 1.9f

data class NavigationDestination(val label: String, val icon: ImageVector)

@Composable
fun FloatingNavigationBar(
    destinations: List<NavigationDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val playSound = LocalFrancaSounds.current
    val indicatorPosition = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(180),
        label = "Indicador de pestaña"
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = CircleShape,
            color = HomePalette.Navy
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .drawBehind {
                        val tabWidth = size.width / (destinations.size - 1 + SELECTED_TAB_WEIGHT)
                        val indicatorWidth = tabWidth * SELECTED_TAB_WEIGHT
                        val start = indicatorPosition.value * tabWidth
                        val left = if (layoutDirection == LayoutDirection.Ltr) start
                        else size.width - start - indicatorWidth
                        drawRoundRect(
                            color = HomePalette.NavSelection,
                            topLeft = Offset(left, 0f),
                            size = Size(indicatorWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = index == selectedIndex
                    val indicatorBehindIcon by remember(index, selectedIndex) {
                        derivedStateOf {
                            val start = indicatorPosition.value
                            val slotStart = index + if (index > selectedIndex) SELECTED_TAB_WEIGHT - 1f else 0f
                            slotStart + 0.5f in start..(start + SELECTED_TAB_WEIGHT)
                        }
                    }
                    Row(
                        Modifier.weight(if (selected) SELECTED_TAB_WEIGHT else 1f).heightIn(min = 48.dp).clip(CircleShape)
                            .selectable(selected = selected, role = Role.Tab, onClick = {
                                if (!selected) {
                                    playSound(FrancaCue.TAP)
                                    onSelect(index)
                                }
                            })
                            .semantics { contentDescription = destination.label },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(destination.icon, contentDescription = null,
                            tint = if (indicatorBehindIcon) HomePalette.Navy else HomePalette.NavIcon,
                            modifier = Modifier.size(22.dp))
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            Text(destination.label, color = HomePalette.Navy, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp),
                                modifier = Modifier.graphicsLayer {
                                    // Reveal the label as the pill reaches it to keep the lettering legible.
                                    alpha = (1f - abs(indicatorPosition.value - index) * 4f).coerceIn(0f, 1f)
                                }.semantics { hideFromAccessibility() })
                        }
                    }
                }
            }
        }
    }
}
