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

    @Test
    fun droppingCommandsForAPageKeepsOtherPagesUndoable() {
        val history = CommandHistory(HistoryLimits(maxCommands = 10, maxEstimatedBytes = 10_000))
        val pageOneCommand = addStroke(1)
        history.recordCommitted(pageOneCommand)
        history.recordCommitted(addStroke(2, pageId = PageId(2)))

        history.dropCommandsForPage(PageId(2))

        assertEquals(pageOneCommand.inverse(), history.commandToUndo())
        history.confirmUndo()
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
        assertEquals(pageOneCommand, history.commandToRedo())
    }

    private fun addStroke(id: Long, pageId: PageId = PageId(1)): DocumentCommand.ReplaceElements {
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
