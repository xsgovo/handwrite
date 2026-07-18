package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryTest {
    @Test
    fun undoAndRedoRemainLinear() {
        val history = CommandHistory(HistoryLimits(maxCommands = 10, maxEstimatedBytes = 10_000))
        val first = addStroke(1)
        val second = addStroke(2)
        history.recordCommitted(first)
        history.recordCommitted(second)

        assertEquals(second.inverse(), history.commandToUndo())
        history.confirmUndo()
        assertEquals(second, history.commandToRedo())

        history.recordCommitted(addStroke(3))
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun oldestCommandsAreEvictedByCount() {
        val history = CommandHistory(HistoryLimits(maxCommands = 2, maxEstimatedBytes = 10_000))
        history.recordCommitted(addStroke(1))
        history.recordCommitted(addStroke(2))
        history.recordCommitted(addStroke(3))

        assertEquals(2, history.undoCount)
    }

    private fun addStroke(id: Long): DocumentCommand.ReplaceElements {
        val pageId = PageId(1)
        val stroke = StrokeElement(
            id = ElementId(id),
            pageId = pageId,
            orderKey = id * 1_024,
            style = BrushStyle(argb = PageBackground.WHITE, width = 10),
            samples = listOf(StrokeSample(LogicalPoint(id.toInt(), id.toInt()))),
        )
        return DocumentCommand.ReplaceElements(DocumentId(1), pageId, removed = emptyList(), added = listOf(stroke))
    }
}
