package com.xsgovo.handwrite.core.model

sealed interface PageBackground {
    data class Solid(val argb: Int = WHITE) : PageBackground

    data object Transparent : PageBackground

    data class Pattern(
        val type: PatternType,
        val baseArgb: Int = WHITE,
    ) : PageBackground

    data class Asset(
        val resourceId: ResourceId,
        val kind: BackgroundAssetKind,
        val pdfPageIndex: Int? = null,
        val transform: BackgroundTransform = BackgroundTransform(),
    ) : PageBackground {
        init {
            require((kind == BackgroundAssetKind.PDF) == (pdfPageIndex != null))
            require(pdfPageIndex == null || pdfPageIndex >= 0)
        }
    }

    companion object {
        const val WHITE: Int = -0x1
    }
}

enum class PatternType {
    LINED,
    GRID,
}

enum class BackgroundAssetKind {
    IMAGE,
    PDF,
}

data class BackgroundTransform(
    val scalePermille: Int = 1_000,
    val translation: LogicalPoint = LogicalPoint(0, 0),
    val rotationMilliDegrees: Int = 0,
    val crop: LogicalRect? = null,
) {
    init {
        require(scalePermille > 0)
    }
}
