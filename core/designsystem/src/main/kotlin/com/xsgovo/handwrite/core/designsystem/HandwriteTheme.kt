package com.xsgovo.handwrite.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.ThemeMode

// Material 3 Expressive palette: high-chroma spring-green primary, warm amber
// tertiary, and quiet green-tinted neutrals so handwriting ink stays dominant.
private val LightColors = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF89F8CF),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4D635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DD),
    onSecondaryContainer = Color(0xFF0A1F19),
    tertiary = Color(0xFF9C4A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF331B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF404944),
    surfaceDim = Color(0xFFD5DBD6),
    surfaceBright = Color(0xFFF5FAF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F0),
    surfaceContainer = Color(0xFFE9EEEA),
    surfaceContainerHigh = Color(0xFFE3E8E4),
    surfaceContainerHighest = Color(0xFFDEE3DF),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C3),
    inverseSurface = Color(0xFF2B322F),
    inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Color(0xFF63DBB1),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF63DBB1),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFF89F8CF),
    secondary = Color(0xFFB4CCC1),
    onSecondary = Color(0xFF1F352E),
    secondaryContainer = Color(0xFF354B44),
    onSecondaryContainer = Color(0xFFCFE9DD),
    tertiary = Color(0xFFFFB86F),
    onTertiary = Color(0xFF512400),
    tertiaryContainer = Color(0xFF723600),
    onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDEE3DF),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDEE3DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    surfaceDim = Color(0xFF0E1512),
    surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404944),
    inverseSurface = Color(0xFFDEE3DF),
    inverseOnSurface = Color(0xFF2B322F),
    inversePrimary = Color(0xFF006C51),
    scrim = Color(0xFF000000),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Colors for canvas-adjacent chrome that has no role in the Material scheme.
// The desk sits behind the page; pattern lines and page fills are document
// content and stay theme-independent so exports match the canvas.
data class HandwriteColors(
    val canvasDesk: Color,
)

private val LightHandwriteColors = HandwriteColors(canvasDesk = Color(0xFFDFE6E1))
private val DarkHandwriteColors = HandwriteColors(canvasDesk = Color(0xFF070C0A))

val LocalHandwriteColors = staticCompositionLocalOf { LightHandwriteColors }

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
    val handwriteColors = if (darkTheme) DarkHandwriteColors else LightHandwriteColors
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ExpressiveShapes,
    ) {
        CompositionLocalProvider(
            LocalHandwriteColors provides handwriteColors,
            content = content,
        )
    }
}
