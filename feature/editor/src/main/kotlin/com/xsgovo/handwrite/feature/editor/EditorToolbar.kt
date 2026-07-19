package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import kotlin.math.abs
import kotlin.math.roundToInt

private val BRUSH_WIDTH_PRESETS = listOf(25, 50, 75)

internal fun selectedBrushWidthPreset(widthStep: Int): Int =
    BRUSH_WIDTH_PRESETS.minBy { preset -> abs(preset - widthStep.coerceIn(1, 100)) }

@Composable
fun EditorToolbar(
    state: EditorUiState,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    isSharing: Boolean,
    onTool: (EditorTool) -> Unit,
    onColorSlot: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onBackground: (PageBackground) -> Unit,
    onImportBackground: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }
    var widthPanelExpanded by remember { mutableStateOf(false) }
    var widthPanelPreset by remember { mutableStateOf<Int?>(null) }
    var pendingWidthStep by remember { mutableStateOf(state.widthStep.toFloat()) }
    val selectedWidthPreset = selectedBrushWidthPreset(state.widthStep)
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp),
        ) {
            IconButton(onClick = onLibrary) {
                Icon(Icons.Default.FolderOpen, contentDescription = "文档库")
            }
            IconButton(onClick = onExport, enabled = state.documentId != null) {
                Icon(Icons.Default.FileDownload, contentDescription = "导出")
            }
            IconButton(onClick = onShare, enabled = !isSharing) {
                if (isSharing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Share, contentDescription = "分享当前页面图片")
                }
            }
            ToolButton(EditorTool.PEN, state.tool, onTool, Icons.Default.Edit, "画笔")
            ToolButton(EditorTool.ERASER, state.tool, onTool, Icons.Default.AutoFixNormal, "橡皮擦")
            state.colorSlots.forEachIndexed { index, argb ->
                ColorSwatch(Color(argb), selected = index == state.activeColorSlot) { onColorSlot(index) }
            }
            BRUSH_WIDTH_PRESETS.forEach { preset ->
                Box {
                    BrushWidthPresetButton(
                        step = preset,
                        selected = selectedWidthPreset == preset,
                        onClick = {
                            if (selectedWidthPreset == preset) {
                                pendingWidthStep = state.widthStep.toFloat()
                                widthPanelPreset = preset
                                widthPanelExpanded = true
                            } else {
                                widthPanelPreset = null
                                widthPanelExpanded = false
                                onWidth(preset)
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = widthPanelExpanded && widthPanelPreset == preset,
                        onDismissRequest = {
                            widthPanelPreset = null
                            widthPanelExpanded = false
                        },
                    ) {
                        Column(Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("笔宽 ${pendingWidthStep.roundToInt()}", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = pendingWidthStep,
                                onValueChange = { pendingWidthStep = it },
                                onValueChangeFinished = { onWidth(pendingWidthStep.roundToInt()) },
                                valueRange = 1f..100f,
                                steps = 98,
                            )
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("1", style = MaterialTheme.typography.labelSmall)
                                Text("100", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onUndo, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onRedo, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
            }
            IconButton(onClick = onClear, enabled = state.elements.isNotEmpty()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清空页面")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "画布选项")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("白色背景") },
                        onClick = { menuExpanded = false; onBackground(PageBackground.Solid()) },
                    )
                    DropdownMenuItem(
                        text = { Text("横线背景") },
                        onClick = { menuExpanded = false; onBackground(PageBackground.Pattern(PatternType.LINED)) },
                    )
                    DropdownMenuItem(
                        text = { Text("方格背景") },
                        onClick = { menuExpanded = false; onBackground(PageBackground.Pattern(PatternType.GRID)) },
                    )
                    DropdownMenuItem(
                        text = { Text("导入图片或 PDF 背景") },
                        leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                        onClick = { menuExpanded = false; onImportBackground() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("设置") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { menuExpanded = false; onSettings() },
                    )
                }
            }
            if (state.isSaving) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun BrushWidthPresetButton(
    step: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = if (selected) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
        },
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = "${step}档笔宽",
            modifier = Modifier.size(
                when (step) {
                    25 -> 8.dp
                    50 -> 14.dp
                    else -> 20.dp
                },
            ),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolButton(
    tool: EditorTool,
    selectedTool: EditorTool,
    onTool: (EditorTool) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    IconButton(onClick = { onTool(tool) }) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (tool == selectedTool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .padding(4.dp)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .padding(3.dp)
            .background(color, CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            .clickable(onClick = onClick),
    )
}
