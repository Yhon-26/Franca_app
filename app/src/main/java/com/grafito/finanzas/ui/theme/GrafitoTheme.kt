package com.grafito.finanzas.ui.theme

import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafito.finanzas.R

object Graphite {
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF1B1D20)
    val Raised = Color(0xFF292D30)
    val Divider = Color(0xFF343A3B)
    val Outline = Color(0xFF727C7B)
    val Muted = Color(0xFFABB5B2)
    val Secondary = Color(0xFFD1D9D6)
    val Primary = Color(0xFFF2F2F5)
    val Mint = Color(0xFF89D9B1)
    val OnMint = Color(0xFF10271D)
    val Selected = Color(0xFF243C33)
    val Balance = Color(0xFF192922)
    val Income = Mint
    val Expense = Color(0xFFFF929B)
}

private val colors = darkColorScheme(
    primary = Graphite.Mint,
    onPrimary = Graphite.OnMint,
    primaryContainer = Graphite.Selected,
    onPrimaryContainer = Graphite.Primary,
    secondary = Graphite.Secondary,
    onSecondary = Graphite.Background,
    secondaryContainer = Graphite.Selected,
    onSecondaryContainer = Graphite.Mint,
    tertiary = Graphite.Mint,
    onTertiary = Graphite.OnMint,
    tertiaryContainer = Graphite.Selected,
    onTertiaryContainer = Graphite.Mint,
    background = Graphite.Background,
    onBackground = Graphite.Primary,
    surface = Graphite.Surface,
    onSurface = Graphite.Primary,
    surfaceTint = Graphite.Mint,
    surfaceVariant = Graphite.Raised,
    onSurfaceVariant = Graphite.Secondary,
    surfaceDim = Graphite.Background,
    surfaceBright = Graphite.Raised,
    surfaceContainerLowest = Graphite.Background,
    surfaceContainerLow = Graphite.Surface,
    surfaceContainer = Graphite.Surface,
    surfaceContainerHigh = Graphite.Raised,
    surfaceContainerHighest = Graphite.Raised,
    outline = Graphite.Outline,
    outlineVariant = Graphite.Divider,
    inverseSurface = Graphite.Raised,
    inverseOnSurface = Graphite.Primary,
    inversePrimary = Graphite.Mint,
    error = Graphite.Expense,
    onError = Color(0xFF3F161A),
    errorContainer = Color(0xFF49282D),
    onErrorContainer = Color(0xFFFFD8DB)
)

@OptIn(ExperimentalTextApi::class)
private val manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

private val defaults = Typography()
private val typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    displayMedium = defaults.displayMedium.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 44.sp,
        lineHeight = 50.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    displaySmall = defaults.displaySmall.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = defaults.titleLarge.copy(fontFamily = manrope, fontWeight = FontWeight.Bold),
    titleMedium = defaults.titleMedium.copy(fontFamily = manrope, fontWeight = FontWeight.Bold),
    titleSmall = defaults.titleSmall.copy(fontFamily = manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = manrope, letterSpacing = 0.sp),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = manrope, letterSpacing = 0.sp),
    bodySmall = defaults.bodySmall.copy(fontFamily = manrope, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelLarge = defaults.labelLarge.copy(fontFamily = manrope, fontWeight = FontWeight.Bold),
    labelMedium = defaults.labelMedium.copy(fontFamily = manrope, fontWeight = FontWeight.SemiBold),
    labelSmall = defaults.labelSmall.copy(fontFamily = manrope, fontWeight = FontWeight.SemiBold)
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GrafitoTheme(colorScheme: ColorScheme = colors, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
}
