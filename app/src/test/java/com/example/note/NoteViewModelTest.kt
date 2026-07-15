package com.example.note

import androidx.compose.ui.graphics.Color
import com.note.handwrite.model.NormalizedPoint
import com.note.handwrite.model.Stroke
import com.note.handwrite.viewmodel.NoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteViewModelTest {
    private fun stroke(x: Float) = Stroke(
        points = listOf(NormalizedPoint(x, 0.5f)),
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
}
