package com.note.handwrite.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.Tool

@Composable
fun TopToolbar(
    currentTool: Tool,
    currentColor: Color,
    currentWidthStep: Int,
    currentBackground: BackgroundType,
    canUndo: Boolean,
    temporaryEraser: Boolean,
    onToolChange: (Tool) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Int) -> Unit,
    zoomPercent: Int,
    onZoomDecrease: () -> Unit,
    onZoomIncrease: () -> Unit,
    onBackgroundChange: (BackgroundType) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    var moreExpanded by remember { mutableStateOf(false) }
    var backgroundMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawWithContent {
                drawContent()
                if (scrollState.maxValue > scrollState.value) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, surfaceColor)
                        ),
                        topLeft = Offset(size.width - 32.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(32.dp.toPx(), size.height)
                    )
                }
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp)
        ) {
            ToolButton(Tool.PEN, currentTool, Icons.Default.Edit, "画笔", onToolChange)
            ToolButton(
                tool = Tool.ERASER,
                currentTool = currentTool,
                icon = Icons.Default.AutoFixNormal,
                description = "橡皮擦",
                temporary = temporaryEraser,
                onToolChange = onToolChange
            )
            ColorPicker(currentColor, onColorChange)
            WidthPicker(currentWidthStep, currentColor, onWidthChange)
            IconButton(onClick = onZoomDecrease, enabled = zoomPercent > 100) {
                Icon(Icons.Default.Remove, contentDescription = "缩小")
            }
            Text(
                text = "${zoomPercent}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onZoomIncrease, enabled = zoomPercent < 400) {
                Icon(Icons.Default.Add, contentDescription = "放大")
            }
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "清空")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "分享作品")
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "保存到相册")
            }
            Box {
                IconButton(onClick = {
                    moreExpanded = true
                    backgroundMenu = false
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = {
                        moreExpanded = false
                        backgroundMenu = false
                    }
                ) {
                    if (backgroundMenu) {
                        DropdownMenuItem(
                            text = { Text("返回") },
                            leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                            onClick = { backgroundMenu = false }
                        )
                        BackgroundType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(backgroundLabel(type)) },
                                onClick = {
                                    onBackgroundChange(type)
                                    moreExpanded = false
                                    backgroundMenu = false
                                }
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text("背景") },
                            onClick = { backgroundMenu = true }
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                moreExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: Tool,
    currentTool: Tool,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onToolChange: (Tool) -> Unit,
    temporary: Boolean = false
) {
    val selected = currentTool == tool
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (temporary) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) else Modifier
            )
    ) {
        IconButton(onClick = { onToolChange(tool) }) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (selected || temporary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun backgroundLabel(type: BackgroundType): String = when (type) {
    BackgroundType.PLAIN -> "纯白"
    BackgroundType.LINED -> "横线"
    BackgroundType.GRID -> "方格"
}
