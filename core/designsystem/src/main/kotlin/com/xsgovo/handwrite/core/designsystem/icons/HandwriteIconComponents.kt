package com.xsgovo.handwrite.core.designsystem.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.designsystem.HandwriteTheme

/**
 * 简洁扁平图标基础组件。
 */
@Composable
fun HandwriteIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * 可爱圆角方（Squircle）扁平图标按钮：
 * - 保持 48.dp 最小无障碍触控区域，视觉主体为 40.dp 圆角方底。
 * - 选中时使用纯平柔和主题容器色与轻微弹性缩放反馈，无厚重阴影。
 * - 可选 [hasSubMenu] 在右下角呈现迷你小圆点，提示再次点击可展开子菜单。
 */
@Composable
fun CuteFlatIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    hasSubMenu: Boolean = false,
    iconSize: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.90f
            selected -> 1.03f
            else -> 1.0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "CuteButtonScale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) containerColor else Color.Transparent,
        label = "CuteButtonBg",
    )
    val fgColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> contentColor
            else -> unselectedContentColor
        },
        label = "CuteButtonFg",
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .scale(scale)
            .clip(shape)
            .background(bgColor, shape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fgColor,
            modifier = Modifier.size(iconSize),
        )
        if (hasSubMenu && selected && enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(5.dp)
                    .background(fgColor.copy(alpha = 0.75f), RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * 扁平圆润图标徽章：用于列表项头像、空状态主图与区块小图标。
 */
@Composable
fun CuteIconBadge(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    borderColor: Color? = null,
) {
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * 扁平可爱画笔粗细点状按钮：按 [dotSize] 真实反映笔触大小，选中时带柔和扁平圆环。
 */
@Composable
fun CuteBrushDotButton(
    dotSize: Dp,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "CuteBrushScale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "CuteBrushBg",
    )
    val dotColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "CuteBrushDot",
    )
    val haloColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val shape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .scale(scale)
            .clip(shape)
            .background(bgColor, shape)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
            ),
    ) {
        Canvas(
            modifier = Modifier.size(28.dp),
            contentDescription = contentDescription,
        ) {
            if (selected) {
                drawCircle(
                    color = haloColor,
                    radius = (dotSize.toPx() / 2f + 3.dp.toPx()).coerceAtMost(size.minDimension / 2f),
                )
            }
            drawCircle(
                color = dotColor,
                radius = dotSize.toPx() / 2f,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HandwriteIconsGalleryPreview() {
    HandwriteTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Pen, contentDescription = null, selected = true)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.EraserStroke, contentDescription = null, selected = true, hasSubMenu = true)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.EraserPixel, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Undo, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Redo, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.ClearPage, contentDescription = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.FolderOpen, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.PageManager, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Share, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Download, contentDescription = null)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Lock, contentDescription = null, selected = true)
                CuteFlatIconButton(onClick = {}, icon = HandwriteIcons.Settings, contentDescription = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CuteIconBadge(imageVector = HandwriteIcons.NoteDocument, contentDescription = null)
                CuteIconBadge(
                    imageVector = HandwriteIcons.BookmarkFilled,
                    contentDescription = null,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                CuteBrushDotButton(dotSize = 6.dp, selected = false, contentDescription = "", onClick = {})
                CuteBrushDotButton(dotSize = 14.dp, selected = true, contentDescription = "", onClick = {})
            }
        }
    }
}
