package com.grafito.finanzas.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object HomePalette {
    val Background = Color(0xFFF3F6FC)
    val Paper = Color.White
    val Ink = Color(0xFF101829)
    val Muted = Color(0xFF45516A)
    val Navy = Color(0xFF101D53)
    val GradientEnd = Color(0xFF173FCF)
    val Blue = Color(0xFF234DF3)
    val Line = Color(0xFFCEDFFD)
    val IconBackground = Color(0xFFE5EFFC)
    val NavSelection = Color(0xFFEAF0FF)
    val NavIcon = Color(0xFFD1E3FF)
    val Income = Color(0xFF006849)
    val Expense = Color(0xFFBA1537)

    val scheme = lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        background = Background,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        onSurfaceVariant = Muted,
        outline = Muted,
        outlineVariant = Line,
        inverseSurface = Navy,
        inverseOnSurface = Color.White,
        inversePrimary = NavIcon,
        error = Expense
    )

    val panelScheme = scheme.copy(
        onBackground = Navy,
        onSurface = Navy,
        primaryContainer = NavSelection,
        onPrimaryContainer = Navy,
        secondary = Muted,
        onSecondary = Paper,
        secondaryContainer = NavSelection,
        onSecondaryContainer = Navy,
        surfaceVariant = NavSelection,
        surfaceContainer = Background,
        surfaceContainerHigh = Background,
        surfaceTint = Blue
    )
}
