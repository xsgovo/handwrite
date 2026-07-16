package com.example.note

import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.util.CanvasTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTransformTest {
    @Test
    fun portraitContentRotatesAndFitsLandscapeViewport() {
        val transform = CanvasTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            targetWidth = 400f,
            targetHeight = 300f
        )

        assertEquals(2f, transform.scale, 0.001f)
        assertEquals(CanvasPoint(400f, 50f), transform.map(CanvasPoint(0f, 0f)))
        assertEquals(CanvasPoint(0f, 50f), transform.map(CanvasPoint(0f, 200f)))
    }

    @Test
    fun inverseMappingRestoresLogicalPoint() {
        val transform = CanvasTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            targetWidth = 400f,
            targetHeight = 300f
        )
        val source = CanvasPoint(25f, 80f)

        val restored = transform.inverse(transform.map(source))

        assertEquals(source.x, restored.x, 0.001f)
        assertEquals(source.y, restored.y, 0.001f)
    }
}
