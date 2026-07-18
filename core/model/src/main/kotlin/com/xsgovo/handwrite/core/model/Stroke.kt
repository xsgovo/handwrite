package com.xsgovo.handwrite.core.model

data class StrokeSample(
    val point: LogicalPoint,
    val pressure: Int = MAX_PRESSURE,
    val elapsedMillis: Int = 0,
    val tiltX: Int? = null,
    val tiltY: Int? = null,
) {
    init {
        require(pressure in 0..MAX_PRESSURE)
        require(elapsedMillis >= 0)
        require(tiltX == null || tiltX in MIN_TILT..MAX_TILT)
        require(tiltY == null || tiltY in MIN_TILT..MAX_TILT)
    }

    companion object {
        const val MAX_PRESSURE = 65_535
        const val MIN_TILT = -32_767
        const val MAX_TILT = 32_767
    }
}

data class BrushStyle(
    val id: BrushId = BrushId.MONOLINE,
    val argb: Int,
    val width: Int,
    val blendMode: BrushBlendMode = BrushBlendMode.SOURCE_OVER,
    val pressureSensitivity: PressureSensitivity = PressureSensitivity.STANDARD,
) {
    init {
        require(width > 0)
    }
}

enum class BrushBlendMode {
    SOURCE_OVER,
    HIGHLIGHT,
}

enum class PressureSensitivity {
    LOW,
    STANDARD,
    HIGH,
}

interface PageElement {
    val id: ElementId
    val pageId: PageId
    val orderKey: Long
}

data class StrokeElement(
    override val id: ElementId,
    override val pageId: PageId,
    override val orderKey: Long,
    val style: BrushStyle,
    val samples: List<StrokeSample>,
) : PageElement {
    init {
        require(samples.isNotEmpty())
    }
}
