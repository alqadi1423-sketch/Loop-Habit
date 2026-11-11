package app.daab.habits.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import app.daab.habits.R
import app.daab.habits.logic.Settings

private val Light = lightColorScheme(
    primary = Color(0xFF0F5C55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEDEA),
    onPrimaryContainer = Color(0xFF0B3D38),
    secondaryContainer = Color(0xFFDCEDEA),
    onSecondaryContainer = Color(0xFF0B3D38),
    background = Color(0xFFF1F4F3),
    onBackground = Color(0xFF10262B),
    surface = Color(0xFFF1F4F3),
    onSurface = Color(0xFF10262B),
    surfaceVariant = Color(0xFFE3E9E7),
    onSurfaceVariant = Color(0xFF52646A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE3E9E7),
    inverseSurface = Color(0xFF10262B),
    inverseOnSurface = Color(0xFFFFFFFF),
    outline = Color(0xFF8A9A9C),
    outlineVariant = Color(0xFFDDE4E2),
    error = Color(0xFFA8322B)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5CC4B6),
    onPrimary = Color(0xFF06302B),
    primaryContainer = Color(0xFF14463F),
    onPrimaryContainer = Color(0xFFCDEDE7),
    secondaryContainer = Color(0xFF14463F),
    onSecondaryContainer = Color(0xFFCDEDE7),
    background = Color(0xFF121A1C),
    onBackground = Color(0xFFE8EEED),
    surface = Color(0xFF121A1C),
    onSurface = Color(0xFFE8EEED),
    surfaceVariant = Color(0xFF263438),
    onSurfaceVariant = Color(0xFFA3B4B2),
    surfaceContainer = Color(0xFF1B2A2E),
    surfaceContainerLow = Color(0xFF1B2A2E),
    surfaceContainerHigh = Color(0xFF223237),
    surfaceContainerHighest = Color(0xFF2A3B40),
    inverseSurface = Color(0xFFE8EEED),
    inverseOnSurface = Color(0xFF10262B),
    outline = Color(0xFF6F8280),
    outlineVariant = Color(0xFF2C3C40),
    error = Color(0xFFF2A39C)
)

private val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold)
)

private fun typography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = Plex),
        displayMedium = d.displayMedium.copy(fontFamily = Plex),
        displaySmall = d.displaySmall.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        headlineLarge = d.headlineLarge.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        headlineMedium = d.headlineMedium.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        headlineSmall = d.headlineSmall.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        titleLarge = d.titleLarge.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontFamily = Plex, fontWeight = FontWeight.Medium),
        titleSmall = d.titleSmall.copy(fontFamily = Plex, fontWeight = FontWeight.Medium),
        bodyLarge = d.bodyLarge.copy(fontFamily = Plex),
        bodyMedium = d.bodyMedium.copy(fontFamily = Plex),
        bodySmall = d.bodySmall.copy(fontFamily = Plex),
        labelLarge = d.labelLarge.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        labelMedium = d.labelMedium.copy(fontFamily = Plex, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontFamily = Plex, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun isDark(): Boolean = when (Settings.theme) {
    Settings.THEME_LIGHT -> false
    Settings.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

/** A habit color adjusted so it stays readable as text on the current background. */
@Composable
fun habitTint(color: Int): Color {
    val c = Color(color)
    return if (isDark()) Color(
        red = c.red + (1f - c.red) * 0.45f,
        green = c.green + (1f - c.green) * 0.45f,
        blue = c.blue + (1f - c.blue) * 0.45f
    ) else c
}

@Composable
fun DaabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark()) Dark else Light, typography = typography()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
