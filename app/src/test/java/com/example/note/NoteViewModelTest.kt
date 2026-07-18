package com.example.note

import androidx.compose.ui.graphics.Color
import com.note.handwrite.model.DefaultColorSlots
import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.model.Stroke
import com.note.handwrite.viewmodel.NoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteViewModelTest {
    private fun stroke(x: Float) = Stroke(
        points = listOf(CanvasPoint(x, 0.5f)),
        color = Color.Black,
        width = 8f
    )

    @Test
    fun undoRemovesTheLastAddedStroke() {
        val viewModel = NoteViewModel()
        val first = stroke(0.1f)
        val second = stroke(0.2f)

        viewModel.addStroke(first)
        viewModel.addStroke(second)
        viewModel.undo()

        assertEquals(listOf(first), viewModel.strokes.value)
    }

    @Test
    fun eraseUndoRestoresAllStrokesInOriginalOrder() {
        val viewModel = NoteViewModel()
        val first = stroke(0.1f)
        val second = stroke(0.2f)
        val third = stroke(0.3f)

        viewModel.addStroke(first)
        viewModel.addStroke(second)
        viewModel.addStroke(third)
        viewModel.beginErase()
        viewModel.eraseStrokes(listOf(second))
        viewModel.eraseStrokes(listOf(third))
        viewModel.endErase()

        assertEquals(listOf(first), viewModel.strokes.value)
        viewModel.undo()
        assertEquals(listOf(first, second, third), viewModel.strokes.value)
    }

    @Test
    fun clearRemovesUndoHistory() {
        val viewModel = NoteViewModel()
        viewModel.addStroke(stroke(0.1f))
        viewModel.clearAll()
        viewModel.undo()

        assertTrue(viewModel.strokes.value.isEmpty())
        assertEquals(false, viewModel.canUndo.value)
    }

    @Test
    fun selectingColorSlotUpdatesTheCurrentPenColor() {
        val viewModel = NoteViewModel()

        viewModel.selectColorSlot(1)

        assertEquals(1, viewModel.activeColorSlot.value)
        assertEquals(DefaultColorSlots[1], viewModel.currentColor.value)
    }

    @Test
    fun updatingActiveColorOnlyChangesTheActiveSlot() {
        val viewModel = NoteViewModel()
        val customColor = Color(0xFF123456)

        viewModel.selectColorSlot(2)
        viewModel.updateActiveColor(customColor)

        assertEquals(customColor, viewModel.currentColor.value)
        assertEquals(customColor, viewModel.colorSlots.value[2])
        assertEquals(DefaultColorSlots[0], viewModel.colorSlots.value[0])
    }

    @Test
    fun restoringColorSlotsRestoresActiveColor() {
        val viewModel = NoteViewModel()
        val colors = listOf(Color(0xFF111111), Color(0xFF222222), Color(0xFF333333))

        viewModel.restoreColorSlots(colors, 1)

        assertEquals(colors, viewModel.colorSlots.value)
        assertEquals(1, viewModel.activeColorSlot.value)
        assertEquals(colors[1], viewModel.currentColor.value)
    }
}
