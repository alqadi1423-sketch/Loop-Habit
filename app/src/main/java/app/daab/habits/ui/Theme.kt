package app.daab.habits.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Light = lightColorScheme(
    primary = Color(0xFF0F6E56),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F5EE),
    onPrimaryContainer = Color(0xFF04342C),
    background = Color(0xFFF7F6F2),
    surface = Color(0xFFF7F6F2),
    surfaceVariant = Color(0xFFE9E7DF),
    onSurfaceVariant = Color(0xFF5F5E5A)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5DCAA5),
    onPrimary = Color(0xFF04342C),
    primaryContainer = Color(0xFF085041),
    onPrimaryContainer = Color(0xFFE1F5EE),
    background = Color(0xFF161615),
    surface = Color(0xFF161615),
    surfaceVariant = Color(0xFF2C2C2A),
    onSurfaceVariant = Color(0xFFB4B2A9)
)

@Composable
fun DaabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
