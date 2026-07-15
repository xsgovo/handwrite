package com.example.note

import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NormalizedPoint
import com.note.handwrite.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {
    @Test
    fun normalizedPointStoresCoordinates() {
        assertEquals(NormalizedPoint(0.25f, 0.75f), NormalizedPoint(0.25f, 0.75f))
    }

    @Test
    fun enumsExposeDrawingModes() {
        assertEquals(listOf(Tool.PEN, Tool.ERASER), Tool.entries)
        assertEquals(listOf(BackgroundType.PLAIN, BackgroundType.LINED, BackgroundType.GRID), BackgroundType.entries)
    }
}
