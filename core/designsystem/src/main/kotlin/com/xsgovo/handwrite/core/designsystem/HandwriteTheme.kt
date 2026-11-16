package com.xsgovo.handwrite.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 主题模式。取值含义与 core:model 的 ThemeMode 一致，但刻意定义在本模块：
 * 设计系统不依赖领域模型，领域枚举到本枚举的映射由 :app 完成。
 */
enum class HandwriteThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

// 简洁中性的手写应用色彩体系：以纯净黑白灰为主基调，仅在关键交互处保留克制的强调色，
// 避免传统 Material 3 大面积高饱和色块干扰手写内容。
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6E5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F2EE),
    onPrimaryContainer = Color(0xFF0E473A),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF2),
    onSecondaryContainer = Color(0xFF14161A),
    tertiary = Color(0xFF8A4B16),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6EBE0),
    onTertiaryContainer = Color(0xFF3E2208),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDEAEA),
    onErrorContainer = Color(0xFF5F1111),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF14161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF5A606B),
    surfaceDim = Color(0xFFE5E7EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF1F2F5),
    surfaceContainerHigh = Color(0xFFE8EAEE),
    surfaceContainerHighest = Color(0xFFDFE2E7),
    outline = Color(0xFF7B818C),
    outlineVariant = Color(0xFFE1E4EA),
    inverseSurface = Color(0xFF1E2024),
    inverseOnSurface = Color(0xFFF4F5F7),
    inversePrimary = Color(0xFF6CD4B8),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CD4B8),
    onPrimary = Color(0xFF08342A),
    primaryContainer = Color(0xFF193830),
    onPrimaryContainer = Color(0xFFC6EFE3),
    secondary = Color(0xFFA0A6B1),
    onSecondary = Color(0xFF1C1E22),
    secondaryContainer = Color(0xFF2B2C32),
    onSecondaryContainer = Color(0xFFF2F3F5),
    tertiary = Color(0xFFE6AC74),
    onTertiary = Color(0xFF432406),
    tertiaryContainer = Color(0xFF3A2817),
    onTertiaryContainer = Color(0xFFF6DFC6),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0A0A),
    errorContainer = Color(0xFF471515),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA0A4AD),
    surfaceDim = Color(0xFF0A0A0C),
    surfaceBright = Color(0xFF2C2C30),
    surfaceContainerLowest = Color(0xFF1A1A1D),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF222226),
    surfaceContainerHigh = Color(0xFF2B2B30),
    surfaceContainerHighest = Color(0xFF35353B),
    outline = Color(0xFF6E737D),
    outlineVariant = Color(0xFF2E2E34),
    inverseSurface = Color(0xFFF2F3F5),
    inverseOnSurface = Color(0xFF18181B),
    inversePrimary = Color(0xFF1B6E5B),
    scrim = Color(0xFF000000),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LightToolbarBackground = Color(0xFFFFFFFF)
private val DarkToolbarBackground = Color(0xFF000000)
private val LightCanvasDesk = Color(0xFFF1F2F5)
private val DarkCanvasDesk = Color(0xFF1C1C1E)
private val LightElevatedCardBackground = Color(0xFFFFFFFF)
private val DarkElevatedCardBackground = Color(0xFF25252A)
private val LightElevatedCardBorder = Color(0xFFDFE2E8)
private val DarkElevatedCardBorder = Color(0xFF393A42)

// 编辑器专用视觉色彩：工具栏始终保持纯白/纯黑，画布桌面保持灰白/灰黑，
// 使书写纸面成为绝对视觉焦点，不被系统或主题色彩干扰。
data class HandwriteColors(
    val canvasDesk: Color,
    val toolbarBackground: Color,
    val elevatedCardBackground: Color = LightElevatedCardBackground,
    val elevatedCardBorder: Color = LightElevatedCardBorder,
)

val LocalHandwriteColors = staticCompositionLocalOf {
    HandwriteColors(
        canvasDesk = LightCanvasDesk,
        toolbarBackground = LightToolbarBackground,
        elevatedCardBackground = LightElevatedCardBackground,
        elevatedCardBorder = LightElevatedCardBorder,
    )
}

@Composable
fun HandwriteTheme(
    themeMode: HandwriteThemeMode = HandwriteThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        HandwriteThemeMode.SYSTEM -> isSystemInDarkTheme()
        HandwriteThemeMode.LIGHT -> false
        HandwriteThemeMode.DARK -> true
    }
    // minSdk 31，壁纸动态取色（Material You）在全部支持设备上可用。
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val handwriteColors = HandwriteColors(
        canvasDesk = if (darkTheme) DarkCanvasDesk else LightCanvasDesk,
        toolbarBackground = if (darkTheme) DarkToolbarBackground else LightToolbarBackground,
        elevatedCardBackground = if (darkTheme) DarkElevatedCardBackground else LightElevatedCardBackground,
        elevatedCardBorder = if (darkTheme) DarkElevatedCardBorder else LightElevatedCardBorder,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
    ) {
        CompositionLocalProvider(
            LocalHandwriteColors provides handwriteColors,
            content = content,
        )
    }
}
