package com.grafito.finanzas.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.grafito.finanzas.R
import com.grafito.finanzas.ui.theme.HomePalette
import kotlinx.coroutines.delay

internal class FirstMovementFeedback(override val message: String) : SnackbarVisuals {
    override val actionLabel = "Deshacer"
    override val withDismissAction = true
    override val duration = SnackbarDuration.Short
}

@Composable
internal fun MovementFeedbackHost(
    state: SnackbarHostState,
    showMascotInHome: Boolean,
    modifier: Modifier = Modifier
) {
    SnackbarHost(state, modifier) { data ->
        Column {
            Surface(
                modifier = Modifier.widthIn(max = 344.dp).fillMaxWidth(),
                shape = CircleShape,
                color = HomePalette.Navy,
                contentColor = HomePalette.Paper,
                shadowElevation = 3.dp
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(26.dp),
                        shape = CircleShape,
                        color = HomePalette.Income,
                        contentColor = HomePalette.Paper
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(16.dp))
                        }
                    }
                    Text(data.visuals.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    data.visuals.actionLabel?.let { label ->
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.NavIcon)
                        ) { Text(label) }
                    }
                    IconButton(onClick = data::dismiss) {
                        Icon(Icons.Outlined.Close, "Cerrar aviso", Modifier.size(18.dp))
                    }
                }
            }
            if (data.visuals is FirstMovementFeedback) {
                FirstMovementMascot(showMascotInHome, Modifier.padding(start = 16.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun FirstMovementMascot(inHome: Boolean, modifier: Modifier) {
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    var appearing by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { appearing = false }
    LaunchedEffect(Unit) {
        appearing = true
        delay(if (motionEnabled) 1_880 else 1_250)
        appearing = false
    }

    AnimatedVisibility(
        visible = inHome && appearing,
        modifier = modifier,
        enter = if (motionEnabled) fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 }
            else EnterTransition.None,
        exit = if (motionEnabled) fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 3 }
            else ExitTransition.None
    ) {
        MascotGreeting(Modifier.size(76.dp).testTag("first_movement_mascot"))
    }
}

@Composable
internal fun MascotGreeting(modifier: Modifier = Modifier) {
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        delay(220)
        tilt.animateTo(-8f, tween(130))
        tilt.animateTo(4f, tween(150))
        tilt.animateTo(0f, tween(130))
    }
    Image(
        painter = painterResource(R.drawable.franca_first_movement),
        contentDescription = null,
        modifier = modifier.graphicsLayer {
            rotationZ = tilt.value
            transformOrigin = TransformOrigin(0.5f, 0.9f)
        }
    )
}
