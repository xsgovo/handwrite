package com.xsgovo.handwrite.core.data.codec

import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCommandCodecTest {
    @Test
    fun replaceElementsRoundTripPreservesOperationAndSequence() {
        val pageId = PageId(8)
        val removed = stroke(id = 11, pageId = pageId, orderKey = 1_024)
        val added = stroke(id = 12, pageId = pageId, orderKey = 2_048)
        val pending = PendingCommand(
            operationId = OperationId("editor-session:17"),
            command = DocumentCommand.ReplaceElements(
                documentId = DocumentId(5),
                pageId = pageId,
                removed = listOf(removed),
                added = listOf(added),
            ),
        )

        val decoded = PendingCommandCodec.decode(PendingCommandCodec.encode(pending, sequence = 99))

        assertEquals(99, decoded.sequence)
        assertEquals(pending, decoded.command)
    }

    private fun stroke(id: Long, pageId: PageId, orderKey: Long) = StrokeElement(
        id = ElementId(id),
        pageId = pageId,
        orderKey = orderKey,
        style = BrushStyle(argb = 0xFF000000.toInt(), width = 64),
        samples = listOf(StrokeSample(LogicalPoint(id.toInt(), id.toInt()))),
    )
}
