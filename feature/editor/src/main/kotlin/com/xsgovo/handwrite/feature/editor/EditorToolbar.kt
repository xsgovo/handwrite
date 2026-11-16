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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.designsystem.icons.CuteBrushDotButton
import com.xsgovo.handwrite.core.designsystem.icons.CuteFlatIconButton
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcon
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcons
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MENU_EXIT_ANIMATION_MILLIS = 300L

// 背景二级菜单的底色色板；透明底以棋盘格呈现，样式（横线/方格）可叠加在任意底色上。
// 色板名称保存为字符串资源 ID，展示时再按当前语言解析。
internal val backgroundSwatchChoices = listOf(
    R.string.editor_background_transparent to PageBackground.TRANSPARENT,
    R.string.editor_background_white to PageBackground.WHITE,
    R.string.editor_background_gray to PageBackground.GRAY,
    R.string.editor_background_cream to PageBackground.CREAM,
    R.string.editor_background_pink to PageBackground.PINK,
    R.string.editor_background_teal to PageBackground.TEAL,
    R.string.editor_background_black to PageBackground.BLACK,
)

// 颜色与样式相互独立：无样式时是纯色/透明底，叠加样式时底色成为 pattern 的底色。
internal fun backgroundWithStyle(colorArgb: Int, style: PatternType?): PageBackground = when (style) {
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
    onEraserMode: (EraserMode) -> Unit,
    onEraserSize: (Int) -> Unit,
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
    onOpenPages: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    onZoomLock: (Boolean) -> Unit,
    onBackground: (PageBackground) -> Unit,
    onImportBackground: () -> Unit,
) {
    val primaryScrollState = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }
    var backgroundPanelOpen by remember { mutableStateOf(false) }
    var widthPanelExpanded by remember { mutableStateOf(false) }
    var widthPanelSlot by remember { mutableStateOf<Int?>(null) }
    var pendingWidthStep by remember { mutableStateOf(state.widthStep.toFloat()) }
    val pickerOpenState = remember { mutableStateOf(false) }
    var shareExportMenuExpanded by remember { mutableStateOf(false) }
    var eraserMenuExpanded by remember { mutableStateOf(false) }
    var pendingEraserSize by remember { mutableStateOf(state.eraserSize.toFloat()) }
    LaunchedEffect(eraserMenuExpanded) {
        if (eraserMenuExpanded) pendingEraserSize = state.eraserSize.toFloat()
    }

    val selectedTool = if (temporaryEraserActive) EditorTool.ERASER else state.tool
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val primaryTools: @Composable () -> Unit = {
        CuteFlatIconButton(
            onClick = onLibrary,
            icon = HandwriteIcons.FolderOpen,
            contentDescription = stringResource(R.string.editor_tool_library),
        )
        Box {
            CuteFlatIconButton(
                onClick = { shareExportMenuExpanded = true },
                icon = HandwriteIcons.Share,
                contentDescription = stringResource(R.string.editor_tool_share_or_export),
            )
            DropdownMenu(
                expanded = shareExportMenuExpanded,
                onDismissRequest = { shareExportMenuExpanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_share_current_page_image)) },
                    leadingIcon = {
                        if (isSharing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            HandwriteIcon(HandwriteIcons.Share, contentDescription = null)
                        }
                    },
                    enabled = !isSharing,
                    onClick = {
                        shareExportMenuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_export_document)) },
                    leadingIcon = { HandwriteIcon(HandwriteIcons.Download, contentDescription = null) },
                    enabled = state.documentId != null,
                    onClick = {
                        shareExportMenuExpanded = false
                        onExport()
                    },
                )
            }
        }
        CuteFlatIconButton(
            onClick = onOpenPages,
            icon = HandwriteIcons.PageManager,
            contentDescription = stringResource(R.string.editor_tool_page_manager),
        )
        ToolButton(
            tool = EditorTool.PEN,
            selectedTool = selectedTool,
            onTool = onTool,
            icon = HandwriteIcons.Pen,
            description = stringResource(R.string.editor_tool_pen),
        )
        val eraserIcon = if (state.eraserMode == EraserMode.PIXEL) {
            HandwriteIcons.EraserPixel
        } else {
            HandwriteIcons.EraserStroke
        }
        Box {
            ToolButton(
                tool = EditorTool.ERASER,
                selectedTool = selectedTool,
                onTool = { if (selectedTool == EditorTool.ERASER) eraserMenuExpanded = true else onTool(EditorTool.ERASER) },
                icon = eraserIcon,
                description = stringResource(R.string.editor_tool_eraser),
                hasSubMenu = true,
            )
            EraserDropdownMenu(
                expanded = eraserMenuExpanded,
                eraserMode = state.eraserMode,
                eraserSize = pendingEraserSize,
                onSelectMode = { mode ->
                    onTool(EditorTool.ERASER)
                    onEraserMode(mode)
                },
                onPendingSizeChange = { pendingEraserSize = it },
                onCommitSize = onEraserSize,
                onDismiss = { eraserMenuExpanded = false },
            )
        }
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
                BrushWidthDropdownMenu(
                    expanded = panelIsOpen,
                    widthStep = pendingWidthStep,
                    activeColorArgb = state.activeColor,
                    onPendingWidthChange = { pendingWidthStep = it },
                    onCommitWidth = onWidth,
                    onDismiss = {
                        widthPanelSlot = null
                        widthPanelExpanded = false
                    },
                )
            }
        }
        CuteFlatIconButton(
            onClick = { onZoomLock(!state.zoomLocked) },
            selected = state.zoomLocked,
            icon = if (state.zoomLocked) HandwriteIcons.Lock else HandwriteIcons.LockOpen,
            contentDescription = stringResource(R.string.editor_zoom_lock),
        )
        CuteFlatIconButton(
            onClick = onClear,
            enabled = state.elements.isNotEmpty(),
            icon = HandwriteIcons.ClearPage,
            contentDescription = stringResource(R.string.editor_clear_page),
        )
    }

    val pinnedActions: @Composable () -> Unit = {
        CuteFlatIconButton(
            onClick = onUndo,
            enabled = state.canUndo,
            icon = HandwriteIcons.Undo,
            contentDescription = stringResource(R.string.editor_undo),
        )
        CuteFlatIconButton(
            onClick = onRedo,
            enabled = state.canRedo,
            icon = HandwriteIcons.Redo,
            contentDescription = stringResource(R.string.editor_redo),
        )
        Box {
            CuteFlatIconButton(
                onClick = { menuExpanded = true },
                icon = HandwriteIcons.MoreVert,
                contentDescription = stringResource(R.string.editor_canvas_options),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = {
                    menuExpanded = false
                    backgroundPanelOpen = false
                },
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                if (!backgroundPanelOpen) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_background)) },
                        leadingIcon = { HandwriteIcon(HandwriteIcons.Background, contentDescription = null) },
                        onClick = { backgroundPanelOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_import_background)) },
                        leadingIcon = { HandwriteIcon(HandwriteIcons.AddImage, contentDescription = null) },
                        onClick = { menuExpanded = false; onImportBackground() },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_add_page)) },
                        leadingIcon = { HandwriteIcon(HandwriteIcons.AddPage, contentDescription = null) },
                        onClick = { menuExpanded = false; onAddPage() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_delete_current_page)) },
                        leadingIcon = { HandwriteIcon(HandwriteIcons.DeletePage, contentDescription = null) },
                        onClick = { menuExpanded = false; onDeletePage() },
                        enabled = state.pageCount > 1,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_settings)) },
                        leadingIcon = { HandwriteIcon(HandwriteIcons.Settings, contentDescription = null) },
                        onClick = { menuExpanded = false; onSettings() },
                    )
                } else {
                    BackgroundMenuPanel(
                        background = if (state.onPendingAddPage) state.pendingAddBackground else state.background,
                        onSelect = {
                            menuExpanded = false
                            backgroundPanelOpen = false
                            onBackground(it)
                        },
                        onBack = { backgroundPanelOpen = false },
                    )
                }
            }
        }
    }

    Surface(
        color = LocalHandwriteColors.current.toolbarBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(primaryScrollState),
                ) {
                    primaryTools()
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(dividerColor),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    pinnedActions()
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
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
    CuteBrushDotButton(
        dotSize = brushWidthIconSizeDp(step).dp,
        selected = selected,
        contentDescription = stringResource(R.string.editor_brush_width_content_description, step),
        onClick = onClick,
    )
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
    icon: ImageVector,
    description: String,
    hasSubMenu: Boolean = false,
) {
    CuteFlatIconButton(
        onClick = { onTool(tool) },
        icon = icon,
        contentDescription = description,
        selected = tool == selectedTool,
        hasSubMenu = hasSubMenu,
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                // 半透明槽位垫棋盘格让透明度可被感知；不透明颜色完全覆盖棋盘格，观感不变。
                .checkerboardBackdrop()
                .background(color, CircleShape),
        )
    }
}

// 背景二级菜单：顶部色板行选底色，下方样式项与底色自由组合。
@Composable
internal fun BackgroundMenuPanel(
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
            CuteFlatIconButton(
                onClick = onBack,
                icon = HandwriteIcons.ArrowBack,
                contentDescription = stringResource(R.string.editor_back_to_background_options),
            )
            Text(stringResource(R.string.editor_background), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            backgroundSwatchChoices.forEach { (labelRes, argb) ->
                BackgroundSwatch(
                    labelRes = labelRes,
                    argb = argb,
                    selected = currentColor == argb,
                    onClick = { onSelect(backgroundWithStyle(argb, currentStyle)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        CheckableMenuItem(stringResource(R.string.editor_background_style_none), currentStyle == null) {
            onSelect(backgroundWithStyle(currentColor, null))
        }
        CheckableMenuItem(stringResource(R.string.editor_background_style_lined), currentStyle == PatternType.LINED) {
            onSelect(backgroundWithStyle(currentColor, PatternType.LINED))
        }
        CheckableMenuItem(stringResource(R.string.editor_background_style_grid), currentStyle == PatternType.GRID) {
            onSelect(backgroundWithStyle(currentColor, PatternType.GRID))
        }
    }
}

@Composable
private fun CheckableMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) {
                HandwriteIcon(
                    HandwriteIcons.Check,
                    contentDescription = stringResource(R.string.editor_selected),
                    size = 18.dp,
                )
            }
        },
        onClick = onClick,
    )
}

// 底色色块：透明底垫棋盘格；选中时主色描边加对勾。
@Composable
private fun BackgroundSwatch(labelRes: Int, argb: Int, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    val swatchDescription = stringResource(R.string.editor_background_swatch_content_description, label)
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
            .semantics { contentDescription = swatchDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            HandwriteIcon(
                HandwriteIcons.Check,
                contentDescription = null,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
