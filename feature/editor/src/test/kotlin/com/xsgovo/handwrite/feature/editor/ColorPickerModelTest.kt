package com.xsgovo.handwrite.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPickerModelTest {
    @Test
    fun paletteGridHasGrayColumnAndTwelveHueColumns() {
        val rows = paletteGridRows()

        assertEquals(PALETTE_ROW_COUNT, rows.size)
        rows.forEach { row -> assertEquals(PALETTE_COLUMN_COUNT, row.size) }
        assertEquals(0xFFFFFFFF.toInt(), rows.first().first())
        assertEquals(0xFF000000.toInt(), rows.last().first())
        assertEquals(0xFFFF0000.toInt(), rows[TONE_SPLIT_ROW_INDEX][RED_COLUMN])
    }

    @Test
    fun hueColumnsKeepConstantHueWhileDarkening() {
        val rows = paletteGridRows()

        val column = rows.map { it[3] }
        column.forEach { color -> assertEquals(60f, argbToHsv(color).hue, 1f) }
        assertTrue(
            column.zipWithNext().all { (above, below) -> argbToHsv(below).value <= argbToHsv(above).value },
        )
    }

    @Test
    fun grayColumnRunsFromWhiteToBlack() {
        val grayColumn = paletteGridRows().map { it[0] }

        assertEquals(0xFFFFFFFF.toInt(), grayColumn.first())
        assertEquals(0xFF000000.toInt(), grayColumn.last())
        assertTrue(
            grayColumn.zipWithNext().all { (above, below) -> argbToHsv(below).value < argbToHsv(above).value },
        )
    }

    @Test
    fun hsvRoundTripsThroughArgb() {
        for (hue in 0..330 step 30) {
            val roundTripped = argbToHsv(hsvToArgb(hue.toFloat(), 1f, 1f))

            assertEquals(hue.toFloat(), roundTripped.hue, 0.6f)
            assertEquals(1f, roundTripped.saturation, 0.01f)
            assertEquals(1f, roundTripped.value, 0.01f)
        }

        val tinted = argbToHsv(hsvToArgb(210f, 0.4f, 0.7f))

        assertEquals(210f, tinted.hue, 1f)
        assertEquals(0.4f, tinted.saturation, 0.01f)
        assertEquals(0.7f, tinted.value, 0.01f)
    }

    @Test
    fun hexFormattingAndParsingRoundTrip() {
        assertEquals("#000000", formatHex(0xFF000000.toInt()))
        assertEquals("#E53935", formatHex(0xFFE53935.toInt()))
        assertEquals(0xFF1F1F1F.toInt(), parseHex("#1F1F1F"))
        assertEquals(0xFF2E7D32.toInt(), parseHex("2e7d32"))
        assertEquals(0xFF0000FF.toInt(), parseHex("#0000ff"))
    }

    @Test
    fun hexParsingRejectsInvalidInput() {
        assertNull(parseHex("#12345"))
        assertNull(parseHex("#1234567"))
        assertNull(parseHex("#12G456"))
        assertNull(parseHex(""))
        assertNull(parseHex("#12 456"))
    }

    @Test
    fun paletteCellAtMapsPositionOntoTheGrid() {
        assertEquals(Pair(0, 0), paletteCellAt(1f, 1f, 130f, 90f))
        assertEquals(Pair(4, 6), paletteCellAt(65f, 45f, 130f, 90f))
        assertEquals(Pair(8, 12), paletteCellAt(129.9f, 89.9f, 130f, 90f))
        assertEquals(null, paletteCellAt(-1f, 45f, 130f, 90f))
        assertEquals(null, paletteCellAt(65f, 90f, 130f, 90f))
        assertEquals(null, paletteCellAt(65f, 45f, 0f, 90f))
    }

    @Test
    fun paletteCellOfLocatesTheWorkingColor() {
        val rows = paletteGridRows()

        assertEquals(Pair(4, 1), paletteCellOf(rows[4][1], rows))
        assertEquals(Pair(0, 0), paletteCellOf(0xFFFFFFFF.toInt(), rows))
        assertEquals(null, paletteCellOf(0xFF123456.toInt(), rows))
    }

    private companion object {
        const val TONE_SPLIT_ROW_INDEX = 4
        const val RED_COLUMN = 1
    }
}
