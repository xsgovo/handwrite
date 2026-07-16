package com.note.handwrite.model

/** Fixed logical A4 portrait page used by every drawing and export. */
object NotePage {
    const val WIDTH = 1000f
    const val HEIGHT = 1414f
    const val WIDTH_MM = 210f
    const val LOGICAL_UNITS_PER_MM = WIDTH / WIDTH_MM

    const val LINE_SPACING = 40f
    const val GRID_SPACING = 20f
    const val BACKGROUND_LINE_WIDTH_MM = 0.2f
    const val ERASER_HIT_RADIUS_MM = 2.5f

    const val EXPORT_WIDTH = 2480
    const val EXPORT_HEIGHT = 3508

    fun widthForStep(step: Int): Float {
        val clampedStep = step.coerceIn(1, 100)
        val millimeters = 0.1f + (clampedStep - 1) * (2.9f / 99f)
        return millimeters * LOGICAL_UNITS_PER_MM
    }

    fun millimetersForStep(step: Int): Float = widthForStep(step) / LOGICAL_UNITS_PER_MM
}
