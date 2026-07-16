package com.example.note

import com.note.handwrite.model.NotePage
import org.junit.Assert.assertEquals
import org.junit.Test

class NotePageTest {
    @Test
    fun widthStepsCoverTheAgreedMillimeterRangeLinearly() {
        assertEquals(0.1f, NotePage.millimetersForStep(1), 0.001f)
        assertEquals(1.535f, NotePage.millimetersForStep(50), 0.001f)
        assertEquals(3f, NotePage.millimetersForStep(100), 0.001f)
    }
}
