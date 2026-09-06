package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MENU_EXIT_ANIMATION_MILLIS = 300L

// 背景二级菜单的底色色板；透明底以棋盘格呈现，样式（横线/方格）可叠加在任意底色上。
private val backgroundSwatchChoices = listOf(
    "透明" to PageBackground.TRANSPARENT,
    "白色" to PageBackground.WHITE,
    "浅灰" to PageBackground.GRAY,
    "米黄" to PageBackground.CREAM,
    "浅粉" to PageBackground.PINK,
    "浅青" to PageBackground.TEAL,
    "纯黑" to PageBackground.BLACK,
)

// 状态栏衬色跟随纸面底色，让画布颜色延伸到系统栏；透明底/图片背景时用工具栏表面色。
internal fun PageBackground.statusBarColorArgbOrNull(): Int? = when (this) {
    is PageBackground.Solid -> argb
    is PageBackground.Pattern -> baseArgb.takeIf { it != PageBackground.TRANSPARENT }
    else -> null
}

// 颜色与样式相互独立：无样式时是纯色/透明底，叠加样式时底色成为 pattern 的底色。
private fun backgroundWithStyle(colorArgb: Int, style: PatternType?): PageBackground = when (style) {
    null -> if (colorArgb == PageBackground.TRANSPARENT) PageBackground.Transparent else PageBackground.Solid(colorArgb)
    else -> PageBackground.Pattern(style, colorArgb)
}

internal fun brushWidthIconSizeDp(widthStep: Int): Float =
    4f + (widthStep.coerceIn(1, 100) - 1) * (20f / 99f)

@Composable
fun EditorToolbar(
    state: EditorUiState,
    temporaryEraserActive: Boolean,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    isSharing: Boolean,
    onTool: (EditorTool) -> Unit,
    onColorSlot: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onCandidateAdd: (Int) -> Unit,
    onCandidateDelete: (Int) -> Unit,
    onWidthSlot: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onZoomLock: (Boolean) -> Unit,
    onBackground: (PageBackground) -> Unit,
    onImportBackground: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }
    var backgroundPanelOpen by remember { mutableStateOf(false) }
    var widthPanelExpanded by remember { mutableStateOf(false) }
    var widthPanelSlot by remember { mutableStateOf<Int?>(null) }
    var pendingWidthStep by remember { mutableStateOf(state.widthStep.toFloat()) }
    val pickerOpenState = remember { mutableStateOf(false) }
    // 无底色（透明/图片背景）时状态栏区域沿用工具栏表面色。
    val barColor = state.background.statusBarColorArgbOrNull()?.let(::Color)
        ?: MaterialTheme.colorScheme.surfaceContainer
    Surface(
        color = barColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .height(64.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
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
    val selectedTool = if (temporaryEraserActive) EditorTool.ERASER else state.tool
    ToolButton(EditorTool.PEN, selectedTool, onTool, Icons.Default.Edit, "画笔")
    ToolButton(EditorTool.ERASER, selectedTool, onTool, Icons.Default.AutoFixNormal, "橡皮擦")
            state.colorSlots.forEachIndexed { index, argb ->
                ColorSlotButton(
                    index = index,
                    argb = argb,
                    candidates = state.pickerCandidates,
                    isActive = index == state.activeColorSlot,
                    pickerOpen = pickerOpenState,
                    onSelectSlot = onColorSlot,
                    onColorChange = onColorChange,
                    onOpacityChange = onOpacityChange,
                    onCandidateAdd = onCandidateAdd,
                    onCandidateDelete = onCandidateDelete,
                )
            }
            state.widthSteps.forEachIndexed { index, step ->
                Box {
                    val panelIsOpen = widthPanelExpanded && widthPanelSlot == index
                    val displayedStep = if (panelIsOpen) pendingWidthStep.roundToInt() else step
                    BrushWidthPresetButton(
                        step = displayedStep,
                        selected = state.activeWidthSlot == index,
                        onClick = {
                            if (state.activeWidthSlot == index) {
                                pendingWidthStep = state.widthSteps[index].toFloat()
                                widthPanelSlot = index
                                widthPanelExpanded = true
                            } else {
                                widthPanelSlot = null
                                widthPanelExpanded = false
                                onWidthSlot(index)
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = panelIsOpen,
                        onDismissRequest = {
                            widthPanelSlot = null
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
            IconButton(
                onClick = { onZoomLock(!state.zoomLocked) },
                colors = if (state.zoomLocked) {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            ) {
                Icon(
                    if (state.zoomLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = "锁定画布",
                )
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                        backgroundPanelOpen = false
                    },
                ) {
                    if (!backgroundPanelOpen) {
                        DropdownMenuItem(
                            text = { Text("背景") },
                            leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                            onClick = { backgroundPanelOpen = true },
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
                    } else {
                        BackgroundMenuPanel(
                            background = state.background,
                            onSelect = {
                                menuExpanded = false
                                onBackground(it)
                            },
                            onBack = { backgroundPanelOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSlotButton(
    index: Int,
    argb: Int,
    candidates: List<Int>,
    isActive: Boolean,
    pickerOpen: MutableState<Boolean>,
    onSelectSlot: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onCandidateAdd: (Int) -> Unit,
    onCandidateDelete: (Int) -> Unit,
) {
    // 菜单关闭时先留在组合中让 DropdownMenu 播完退场动画，再延迟卸载。
    val menuMounted = remember { mutableStateOf(false) }
    LaunchedEffect(pickerOpen.value) {
        if (pickerOpen.value) {
            menuMounted.value = true
        } else if (menuMounted.value) {
            delay(MENU_EXIT_ANIMATION_MILLIS)
            menuMounted.value = false
        }
    }
    Box {
        ColorSwatch(Color(argb), selected = isActive) {
            if (isActive) {
                pickerOpen.value = true
            } else {
                onSelectSlot(index)
            }
        }
        if (isActive && menuMounted.value) {
            ColorPickerMenu(
                expanded = pickerOpen.value,
                activeColor = argb,
                candidates = candidates,
                onColorChanged = onColorChange,
                onOpacityChanged = onOpacityChange,
                onCandidateAdd = onCandidateAdd,
                onCandidateDelete = onCandidateDelete,
                onDismiss = { pickerOpen.value = false },
            )
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
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = "${step}档笔宽",
            modifier = Modifier.size(brushWidthIconSizeDp(step).dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrushWidthPresetButtonPreview() {
    MaterialTheme {
        Row {
            listOf(1, 50, 100).forEachIndexed { index, step ->
                BrushWidthPresetButton(step = step, selected = index == 1, onClick = {})
            }
        }
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
    val selected = tool == selectedTool
    IconButton(
        onClick = { onTool(tool) },
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
            // 半透明槽位垫棋盘格让透明度可被感知；不透明颜色完全覆盖棋盘格，观感不变。
            .clip(CircleShape)
            .checkerboardBackdrop()
            .background(color, CircleShape),
    )
}

// 背景二级菜单：顶部色板行选底色，下方样式项与底色自由组合。
@Composable
private fun BackgroundMenuPanel(
    background: PageBackground,
    onSelect: (PageBackground) -> Unit,
    onBack: () -> Unit,
) {
    val currentColor = when (background) {
        is PageBackground.Solid -> background.argb
        is PageBackground.Pattern -> background.baseArgb
        PageBackground.Transparent -> PageBackground.TRANSPARENT
        is PageBackground.Asset -> PageBackground.WHITE
    }
    val currentStyle = (background as? PageBackground.Pattern)?.type
    // 不固定面板宽度：宽度由色板行内容决定，色块数量变化时不再被截断。
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回背景选项")
            }
            Text("背景", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            backgroundSwatchChoices.forEach { (label, argb) ->
                BackgroundSwatch(
                    label = label,
                    argb = argb,
                    selected = currentColor == argb,
                    onClick = { onSelect(backgroundWithStyle(argb, currentStyle)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        BackgroundStyleItem("无样式", currentStyle == null) {
            onSelect(backgroundWithStyle(currentColor, null))
        }
        BackgroundStyleItem("横线", currentStyle == PatternType.LINED) {
            onSelect(backgroundWithStyle(currentColor, PatternType.LINED))
        }
        BackgroundStyleItem("方格", currentStyle == PatternType.GRID) {
            onSelect(backgroundWithStyle(currentColor, PatternType.GRID))
        }
    }
}

@Composable
private fun BackgroundStyleItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "已选中")
            }
        },
        onClick = onClick,
    )
}

// 底色色块：透明底垫棋盘格；选中时主色描边加对勾。
@Composable
private fun BackgroundSwatch(label: String, argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (argb == PageBackground.TRANSPARENT) Modifier.checkerboardBackdrop() else Modifier)
            .background(Color(argb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label 背景" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
