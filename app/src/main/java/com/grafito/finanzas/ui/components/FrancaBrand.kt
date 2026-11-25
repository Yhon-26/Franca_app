package com.grafito.finanzas.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.grafito.finanzas.R
import com.grafito.finanzas.ui.theme.HomePalette

@Composable
internal fun FrancaBrand() {
    val name = stringResource(R.string.app_name)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = name },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_franca_light),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Image(
            painter = painterResource(R.drawable.franca_wordmark),
            contentDescription = null,
            modifier = Modifier.width(116.dp).aspectRatio(3.398f),
            colorFilter = ColorFilter.tint(HomePalette.GradientEnd)
        )
    }
}
