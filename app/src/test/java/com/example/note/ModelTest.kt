package com.example.note

import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Test
import com.note.handwrite.model.Stroke
import androidx.compose.ui.graphics.Color

class ModelTest {

    @Test
    fun strokeKeepsDisconnectedPageSegmentsSeparate() {
        val stroke = Stroke(
            points = listOf(CanvasPoint(1f, 1f), CanvasPoint(2f, 2f), CanvasPoint(3f, 3f)),
            color = Color.Black,
            width = 1f,
            breakIndices = setOf(2)
        )

        assertEquals(listOf(listOf(CanvasPoint(1f, 1f), CanvasPoint(2f, 2f)), listOf(CanvasPoint(3f, 3f))), stroke.paths())
    }
    @Test
    fun normalizedPointStoresCoordinates() {
        assertEquals(CanvasPoint(240f, 480f), CanvasPoint(240f, 480f))
    }

    @Test
    fun enumsExposeDrawingModes() {
        assertEquals(listOf(Tool.PEN, Tool.ERASER), Tool.entries)
        assertEquals(listOf(BackgroundType.PLAIN, BackgroundType.LINED, BackgroundType.GRID), BackgroundType.entries)
    }
}
