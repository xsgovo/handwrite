package com.example.note

import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.util.CanvasTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTransformTest {
    @Test
    fun portraitContentRotatesIntoLandscapeViewportUsingWidthFit() {
        val transform = CanvasTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            targetWidth = 400f,
            targetHeight = 300f,
            rotation = 1
        )

        assertEquals(2f, transform.scale, 0.001f)
        assertEquals(CanvasPoint(400f, 50f), transform.map(CanvasPoint(0f, 0f)))
        assertEquals(CanvasPoint(0f, 50f), transform.map(CanvasPoint(0f, 200f)))
    }

    @Test
    fun inverseMappingRestoresLogicalPointForAllRotations() {
        listOf(0, 1, 2, 3).forEach { rotation ->
            val transform = CanvasTransform(
                sourceWidth = 100f,
                sourceHeight = 200f,
                targetWidth = if (rotation % 2 == 0) 300f else 400f,
                targetHeight = if (rotation % 2 == 0) 400f else 300f,
                rotation = rotation
            )
            val source = CanvasPoint(25f, 80f)
            val restored = transform.inverse(transform.map(source))

            assertEquals(source.x, restored.x, 0.001f)
            assertEquals(source.y, restored.y, 0.001f)
        }
    }

    @Test
    fun zoomUsesWidthFitAndClampsPanIndependently() {
        val transform = CanvasTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            targetWidth = 300f,
            targetHeight = 400f,
            zoomPercent = 200f,
            panX = 10_000f,
            panY = -10_000f
        )

        assertEquals(6f, transform.scale, 0.001f)
        assertEquals(CanvasPoint(0f, -800f), transform.map(CanvasPoint(0f, 0f)))
    }

    @Test
    fun baseZoomAlwaysCentersRegardlessOfRequestedPan() {
        val transform = CanvasTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            targetWidth = 300f,
            targetHeight = 400f,
            panX = 100f,
            panY = -100f
        )

        assertEquals(CanvasPoint(0f, -100f), transform.map(CanvasPoint(0f, 0f)))
    }
}
