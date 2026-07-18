package com.xsgovo.handwrite.core.data.codec

import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BackgroundTransform
import com.xsgovo.handwrite.core.model.BrushBlendMode
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalRect
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.ResourceId
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadCodecTest {
    @Test
    fun strokeRoundTripPreservesStyleAndSamples() {
        val style = BrushStyle(
            id = BrushId.PRESSURE_PEN,
            argb = 0x7F123456,
            width = 321,
            blendMode = BrushBlendMode.HIGHLIGHT,
            pressureSensitivity = PressureSensitivity.HIGH,
        )
        val samples = listOf(
            StrokeSample(LogicalPoint(120, 80), pressure = 10_000, elapsedMillis = 0),
            StrokeSample(
                LogicalPoint(125, 72),
                pressure = 60_000,
                elapsedMillis = 17,
                tiltX = -120,
                tiltY = 340,
            ),
        )

        val decoded = PayloadCodec.decodeStroke(PayloadCodec.encodeStroke(style, samples))

        assertEquals(style, decoded.first)
        assertEquals(samples, decoded.second)
    }

    @Test
    fun backgroundsRoundTripWithoutLosingAssetTransform() {
        val backgrounds = listOf(
            PageBackground.Solid(0xFFEEEEEE.toInt()),
            PageBackground.Transparent,
            PageBackground.Pattern(PatternType.GRID, 0xFFFDFDFD.toInt()),
            PageBackground.Asset(
                resourceId = ResourceId(42),
                kind = BackgroundAssetKind.PDF,
                pdfPageIndex = 3,
                transform = BackgroundTransform(
                    scalePermille = 1_250,
                    translation = LogicalPoint(-300, 470),
                    rotationMilliDegrees = 90_000,
                    crop = LogicalRect(10, 20, 1_000, 2_000),
                ),
            ),
        )

        backgrounds.forEach { background ->
            assertEquals(background, PayloadCodec.decodeBackground(PayloadCodec.encodeBackground(background)))
        }
    }
}
