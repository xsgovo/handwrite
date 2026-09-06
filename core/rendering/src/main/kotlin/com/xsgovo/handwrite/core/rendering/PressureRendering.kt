package com.xsgovo.handwrite.core.rendering

import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.StrokeSample
import kotlin.math.sqrt

// 压力映射在 [0, 1] 全范围生效：最小压力细至基础宽度的 35%，满压时恰好等于基础宽度，
// 因此恒定满压输入（手指、鼠标、无压感触控笔）仍渲染为所选宽度。平方根整形让常用书写
// 力度区间保持在基础宽度附近，仅轻触明显变细。屏幕 Ink 画笔与光栅导出共用该曲线。
internal const val PRESSURE_MIN_WIDTH_FACTOR = 0.35f

internal fun BrushStyle.rendersWithPressure(): Boolean =
    brushRendersWithPressure(id, pressureSensitivity)

internal fun brushRendersWithPressure(
    brushId: BrushId,
    pressureSensitivity: PressureSensitivity,
): Boolean = brushId != BrushId.HIGHLIGHTER && pressureSensitivity != PressureSensitivity.OFF

internal fun pressureWidthMultiplier(pressure: Int): Float {
    val normalized = (pressure.toFloat() / StrokeSample.MAX_PRESSURE).coerceIn(0f, 1f)
    return PRESSURE_MIN_WIDTH_FACTOR + (1f - PRESSURE_MIN_WIDTH_FACTOR) * sqrt(normalized)
}
