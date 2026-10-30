package com.salonbooking.app.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Design tokens shared with the web: warm neutral backgrounds, charcoal text, one muted accent.
private val Sage = Color(0xFF5E7A68)
private val SageLight = Color(0xFF9DB8A6)
private val DustyRose = Color(0xFFB07A7A)
private val Charcoal = Color(0xFF2B2B2B)
private val WarmWhite = Color(0xFFFAF7F2)
private val Sand = Color(0xFFE3DDD3)

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    secondary = DustyRose,
    onSecondary = Color.White,
    background = WarmWhite,
    onBackground = Charcoal,
    surface = Color.White,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFF2EDE5),
    onSurfaceVariant = Color(0xFF5C5853),
    // Containers (cards, sheets, app bars) must be set too, or Material's default lavender tones show.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFBF8),
    surfaceContainer = Color(0xFFF7F3ED),
    surfaceContainerHigh = Color(0xFFF2EDE5),
    surfaceContainerHighest = Color(0xFFECE6DC),
    surfaceDim = Color(0xFFE6E0D6),
    surfaceBright = WarmWhite,
    outline = Sand,
    outlineVariant = Color(0xFFEDE7DE),
)

private val DarkColors = darkColorScheme(
    primary = SageLight,
    onPrimary = Color(0xFF10251A),
    secondary = Color(0xFFD9A9A9),
    onSecondary = Color(0xFF2E1414),
    background = Color(0xFF161514),
    onBackground = Color(0xFFEDEAE4),
    surface = Color(0xFF1F1E1C),
    onSurface = Color(0xFFEDEAE4),
    surfaceVariant = Color(0xFF2A2826),
    onSurfaceVariant = Color(0xFFC9C3BA),
    surfaceContainerLowest = Color(0xFF110F0E),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2A2826),
    surfaceContainerHighest = Color(0xFF34312E),
    surfaceDim = Color(0xFF161514),
    surfaceBright = Color(0xFF3B3835),
    outline = Color(0xFF3A3734),
    outlineVariant = Color(0xFF2E2C29),
)

private val SalonShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * App theme. [accentHex] is a business's brand colour ("#RRGGBB"); while viewing that business
 * it replaces the primary colour (TEN-7, APP-5).
 */
@Composable
fun SalonTheme(
    accentHex: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = base.withAccent(accentHex),
        shapes = SalonShapes,
        content = content,
    )
}

private fun ColorScheme.withAccent(accentHex: String?): ColorScheme {
    val accent = BrandColor.parse(accentHex) ?: return this
    return copy(
        primary = Color(accent.toLong() or 0xFF000000L),
        onPrimary = Color(BrandColor.readableTextOn(accent).toLong() or 0xFF000000L),
    )
}
