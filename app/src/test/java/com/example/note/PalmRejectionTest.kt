package com.example.note

import com.note.handwrite.util.isPalmContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmRejectionTest {
    @Test
    fun largeContactIsClassifiedAsPalm() {
        assertTrue(isPalmContact(25f, 18f, 8f))
    }

    @Test
    fun elongatedLargeContactIsClassifiedAsPalm() {
        assertTrue(isPalmContact(18f, 7f, 8f))
    }

    @Test
    fun normalFingerIsNotClassifiedAsPalm() {
        assertFalse(isPalmContact(9f, 7f, 8f))
    }
}
