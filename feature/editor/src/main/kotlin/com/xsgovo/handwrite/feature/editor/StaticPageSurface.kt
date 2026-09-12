package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.rendering.InkDocumentRenderer

// 页面静态渲染层：翻页过渡时滑出的旧页面用它呈现，只画纸面与笔迹，不挂任何交互。
@Composable
fun StaticPageSurface(
    page: PageContent?,
    fallbackSize: LogicalSize,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { InkDocumentRenderer() }
    val prepared = remember(page?.elements) {
        runCatching {
            renderer.prepare(page?.elements.orEmpty().filterIsInstance<StrokeElement>())
        }.getOrDefault(emptyList())
    }
    val transparentFillColor = MaterialTheme.colorScheme.surface.copy(alpha = TRANSPARENT_PAGE_FILL_ALPHA)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalHandwriteColors.current.canvasDesk)
            .onSizeChanged { canvasSize = it },
    ) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@Box
        val pageSize = page?.page?.size ?: fallbackSize
        val transform = CanvasPageTransform.create(canvasSize, pageSize, 1f, Offset.Zero)
        Canvas(Modifier.fillMaxSize()) {
            val rect = transform.pageRect
            drawRect(
                (page?.page?.background ?: PageBackground.Solid()).baseColor(transparentFillColor),
                topLeft = rect.topLeft,
                size = rect.size,
            )
            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                when (val background = page?.page?.background) {
                    is PageBackground.Pattern -> drawPattern(background, rect, transform.scale)
                    else -> Unit
                }
                if (page != null) {
                    renderer.draw(this, prepared, transform.scale, rect.left, rect.top)
                }
            }
        }
    }
}
