package com.xsgovo.handwrite.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.xsgovo.handwrite.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EBDD),
    onPrimaryContainer = Color(0xFF073C2D),
    secondary = Color(0xFF4E5E58),
    secondaryContainer = Color(0xFFD8E4DE),
    onSecondaryContainer = Color(0xFF26332E),
    tertiary = Color(0xFF9B3D43),
    tertiaryContainer = Color(0xFFFFDADB),
    onTertiaryContainer = Color(0xFF5B151B),
    background = Color(0xFFF7F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4E8E4),
    outline = Color(0xFF747874),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED6BB),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFC4EBDD),
    secondary = Color(0xFFB7CBC2),
    secondaryContainer = Color(0xFF35453F),
    onSecondaryContainer = Color(0xFFD8E4DE),
    tertiary = Color(0xFFFFB2B5),
    tertiaryContainer = Color(0xFF7D292F),
    onTertiaryContainer = Color(0xFFFFDADB),
    background = Color(0xFF111412),
    surface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFF303532),
    outline = Color(0xFF8D938F),
)

@Composable
fun HandwriteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
