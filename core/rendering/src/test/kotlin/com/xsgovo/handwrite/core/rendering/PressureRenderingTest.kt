package com.xsgovo.handwrite.core.rendering

import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureRenderingTest {
    @Test
    fun onlyPenStylesWithoutPressureOffRenderWithPressure() {
        val responsive = listOf(
            BrushStyle(
                id = BrushId.MONOLINE,
                argb = 0xFF111111.toInt(),
                width = 10,
                pressureSensitivity = PressureSensitivity.STANDARD,
            ),
            BrushStyle(
                id = BrushId.PRESSURE_PEN,
                argb = 0xFF111111.toInt(),
                width = 10,
                pressureSensitivity = PressureSensitivity.LOW,
            ),
        )
        val flat = listOf(
            BrushStyle(
                id = BrushId.MONOLINE,
                argb = 0xFF111111.toInt(),
                width = 10,
                pressureSensitivity = PressureSensitivity.OFF,
            ),
            BrushStyle(
                id = BrushId.HIGHLIGHTER,
                argb = 0xFF111111.toInt(),
                width = 10,
                pressureSensitivity = PressureSensitivity.HIGH,
            ),
        )

        responsive.forEach { assertTrue(it.rendersWithPressure()) }
        flat.forEach { assertFalse(it.rendersWithPressure()) }
    }

    @Test
    fun pressureWidthMultiplierFollowsTheSharedSquareRootCurve() {
        assertEquals(0.35f, pressureWidthMultiplier(0), 0.0001f)
        assertEquals(0.675f, pressureWidthMultiplier((StrokeSample.MAX_PRESSURE * 0.25f).toInt()), 0.001f)
        assertEquals(0.8375f, pressureWidthMultiplier((StrokeSample.MAX_PRESSURE * 0.5625f).toInt()), 0.001f)
        assertEquals(1f, pressureWidthMultiplier(StrokeSample.MAX_PRESSURE), 0.0001f)
    }

    @Test
    fun pressureWidthMultiplierGrowsMonotonicallyWithPressure() {
        var previous = 0f
        (0..StrokeSample.MAX_PRESSURE step 1_024).forEach { pressure ->
            val current = pressureWidthMultiplier(pressure)
            assertTrue(previous <= current)
            previous = current
        }
    }
}
