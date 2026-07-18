package com.xsgovo.handwrite.core.data.codec

import com.google.protobuf.InvalidProtocolBufferException
import com.xsgovo.handwrite.core.data.proto.AssetBackgroundPayload
import com.xsgovo.handwrite.core.data.proto.BackgroundPayload
import com.xsgovo.handwrite.core.data.proto.BackgroundTransformPayload
import com.xsgovo.handwrite.core.data.proto.BrushBlendMode
import com.xsgovo.handwrite.core.data.proto.LogicalRectPayload
import com.xsgovo.handwrite.core.data.proto.PatternBackgroundPayload
import com.xsgovo.handwrite.core.data.proto.SolidBackgroundPayload
import com.xsgovo.handwrite.core.data.proto.StrokePayload
import com.xsgovo.handwrite.core.data.proto.StrokeSamplePayload
import com.xsgovo.handwrite.core.data.proto.TransparentBackgroundPayload
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BackgroundTransform
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalRect
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.ResourceId
import com.xsgovo.handwrite.core.model.StrokeSample

object PayloadCodec {
    const val STROKE_PAYLOAD_VERSION = 1

    fun encodeStroke(style: BrushStyle, samples: List<StrokeSample>): ByteArray {
        val builder = StrokePayload.newBuilder()
            .setBrushId(style.id.value)
            .setArgb(style.argb)
            .setWidth(style.width)
            .setBlendMode(style.blendMode.toProto())
            .setPressureSensitivity(style.pressureSensitivity.toProto())
        var previous = LogicalPoint(0, 0)
        samples.forEach { sample ->
            val sampleBuilder = StrokeSamplePayload.newBuilder()
                .setDeltaX(sample.point.x - previous.x)
                .setDeltaY(sample.point.y - previous.y)
                .setPressure(sample.pressure)
                .setElapsedMillis(sample.elapsedMillis)
            sample.tiltX?.let(sampleBuilder::setTiltX)
            sample.tiltY?.let(sampleBuilder::setTiltY)
            builder.addSamples(sampleBuilder)
            previous = sample.point
        }
        return builder.build().toByteArray()
    }

    @Throws(InvalidProtocolBufferException::class)
    fun decodeStroke(bytes: ByteArray): Pair<BrushStyle, List<StrokeSample>> {
        val payload = StrokePayload.parseFrom(bytes)
        val style = BrushStyle(
            id = BrushId(payload.brushId.ifBlank { BrushId.MONOLINE.value }),
            argb = payload.argb,
            width = payload.width.coerceAtLeast(1),
            blendMode = payload.blendMode.toDomain(),
            pressureSensitivity = payload.pressureSensitivity.toDomain(),
        )
        var previous = LogicalPoint(0, 0)
        val samples = payload.samplesList.map { sample ->
            val point = LogicalPoint(previous.x + sample.deltaX, previous.y + sample.deltaY)
            previous = point
            StrokeSample(
                point = point,
                pressure = sample.pressure.coerceIn(0, StrokeSample.MAX_PRESSURE),
                elapsedMillis = sample.elapsedMillis,
                tiltX = sample.takeIf { it.hasTiltX() }?.tiltX,
                tiltY = sample.takeIf { it.hasTiltY() }?.tiltY,
            )
        }
        require(samples.isNotEmpty())
        return style to samples
    }

    fun encodeBackground(background: PageBackground): ByteArray = background.toProto().toByteArray()

    @Throws(InvalidProtocolBufferException::class)
    fun decodeBackground(bytes: ByteArray): PageBackground = BackgroundPayload.parseFrom(bytes).toDomain()
}

internal fun PageBackground.toProto(): BackgroundPayload {
    val builder = BackgroundPayload.newBuilder()
    when (this) {
        is PageBackground.Solid -> builder.solid = SolidBackgroundPayload.newBuilder().setArgb(argb).build()
        PageBackground.Transparent -> builder.transparent = TransparentBackgroundPayload.getDefaultInstance()
        is PageBackground.Pattern -> builder.pattern = PatternBackgroundPayload.newBuilder()
            .setType(
                when (type) {
                    PatternType.LINED -> com.xsgovo.handwrite.core.data.proto.PatternType.PATTERN_TYPE_LINED
                    PatternType.GRID -> com.xsgovo.handwrite.core.data.proto.PatternType.PATTERN_TYPE_GRID
                },
            )
            .setBaseArgb(baseArgb)
            .build()
        is PageBackground.Asset -> {
            val transformBuilder = BackgroundTransformPayload.newBuilder()
                .setScalePermille(transform.scalePermille)
                .setTranslationX(transform.translation.x)
                .setTranslationY(transform.translation.y)
                .setRotationMilliDegrees(transform.rotationMilliDegrees)
            transform.crop?.let { crop ->
                transformBuilder.crop = LogicalRectPayload.newBuilder()
                    .setLeft(crop.left)
                    .setTop(crop.top)
                    .setRight(crop.right)
                    .setBottom(crop.bottom)
                    .build()
            }
            val assetBuilder = AssetBackgroundPayload.newBuilder()
                .setResourceId(resourceId.value)
                .setKind(
                    when (kind) {
                        BackgroundAssetKind.IMAGE -> com.xsgovo.handwrite.core.data.proto.BackgroundAssetKind.BACKGROUND_ASSET_KIND_IMAGE
                        BackgroundAssetKind.PDF -> com.xsgovo.handwrite.core.data.proto.BackgroundAssetKind.BACKGROUND_ASSET_KIND_PDF
                    },
                )
                .setTransform(transformBuilder)
            pdfPageIndex?.let(assetBuilder::setPdfPageIndex)
            builder.asset = assetBuilder.build()
        }
    }
    return builder.build()
}

internal fun BackgroundPayload.toDomain(): PageBackground = when (valueCase) {
    BackgroundPayload.ValueCase.SOLID -> PageBackground.Solid(solid.argb)
    BackgroundPayload.ValueCase.TRANSPARENT -> PageBackground.Transparent
    BackgroundPayload.ValueCase.PATTERN -> PageBackground.Pattern(
        type = when (pattern.type) {
            com.xsgovo.handwrite.core.data.proto.PatternType.PATTERN_TYPE_GRID -> PatternType.GRID
            else -> PatternType.LINED
        },
        baseArgb = pattern.baseArgb,
    )
    BackgroundPayload.ValueCase.ASSET -> PageBackground.Asset(
        resourceId = ResourceId(asset.resourceId),
        kind = when (asset.kind) {
            com.xsgovo.handwrite.core.data.proto.BackgroundAssetKind.BACKGROUND_ASSET_KIND_PDF -> BackgroundAssetKind.PDF
            else -> BackgroundAssetKind.IMAGE
        },
        pdfPageIndex = asset.takeIf { it.hasPdfPageIndex() }?.pdfPageIndex,
        transform = asset.transform.toDomain(),
    )
    else -> PageBackground.Solid()
}

private fun BackgroundTransformPayload.toDomain(): BackgroundTransform = BackgroundTransform(
    scalePermille = scalePermille.coerceAtLeast(1),
    translation = LogicalPoint(translationX, translationY),
    rotationMilliDegrees = rotationMilliDegrees,
    crop = takeIf { it.hasCrop() }?.crop?.let { LogicalRect(it.left, it.top, it.right, it.bottom) },
)

private fun com.xsgovo.handwrite.core.model.BrushBlendMode.toProto(): BrushBlendMode = when (this) {
    com.xsgovo.handwrite.core.model.BrushBlendMode.SOURCE_OVER -> BrushBlendMode.BRUSH_BLEND_MODE_SOURCE_OVER
    com.xsgovo.handwrite.core.model.BrushBlendMode.HIGHLIGHT -> BrushBlendMode.BRUSH_BLEND_MODE_HIGHLIGHT
}

private fun BrushBlendMode.toDomain(): com.xsgovo.handwrite.core.model.BrushBlendMode = when (this) {
    BrushBlendMode.BRUSH_BLEND_MODE_HIGHLIGHT -> com.xsgovo.handwrite.core.model.BrushBlendMode.HIGHLIGHT
    else -> com.xsgovo.handwrite.core.model.BrushBlendMode.SOURCE_OVER
}

private fun PressureSensitivity.toProto(): com.xsgovo.handwrite.core.data.proto.PressureSensitivity = when (this) {
    PressureSensitivity.LOW -> com.xsgovo.handwrite.core.data.proto.PressureSensitivity.PRESSURE_SENSITIVITY_LOW
    PressureSensitivity.STANDARD -> com.xsgovo.handwrite.core.data.proto.PressureSensitivity.PRESSURE_SENSITIVITY_STANDARD
    PressureSensitivity.HIGH -> com.xsgovo.handwrite.core.data.proto.PressureSensitivity.PRESSURE_SENSITIVITY_HIGH
}

private fun com.xsgovo.handwrite.core.data.proto.PressureSensitivity.toDomain(): PressureSensitivity = when (this) {
    com.xsgovo.handwrite.core.data.proto.PressureSensitivity.PRESSURE_SENSITIVITY_LOW -> PressureSensitivity.LOW
    com.xsgovo.handwrite.core.data.proto.PressureSensitivity.PRESSURE_SENSITIVITY_HIGH -> PressureSensitivity.HIGH
    else -> PressureSensitivity.STANDARD
}
