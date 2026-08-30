package com.xsgovo.handwrite.core.data.db

import com.xsgovo.handwrite.core.data.codec.PayloadCodec
import com.xsgovo.handwrite.core.model.BrushBlendMode
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMappersTest {
    @Test
    fun invalidPersistedNameFallsBackToDefaultName() {
        val bundle = DocumentBundle(
            item = LibraryItemEntity(
                id = 5,
                kind = LibraryItemKinds.DOCUMENT,
                name = "   ",
                normalizedName = "",
                createdAtEpochMillis = 1,
                modifiedAtEpochMillis = 1,
            ),
            state = DocumentStateEntity(documentId = 5, lastActivePageId = 6),
        )

        val document = bundle.toDomain()

        assertEquals(DocumentId(5), document.id)
        assertEquals(defaultName(), document.name)
    }

    @Test
    fun unknownElementTypesAreSkippedWhileValidStrokesSurvive() {
        val bundle = PageBundle(
            page = portraitPage(),
            elements = listOf(unknownElement(), validStrokeEntity()),
        )

        val content = bundle.toDomain()

        assertEquals(listOf(ElementId(7)), content.elements.map { it.id })
    }

    @Test
    fun strokesWithUnknownPayloadVersionOrCorruptPayloadAreSkipped() {
        val bundle = PageBundle(
            page = portraitPage(),
            elements = listOf(
                validStrokeEntity().copy(payloadVersion = PayloadCodec.STROKE_PAYLOAD_VERSION + 1),
                validStrokeEntity().copy(id = 9, payload = byteArrayOf(0, 0)),
            ),
        )

        val content = bundle.toDomain()

        assertTrue(content.elements.isEmpty())
    }

    @Test
    fun unreadableBackgroundPayloadFallsBackToSolid() {
        val page = portraitPage().copy(backgroundPayload = byteArrayOf(0))

        assertEquals(PageBackground.Solid(), page.toDomain().background)
    }

    @Test
    fun strokesRoundTripThroughPersistenceEntitiesWithoutLoss() {
        val strokes = listOf(
            StrokeElement(
                id = ElementId(7),
                pageId = PageId(4),
                orderKey = 2_048L,
                style = BrushStyle(
                    id = BrushId.PRESSURE_PEN,
                    argb = 0x80123456.toInt(),
                    width = 42,
                    blendMode = BrushBlendMode.HIGHLIGHT,
                    pressureSensitivity = PressureSensitivity.HIGH,
                ),
                samples = listOf(
                    StrokeSample(
                        LogicalPoint(10, 20),
                        pressure = 10_000,
                        elapsedMillis = 0,
                        tiltX = -120,
                        tiltY = 340,
                    ),
                    StrokeSample(LogicalPoint(40, 18), pressure = StrokeSample.MAX_PRESSURE, elapsedMillis = 250),
                ),
            ),
            StrokeElement(
                id = ElementId(9),
                pageId = PageId(4),
                orderKey = 4_096L,
                style = BrushStyle(argb = 0xFF112233.toInt(), width = 10),
                samples = listOf(StrokeSample(LogicalPoint(1, 1))),
            ),
        )
        val bundle = PageBundle(portraitPage(), strokes.map(StrokeElement::toEntity).reversed())

        val content = bundle.toDomain()

        assertEquals(PageContent(portraitPage().toDomain(), strokes), content)
    }

    private fun portraitPage() = PageEntity(
        id = 4,
        documentId = 1,
        orderKey = 1_024,
        logicalWidth = 65_535,
        logicalHeight = 65_535,
        backgroundPayload = PayloadCodec.encodeBackground(PageBackground.Solid()),
    )

    private fun validStrokeEntity(): PageElementEntity = StrokeElement(
        id = ElementId(7),
        pageId = PageId(4),
        orderKey = 2_048,
        style = BrushStyle(argb = 0xFF112233.toInt(), width = 10),
        samples = listOf(StrokeSample(LogicalPoint(10, 20))),
    ).toEntity()

    private fun unknownElement() = PageElementEntity(
        id = 8,
        pageId = 4,
        orderKey = 3_072,
        type = "SHAPE",
        payloadVersion = 1,
        payload = ByteArray(0),
    )

    private fun defaultName() = (DisplayName.create("未命名文档") as NameResult.Valid).name
}
