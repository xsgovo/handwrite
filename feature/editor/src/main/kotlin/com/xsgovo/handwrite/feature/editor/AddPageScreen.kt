package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.designsystem.HandwriteTheme
import com.xsgovo.handwrite.core.designsystem.HandwriteThemeMode
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.designsystem.icons.CuteValuePill
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcon
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcons
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PagePattern
import com.xsgovo.handwrite.core.model.PageTemplate
import com.xsgovo.handwrite.core.model.PatternType

// 待添加虚拟页的扁平单卡交互层：
// 1. 顶部柔和凹陷托盘无遮挡展示唯一一份按文档比例缩放的新页面预览（不再叠放加号徽章或重复缩略图）；
// 2. 中部高对比单色主操作按钮触发新建页面；
// 3. 底部“自定义背景”条目展示当前底色与样式摘要，点击展开统一背景菜单。
// interactive 为 false 的滑入阶段只作视觉展示：阻止手指落在卡片上误触，同时保持全不透明颜色避免闪变；
// 在卡片任意区域右滑仍可由父级画布手势顺畅拉回上一页。
@Composable
fun AddPagePlaceholder(
    pendingBackground: PageBackground,
    pageSize: LogicalSize,
    interactive: Boolean,
    onAddPage: () -> Unit,
    onChangeBackground: (PageBackground) -> Unit,
    modifier: Modifier = Modifier,
) {
    var backgroundMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(interactive) {
        if (!interactive) backgroundMenuOpen = false
    }

    val handwriteColors = LocalHandwriteColors.current
    val cardShape = RoundedCornerShape(24.dp)
    val previewTrayShape = RoundedCornerShape(16.dp)
    val actionShape = RoundedCornerShape(14.dp)

    Surface(
        shape = cardShape,
        color = handwriteColors.elevatedCardBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, handwriteColors.elevatedCardBorder),
        modifier = modifier.widthIn(min = 264.dp, max = 316.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(previewTrayShape)
                    .background(handwriteColors.canvasDesk, previewTrayShape)
                    .border(
                        width = 1.dp,
                        color = handwriteColors.elevatedCardBorder.copy(alpha = 0.72f),
                        shape = previewTrayShape,
                    )
                    .clickable(
                        enabled = interactive,
                        role = Role.Button,
                        onClick = onAddPage,
                    )
                    .testTag("add_page_preview_tray")
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                PendingPagePreviewSheet(
                    background = pendingBackground,
                    pageSize = pageSize,
                    borderColor = handwriteColors.elevatedCardBorder,
                )
            }

            Surface(
                onClick = onAddPage,
                enabled = interactive,
                shape = actionShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_page_confirm_button"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                ) {
                    HandwriteIcon(
                        imageVector = HandwriteIcons.Add,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.editor_add_new_page),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.surface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { backgroundMenuOpen = true },
                    enabled = interactive,
                    shape = actionShape,
                    color = handwriteColors.canvasDesk.copy(alpha = 0.75f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, handwriteColors.elevatedCardBorder.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("add_page_background_button"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        HandwriteIcon(
                            imageVector = HandwriteIcons.Background,
                            contentDescription = null,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.editor_custom_background),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        CuteValuePill(
                            text = backgroundSummaryLabel(pendingBackground),
                            containerColor = handwriteColors.elevatedCardBackground,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DropdownMenu(
                    expanded = backgroundMenuOpen,
                    onDismissRequest = { backgroundMenuOpen = false },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = handwriteColors.elevatedCardBackground,
                ) {
                    BackgroundMenuPanel(
                        background = pendingBackground,
                        onSelect = {
                            backgroundMenuOpen = false
                            onChangeBackground(it)
                        },
                        onBack = { backgroundMenuOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPagePreviewSheet(
    background: PageBackground,
    pageSize: LogicalSize,
    borderColor: Color,
) {
    val (sheetWidth, sheetHeight) = remember(pageSize) {
        computePreviewSheetSize(
            pageSize = pageSize,
            maxWidth = 172.dp,
            maxHeight = 132.dp,
        )
    }
    val baseArgb = when (background) {
        is PageBackground.Solid -> background.argb
        is PageBackground.Pattern -> background.baseArgb
        PageBackground.Transparent -> PageBackground.TRANSPARENT
        is PageBackground.Asset -> PageBackground.WHITE
    }
    val patternType = (background as? PageBackground.Pattern)?.type
    val isTransparent = baseArgb == PageBackground.TRANSPARENT
    val isDarkPaper = !isTransparent && Color(baseArgb).luminance() < 0.35f
    val lineColor = if (isDarkPaper) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }
    val sheetShape = RoundedCornerShape(10.dp)
    val shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f)

    Box(
        modifier = Modifier
            .padding(bottom = 2.dp)
            .size(width = sheetWidth, height = sheetHeight),
    ) {
        // 轻量页底边缘投影，增强白纸在浅灰托盘或黑纸在深灰托盘上的纸张层次感
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .height(4.dp)
                .background(shadowColor, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(sheetShape)
                .then(
                    if (isTransparent) {
                        Modifier.checkerboardBackdrop(7.dp)
                    } else {
                        Modifier.background(Color(baseArgb))
                    },
                )
                .border(1.dp, borderColor, sheetShape),
        ) {
            if (patternType != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = size.width / pageSize.width.toFloat()
                    val step = (PagePattern.LOGICAL_SPACING * scale).coerceAtLeast(8.dp.toPx())
                    val strokePx = 1.dp.toPx()
                    var y = step
                    while (y < size.height - 1f) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokePx,
                        )
                        y += step
                    }
                    if (patternType == PatternType.GRID) {
                        var x = step
                        while (x < size.width - 1f) {
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = strokePx,
                            )
                            x += step
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun backgroundSummaryLabel(background: PageBackground): String {
    val baseArgb = when (background) {
        is PageBackground.Solid -> background.argb
        is PageBackground.Pattern -> background.baseArgb
        PageBackground.Transparent -> PageBackground.TRANSPARENT
        is PageBackground.Asset -> PageBackground.WHITE
    }
    val colorLabelRes = backgroundSwatchChoices.firstOrNull { (_, argb) -> argb == baseArgb }?.first
        ?: R.string.editor_background_white
    val colorLabel = stringResource(colorLabelRes)
    val styleLabel = when ((background as? PageBackground.Pattern)?.type) {
        PatternType.LINED -> stringResource(R.string.editor_background_style_lined)
        PatternType.GRID -> stringResource(R.string.editor_background_style_grid)
        null -> null
    }
    return if (styleLabel != null) "$colorLabel · $styleLabel" else colorLabel
}

private fun computePreviewSheetSize(
    pageSize: LogicalSize,
    maxWidth: Dp,
    maxHeight: Dp,
): Pair<Dp, Dp> {
    val aspect = (pageSize.width.toFloat() / pageSize.height.toFloat()).coerceIn(0.45f, 2.2f)
    val maxAspect = maxWidth.value / maxHeight.value
    return if (aspect >= maxAspect) {
        val w = maxWidth
        val h = (maxWidth.value / aspect).dp
        w to h
    } else {
        val h = maxHeight
        val w = (maxHeight.value * aspect).dp.coerceAtLeast(64.dp)
        w to h
    }
}

@Preview(showBackground = true)
@Composable
private fun AddPagePlaceholderLightPreview() {
    HandwriteTheme(themeMode = HandwriteThemeMode.LIGHT) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(LocalHandwriteColors.current.canvasDesk)
                .padding(24.dp),
        ) {
            AddPagePlaceholder(
                pendingBackground = PageBackground.Pattern(PatternType.GRID, PageBackground.WHITE),
                pageSize = PageTemplate.LEGACY_PORTRAIT.size,
                interactive = true,
                onAddPage = {},
                onChangeBackground = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddPagePlaceholderDarkPreview() {
    HandwriteTheme(themeMode = HandwriteThemeMode.DARK) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(LocalHandwriteColors.current.canvasDesk)
                .padding(24.dp),
        ) {
            AddPagePlaceholder(
                pendingBackground = PageBackground.Pattern(PatternType.LINED, PageBackground.CREAM),
                pageSize = PageTemplate.LEGACY_PORTRAIT.size,
                interactive = true,
                onAddPage = {},
                onChangeBackground = {},
            )
        }
    }
}
