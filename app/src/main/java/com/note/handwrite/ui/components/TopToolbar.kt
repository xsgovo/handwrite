package com.note.handwrite.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.Tool

@Composable
fun TopToolbar(
    currentTool: Tool,
    currentColor: Color,
    currentWidth: Float,
    currentBackground: BackgroundType,
    canUndo: Boolean,
    onToolChange: (Tool) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onBackgroundChange: (BackgroundType) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolButton(Tool.PEN, currentTool, Icons.Default.Edit, "画笔", onToolChange)
                ToolButton(Tool.ERASER, currentTool, Icons.Default.Backspace, "橡皮擦", onToolChange)
            }
            ColorPicker(currentColor, onColorChange)
            WidthPicker(currentWidth, onWidthChange)
            BackgroundPicker(currentBackground, onBackgroundChange)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.Default.Undo, contentDescription = "撤销")
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "清空")
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "导出到剪贴板")
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
    onToolChange: (Tool) -> Unit
) {
    IconButton(onClick = { onToolChange(tool) }) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (currentTool == tool) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
