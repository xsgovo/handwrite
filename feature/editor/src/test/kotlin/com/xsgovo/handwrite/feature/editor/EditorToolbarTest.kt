package com.xsgovo.handwrite.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolbarTest {
    @Test
    fun iconSizeTracksTheFullWidthRange() {
        assertEquals(4f, brushWidthIconSizeDp(1), 0f)
        assertEquals(24f, brushWidthIconSizeDp(100), 0.001f)
        assertTrue(brushWidthIconSizeDp(25) < brushWidthIconSizeDp(50))
        assertTrue(brushWidthIconSizeDp(50) < brushWidthIconSizeDp(75))
    }
}
