package com.xsgovo.handwrite.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun templatesUseAnIsotropicFixedLongEdge() {
        assertEquals(LogicalCanvas.LONG_EDGE, PageTemplate.LEGACY_PORTRAIT.size.height)
        assertEquals(46_347, PageTemplate.LEGACY_PORTRAIT.size.width)
        assertEquals(LogicalCanvas.LONG_EDGE, PageTemplate.FOUR_BY_THREE.size.width)
    }

    @Test
    fun namesAreNormalizedWithoutChangingDisplayText() {
        val result = DisplayName.create("  Note  ") as NameResult.Valid

        assertEquals("Note", result.name.value)
        assertEquals("note", result.name.normalizedKey)
    }

    @Test
    fun pageContentRequiresSortedElements() {
        val page = Page(PageId(1), DocumentId(1), 1_024, PageTemplate.SQUARE.size, PageBackground.Solid())
        val style = BrushStyle(argb = PageBackground.WHITE, width = 10)
        val first = StrokeElement(ElementId(1), page.id, 1_024, style, listOf(StrokeSample(LogicalPoint(1, 1))))
        val second = StrokeElement(ElementId(2), page.id, 2_048, style, listOf(StrokeSample(LogicalPoint(2, 2))))

        assertEquals(listOf(first, second), PageContent(page, listOf(first, second)).elements)
        assertTrue(page.size.contains(LogicalPoint(1, 1)))
    }
}
