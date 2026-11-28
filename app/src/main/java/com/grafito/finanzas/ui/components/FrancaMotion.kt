package com.grafito.finanzas.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Animate the incoming content while keeping only one destination active. */
@Composable
internal fun Modifier.francaPageEnter(destination: Any?, forward: Boolean = true): Modifier {
    val previous = remember { mutableStateOf(destination) }
    val progress = remember(destination) {
        Animatable(if (previous.value == destination || !ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    }
    LaunchedEffect(progress) {
        previous.value = destination
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val remaining = 1f - progress.value
        alpha = 1f - 0.28f * remaining
        translationY = (if (forward) 8.dp else (-8).dp).toPx() * remaining
    }
}
