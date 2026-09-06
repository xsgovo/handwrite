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

        // 内置纸张配色预设；背景菜单色板与默认背景持久化共用。透明底用 argb 0 表示。
        const val TRANSPARENT: Int = 0x00000000
        const val GRAY: Int = 0xFFF2F2F2.toInt()
        const val CREAM: Int = 0xFFFFF3E0.toInt()
        const val PINK: Int = 0xFFFCE4EC.toInt()
        const val TEAL: Int = 0xFFE0F2F1.toInt()
        const val BLACK: Int = 0xFF000000.toInt()
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
