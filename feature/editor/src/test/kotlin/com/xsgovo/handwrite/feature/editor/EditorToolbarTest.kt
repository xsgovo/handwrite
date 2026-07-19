package com.xsgovo.handwrite.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorToolbarTest {
    @Test
    fun customWidthSelectsNearestPresetWithoutReplacingItsValue() {
        assertEquals(25, selectedBrushWidthPreset(1))
        assertEquals(25, selectedBrushWidthPreset(37))
        assertEquals(50, selectedBrushWidthPreset(38))
        assertEquals(50, selectedBrushWidthPreset(62))
        assertEquals(75, selectedBrushWidthPreset(63))
        assertEquals(75, selectedBrushWidthPreset(73))
        assertEquals(75, selectedBrushWidthPreset(100))
    }
}
