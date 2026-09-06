package com.xsgovo.handwrite.feature.editor

import kotlin.math.roundToInt

internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val sector = (normalizedHue / 60f).toInt()
    val fraction = normalizedHue / 60f - sector
    val weakest = v * (1f - s)
    val decayed = v * (1f - fraction * s)
    val rising = v * (1f - (1f - fraction) * s)
    return when (sector % 6) {
        0 -> argbFromRgb(v, rising, weakest)
        1 -> argbFromRgb(decayed, v, weakest)
        2 -> argbFromRgb(weakest, v, rising)
        3 -> argbFromRgb(weakest, decayed, v)
        4 -> argbFromRgb(rising, weakest, v)
        else -> argbFromRgb(v, weakest, decayed)
    }
}

internal fun argbToHsv(argb: Int): Hsv {
    val red = (argb shr 16 and 0xFF) / 255f
    val green = (argb shr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta + 6f) % 6f)
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return Hsv(hue, if (max == 0f) 0f else delta / max, max)
}

internal fun formatHex(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

internal fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (digits.length != 6) return null
    if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return 0xFF000000.toInt() or digits.toInt(16)
}

internal const val PALETTE_COLUMN_COUNT = 13
internal const val PALETTE_ROW_COUNT = 9

// 色板网格：首列为白到黑的灰阶，其余 12 列为各色相从浅色调到深色调的过渡。
internal fun paletteGridRows(): List<List<Int>> = List(PALETTE_ROW_COUNT) { row ->
    List(PALETTE_COLUMN_COUNT) { column ->
        if (column == 0) {
            hsvToArgb(0f, 0f, 1f - row / (PALETTE_ROW_COUNT - 1f))
        } else {
            val hue = (column - 1) * 360f / (PALETTE_COLUMN_COUNT - 1)
            if (row <= TONE_SPLIT_ROW) {
                val tint = row.toFloat() / TONE_SPLIT_ROW
                hsvToArgb(hue, TINT_TOP_SATURATION + (1f - TINT_TOP_SATURATION) * tint, 1f)
            } else {
                val shade = (row - TONE_SPLIT_ROW) / (PALETTE_ROW_COUNT - 1f - TONE_SPLIT_ROW)
                hsvToArgb(hue, 1f, 1f - shade * (1f - SHADE_BOTTOM_VALUE))
            }
        }
    }
}

private const val TONE_SPLIT_ROW = 4
private const val TINT_TOP_SATURATION = 0.12f
private const val SHADE_BOTTOM_VALUE = 0.2f

internal fun paletteCellAt(x: Float, y: Float, width: Float, height: Float): Pair<Int, Int>? {
    if (width <= 0f || height <= 0f || x < 0f || y < 0f || x >= width || y >= height) return null
    val column = (x / width * PALETTE_COLUMN_COUNT).toInt().coerceAtMost(PALETTE_COLUMN_COUNT - 1)
    val row = (y / height * PALETTE_ROW_COUNT).toInt().coerceAtMost(PALETTE_ROW_COUNT - 1)
    return row to column
}

internal fun paletteCellOf(color: Int, rows: List<List<Int>>): Pair<Int, Int>? {
    rows.forEachIndexed { row, cells ->
        cells.forEachIndexed { column, cell ->
            if (cell == color) return row to column
        }
    }
    return null
}

private fun argbFromRgb(red: Float, green: Float, blue: Float): Int {
    val r = (red.coerceIn(0f, 1f) * 255f).roundToInt()
    val g = (green.coerceIn(0f, 1f) * 255f).roundToInt()
    val b = (blue.coerceIn(0f, 1f) * 255f).roundToInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}
