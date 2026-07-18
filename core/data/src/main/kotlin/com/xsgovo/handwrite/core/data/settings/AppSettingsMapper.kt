package com.xsgovo.handwrite.core.data.settings

import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ExportResolution
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageTemplate
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.ThemeMode

internal fun AppSettingsPayload.toDomain(): AppSettings {
    val defaults = AppSettings()
    val colors = colorSlotsList.takeIf { it.isNotEmpty() } ?: defaults.colorSlots
    val activeSlot = activeColorSlot.coerceIn(colors.indices)
    return AppSettings(
        inputMode = if (inputMode == com.xsgovo.handwrite.core.data.settings.InputMode.INPUT_MODE_STYLUS) InputMode.STYLUS else InputMode.FINGER,
        themeMode = when (themeMode) {
            com.xsgovo.handwrite.core.data.settings.ThemeMode.THEME_MODE_LIGHT -> ThemeMode.LIGHT
            com.xsgovo.handwrite.core.data.settings.ThemeMode.THEME_MODE_DARK -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        },
        imageFormat = when (imageFormat) {
            com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_PNG -> ImageFormat.PNG
            com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_WEBP -> ImageFormat.WEBP
            com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_JPEG -> ImageFormat.JPEG
            else -> ImageFormat.AUTO
        },
        exportResolution = when (exportResolution) {
            com.xsgovo.handwrite.core.data.settings.ExportResolution.EXPORT_RESOLUTION_SMALL -> ExportResolution.SMALL
            com.xsgovo.handwrite.core.data.settings.ExportResolution.EXPORT_RESOLUTION_HIGH -> ExportResolution.HIGH
            else -> ExportResolution.STANDARD
        },
        compressionQuality = when (compressionQuality) {
            com.xsgovo.handwrite.core.data.settings.CompressionQuality.COMPRESSION_QUALITY_LOW -> CompressionQuality.LOW
            com.xsgovo.handwrite.core.data.settings.CompressionQuality.COMPRESSION_QUALITY_HIGH -> CompressionQuality.HIGH
            else -> CompressionQuality.BALANCED
        },
        backBehavior = if (backBehavior == com.xsgovo.handwrite.core.data.settings.BackBehavior.BACK_BEHAVIOR_OPEN_LIBRARY) BackBehavior.OPEN_LIBRARY else BackBehavior.EXIT_APP,
        sideButtonAction = when (sideButtonAction) {
            com.xsgovo.handwrite.core.data.settings.SideButtonAction.SIDE_BUTTON_ACTION_TOGGLE_ERASER -> SideButtonAction.TOGGLE_ERASER
            com.xsgovo.handwrite.core.data.settings.SideButtonAction.SIDE_BUTTON_ACTION_UNDO -> SideButtonAction.UNDO
            else -> SideButtonAction.TEMPORARY_ERASER
        },
        pressureSensitivity = when (pressureSensitivity) {
            PressureSensitivitySetting.PRESSURE_SETTING_LOW -> PressureSensitivity.LOW
            PressureSensitivitySetting.PRESSURE_SETTING_HIGH -> PressureSensitivity.HIGH
            else -> PressureSensitivity.STANDARD
        },
        activeBrushId = BrushId(activeBrushId.ifBlank { defaults.activeBrushId.value }),
        colorSlots = colors,
        activeColorSlot = activeSlot,
        widthStep = widthStep.coerceIn(1, 100).takeIf { widthStep != 0 } ?: defaults.widthStep,
        defaultPageTemplate = when (defaultPageTemplate) {
            PageTemplateSetting.PAGE_TEMPLATE_THREE_BY_FOUR -> PageTemplate.THREE_BY_FOUR
            PageTemplateSetting.PAGE_TEMPLATE_FOUR_BY_THREE -> PageTemplate.FOUR_BY_THREE
            PageTemplateSetting.PAGE_TEMPLATE_SQUARE -> PageTemplate.SQUARE
            PageTemplateSetting.PAGE_TEMPLATE_NINE_BY_SIXTEEN -> PageTemplate.NINE_BY_SIXTEEN
            else -> PageTemplate.LEGACY_PORTRAIT
        },
        defaultBackground = when (defaultBackground) {
            DefaultBackgroundSetting.DEFAULT_BACKGROUND_TRANSPARENT -> PageBackground.Transparent
            DefaultBackgroundSetting.DEFAULT_BACKGROUND_LINED -> PageBackground.Pattern(PatternType.LINED)
            DefaultBackgroundSetting.DEFAULT_BACKGROUND_GRID -> PageBackground.Pattern(PatternType.GRID)
            else -> PageBackground.Solid()
        },
    )
}

internal fun AppSettings.toProto(): AppSettingsPayload = AppSettingsPayload.newBuilder()
    .setInputMode(if (inputMode == InputMode.STYLUS) com.xsgovo.handwrite.core.data.settings.InputMode.INPUT_MODE_STYLUS else com.xsgovo.handwrite.core.data.settings.InputMode.INPUT_MODE_FINGER)
    .setThemeMode(
        when (themeMode) {
            ThemeMode.SYSTEM -> com.xsgovo.handwrite.core.data.settings.ThemeMode.THEME_MODE_SYSTEM
            ThemeMode.LIGHT -> com.xsgovo.handwrite.core.data.settings.ThemeMode.THEME_MODE_LIGHT
            ThemeMode.DARK -> com.xsgovo.handwrite.core.data.settings.ThemeMode.THEME_MODE_DARK
        },
    )
    .setImageFormat(
        when (imageFormat) {
            ImageFormat.AUTO -> com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_AUTO
            ImageFormat.PNG -> com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_PNG
            ImageFormat.WEBP -> com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_WEBP
            ImageFormat.JPEG -> com.xsgovo.handwrite.core.data.settings.ImageFormat.IMAGE_FORMAT_JPEG
        },
    )
    .setExportResolution(
        when (exportResolution) {
            ExportResolution.SMALL -> com.xsgovo.handwrite.core.data.settings.ExportResolution.EXPORT_RESOLUTION_SMALL
            ExportResolution.STANDARD -> com.xsgovo.handwrite.core.data.settings.ExportResolution.EXPORT_RESOLUTION_STANDARD
            ExportResolution.HIGH -> com.xsgovo.handwrite.core.data.settings.ExportResolution.EXPORT_RESOLUTION_HIGH
        },
    )
    .setCompressionQuality(
        when (compressionQuality) {
            CompressionQuality.LOW -> com.xsgovo.handwrite.core.data.settings.CompressionQuality.COMPRESSION_QUALITY_LOW
            CompressionQuality.BALANCED -> com.xsgovo.handwrite.core.data.settings.CompressionQuality.COMPRESSION_QUALITY_BALANCED
            CompressionQuality.HIGH -> com.xsgovo.handwrite.core.data.settings.CompressionQuality.COMPRESSION_QUALITY_HIGH
        },
    )
    .setBackBehavior(if (backBehavior == BackBehavior.OPEN_LIBRARY) com.xsgovo.handwrite.core.data.settings.BackBehavior.BACK_BEHAVIOR_OPEN_LIBRARY else com.xsgovo.handwrite.core.data.settings.BackBehavior.BACK_BEHAVIOR_EXIT_APP)
    .setSideButtonAction(
        when (sideButtonAction) {
            SideButtonAction.TEMPORARY_ERASER -> com.xsgovo.handwrite.core.data.settings.SideButtonAction.SIDE_BUTTON_ACTION_TEMPORARY_ERASER
            SideButtonAction.TOGGLE_ERASER -> com.xsgovo.handwrite.core.data.settings.SideButtonAction.SIDE_BUTTON_ACTION_TOGGLE_ERASER
            SideButtonAction.UNDO -> com.xsgovo.handwrite.core.data.settings.SideButtonAction.SIDE_BUTTON_ACTION_UNDO
        },
    )
    .setPressureSensitivity(
        when (pressureSensitivity) {
            PressureSensitivity.LOW -> PressureSensitivitySetting.PRESSURE_SETTING_LOW
            PressureSensitivity.STANDARD -> PressureSensitivitySetting.PRESSURE_SETTING_STANDARD
            PressureSensitivity.HIGH -> PressureSensitivitySetting.PRESSURE_SETTING_HIGH
        },
    )
    .setActiveBrushId(activeBrushId.value)
    .addAllColorSlots(colorSlots)
    .setActiveColorSlot(activeColorSlot)
    .setWidthStep(widthStep)
    .setDefaultPageTemplate(
        when (defaultPageTemplate) {
            PageTemplate.LEGACY_PORTRAIT -> PageTemplateSetting.PAGE_TEMPLATE_LEGACY_PORTRAIT
            PageTemplate.THREE_BY_FOUR -> PageTemplateSetting.PAGE_TEMPLATE_THREE_BY_FOUR
            PageTemplate.FOUR_BY_THREE -> PageTemplateSetting.PAGE_TEMPLATE_FOUR_BY_THREE
            PageTemplate.SQUARE -> PageTemplateSetting.PAGE_TEMPLATE_SQUARE
            PageTemplate.NINE_BY_SIXTEEN -> PageTemplateSetting.PAGE_TEMPLATE_NINE_BY_SIXTEEN
        },
    )
    .setDefaultBackground(
        when (val background = defaultBackground) {
            PageBackground.Transparent -> DefaultBackgroundSetting.DEFAULT_BACKGROUND_TRANSPARENT
            is PageBackground.Pattern -> if (background.type == PatternType.GRID) DefaultBackgroundSetting.DEFAULT_BACKGROUND_GRID else DefaultBackgroundSetting.DEFAULT_BACKGROUND_LINED
            else -> DefaultBackgroundSetting.DEFAULT_BACKGROUND_WHITE
        },
    )
    .build()
