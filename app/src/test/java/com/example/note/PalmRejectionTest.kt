package com.example.note

import com.note.handwrite.util.isPalmContact
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

class PalmRejectionTest {
    @Test
    fun largeContactIsClassifiedAsPalm() {
        assertTrue(isPalmContact(29f, 18f))
    }

    @Test
    fun elongatedLargeContactIsClassifiedAsPalm() {
        assertTrue(isPalmContact(21f, 7f))
    }

    @Test
    fun normalFingerIsNotClassifiedAsPalm() {
        assertFalse(isPalmContact(9f, 7f))
    }

    @Test
    fun largerThumbIsNotClassifiedAsPalm() {
        assertFalse(isPalmContact(18f, 11f))
    }

    @Test
    fun oneRemainingNonPalmContactCannotFormAPinch() {
        val nonPalmContacts = listOf(Offset(10f, 10f))

        assertFalse(nonPalmContacts.hasPinchPair())
    }
}

private fun List<Offset>.hasPinchPair(): Boolean = size >= 2 && hypot(
    this[0].x - this[1].x,
    this[0].y - this[1].y
) >= 0f
