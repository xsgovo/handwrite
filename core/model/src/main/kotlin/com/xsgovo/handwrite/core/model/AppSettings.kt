package com.xsgovo.handwrite.core.model

data class AppSettings(
    val inputMode: InputMode = InputMode.FINGER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val imageFormat: ImageFormat = ImageFormat.AUTO,
    val exportResolution: ExportResolution = ExportResolution.STANDARD,
    val compressionQuality: CompressionQuality = CompressionQuality.BALANCED,
    val backBehavior: BackBehavior = BackBehavior.EXIT_APP,
    val sideButtonAction: SideButtonAction = SideButtonAction.TEMPORARY_ERASER,
    val pressureSensitivity: PressureSensitivity = PressureSensitivity.STANDARD,
    val activeBrushId: BrushId = BrushId.MONOLINE,
    val colorSlots: List<Int> = DEFAULT_COLOR_SLOTS,
    val activeColorSlot: Int = 0,
    val widthSteps: List<Int> = DEFAULT_WIDTH_STEPS,
    val activeWidthSlot: Int = 1,
    val defaultPageTemplate: PageTemplate = PageTemplate.LEGACY_PORTRAIT,
    val defaultBackground: PageBackground = PageBackground.Solid(),
) {
    init {
        require(colorSlots.isNotEmpty())
        require(activeColorSlot in colorSlots.indices)
        require(widthSteps.size == DEFAULT_WIDTH_STEPS.size)
        require(widthSteps.all { it in 1..100 })
        require(activeWidthSlot in widthSteps.indices)
        require(defaultBackground !is PageBackground.Asset)
    }

    val activeWidthStep: Int get() = widthSteps[activeWidthSlot]

    companion object {
        val DEFAULT_COLOR_SLOTS = listOf(0xFF1F1F1F.toInt(), 0xFFE53935.toInt(), 0xFF2E7D32.toInt())
        val DEFAULT_WIDTH_STEPS = listOf(25, 50, 75)
    }
}

enum class InputMode {
    FINGER,
    STYLUS,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ImageFormat {
    AUTO,
    PNG,
    WEBP,
    JPEG,
}

enum class ExportResolution {
    SMALL,
    STANDARD,
    HIGH,
}

enum class CompressionQuality {
    LOW,
    BALANCED,
    HIGH,
}

enum class BackBehavior {
    EXIT_APP,
    OPEN_LIBRARY,
}

enum class SideButtonAction {
    TEMPORARY_ERASER,
    TOGGLE_ERASER,
    UNDO,
}
