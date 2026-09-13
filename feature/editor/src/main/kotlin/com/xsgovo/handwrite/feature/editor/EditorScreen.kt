package com.xsgovo.handwrite.feature.editor

import android.content.Context
import android.content.ContextWrapper
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.xsgovo.handwrite.core.document.ResourceInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.rendering.rememberBackgroundAssetImage
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EditorRoute(
    documentId: Long?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onExport: (Long) -> Unit,
    onShare: suspend (EditorShareRequest) -> Result<Unit>,
    onExitApplication: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeletePage by remember { mutableStateOf(false) }
    var pagesPanelOpen by remember { mutableStateOf(false) }
    var temporaryEraserActive by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "image/*"
        viewModel.importBackground(
            mimeType = mimeType,
            input = ResourceInput {
                resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            },
        )
    }
    val backgroundImage by rememberBackgroundAssetImage(state.background, state.backgroundResource)
    // 待添加页的按钮层：无纸面渲染，按钮即待添加页的全部内容；滑入的拖拽阶段按钮
    // 即随页面跟入但不可点，停留在待添加页后才启用交互。
    val pendingAddPageContent: @Composable () -> Unit = {
        AddPagePlaceholder(
            pendingBackground = state.pendingAddBackground,
            interactive = state.onPendingAddPage,
            onAddPage = { viewModel.addPage() },
            onChangeBackground = viewModel::setPendingAddBackground,
        )
    }

    // 纸面底色延伸到状态栏并按明暗切换系统图标对比度；无底色时回到系统默认。
    DisposableEffect(state.background) {
        val activity = context.findComponentActivity()
        val scrim = state.background.statusBarColorArgbOrNull()
        if (activity != null) {
            val style = when {
                scrim == null -> SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                Color(scrim).luminance() < 0.5f -> SystemBarStyle.dark(scrim)
                else -> SystemBarStyle.light(scrim, scrim)
            }
            activity.enableEdgeToEdge(statusBarStyle = style)
        }
        onDispose {
            context.findComponentActivity()?.enableEdgeToEdge()
        }
    }

    LaunchedEffect(documentId) { viewModel.openDocument(documentId) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    BackHandler {
        // 系统返回会终结画布的指针事件流，临时橡皮的侧键释放事件可能不再送达；
        // 导航前复位，避免返回过渡期间工具栏停在橡皮高亮上。
        temporaryEraserActive = false
        if (state.backBehavior == BackBehavior.OPEN_LIBRARY) onLibrary() else onExitApplication()
    }
    // 面板的返回处理注册在导航返回之后，打开面板时优先关闭面板。
    BackHandler(enabled = pagesPanelOpen) { pagesPanelOpen = false }
    BackHandler(enabled = state.onPendingAddPage) { viewModel.exitPendingAddPage() }

    // 面板悬浮于整个界面（含工具栏）之上，因此与 Scaffold 同级包在一个 Box 内。
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                EditorToolbar(
                    state = state,
                    temporaryEraserActive = temporaryEraserActive,
                    onLibrary = onLibrary,
                    onSettings = onSettings,
                    onExport = { state.documentId?.value?.let(onExport) },
                    onShare = {
                        if (canStartShare(state.isSaving, isSharing)) {
                            val request = state.toShareRequest()
                            isSharing = true
                            scope.launch {
                                val shared = try {
                                    onShare(request).isSuccess
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    false
                                }
                                isSharing = false
                                if (!shared) snackbar.showSnackbar("分享图片失败")
                            }
                        }
                    },
                    isSharing = isSharing,
                    onTool = viewModel::setTool,
                    onEraserMode = viewModel::setEraserMode,
                    onEraserSize = viewModel::setEraserSize,
                    onColorSlot = viewModel::selectColorSlot,
                    onColorChange = viewModel::setColorSlotValue,
                    onOpacityChange = viewModel::setColorSlotOpacity,
                    onCandidateAdd = viewModel::addPickerCandidate,
                    onCandidateDelete = viewModel::removePickerCandidate,
                    onWidthSlot = viewModel::selectWidthSlot,
                    onWidth = viewModel::setWidthStep,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onClear = { confirmClear = true },
                    onOpenPages = { pagesPanelOpen = true },
                    onAddPage = { viewModel.addPage() },
                    onDeletePage = { confirmDeletePage = true },
                    onZoomLock = viewModel::setZoomLocked,
                    onBackground = viewModel::setBackground,
                    onImportBackground = {
                        backgroundPicker.launch(arrayOf("image/*", "application/pdf"))
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { paddingValues ->
            // 画布常驻：翻页手势的预览与提交都在画布内完成，切页只更换画布内容，不重建组合。
            // InProgressStrokes 的低延迟湿墨 SurfaceView 若随画布组合销毁重建，每次切页都会
            // 闪一下；页面面板选择发生在全屏覆盖层之下，直接呈现即可。
            // 待添加虚拟页作为紧随最后一页的普通翻页目标参与 PREV/NEXT 切换。
            val prevNeighbor = when (val index = state.pageIndex) {
                null -> state.pages.lastOrNull()?.let { EditorNeighbor.Page(it) }
                else -> if (index > 0) EditorNeighbor.Page(state.pages[index - 1]) else null
            }
            val nextNeighbor = when (val index = state.pageIndex) {
                // 停留在待添加虚拟页时没有下一面；停在最后一页时下一面即虚拟页。
                null -> null
                else -> if (index < state.pages.size - 1) {
                    EditorNeighbor.Page(state.pages[index + 1])
                } else {
                    EditorNeighbor.PendingAdd
                }
            }
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                HandwriteCanvas(
                    surface = state.surface,
                    canvasSession = state.canvasSession,
                    inkingEnabled = !state.onPendingAddPage,
                    pageSize = state.pageSize,
                    background = state.background,
                    backgroundImage = backgroundImage,
                    strokes = state.strokes,
                    tool = state.tool,
                    eraserMode = state.eraserMode,
                    eraserSize = state.eraserSize,
                    inputMode = state.inputMode,
                    zoomPercent = state.zoomPercent,
                    zoomLocked = state.zoomLocked,
                    activeColor = state.activeColor,
                    activeWidth = state.activeWidth,
                    activeBrushId = state.activeBrushId,
                    pressureSensitivity = state.pressureSensitivity,
                    sideButtonAction = state.sideButtonAction,
                    onZoomChanged = viewModel::setZoom,
                    onStrokesFinished = viewModel::commitStrokes,
                    onEraseFinished = viewModel::eraseElements,
                    onErasePixelsFinished = viewModel::eraseByPixels,
                    onToggleEraser = viewModel::toggleEraser,
                    onUndo = viewModel::undo,
                    prevNeighbor = prevNeighbor,
                    nextNeighbor = nextNeighbor,
                    pendingAddPageContent = pendingAddPageContent,
                    onSwipeNavigate = { direction ->
                        val target = when (direction) {
                            SWIPE_NAVIGATE_PREV -> prevNeighbor
                            SWIPE_NAVIGATE_NEXT -> nextNeighbor
                            else -> null
                        }
                        when (target) {
                            is EditorNeighbor.Page -> viewModel.selectPage(target.content.page.id)
                            EditorNeighbor.PendingAdd -> viewModel.selectPendingAddPage()
                            null -> Unit
                        }
                    },
                    onTemporaryEraserChanged = { temporaryEraserActive = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        PagePanel(
            visible = pagesPanelOpen,
            pages = state.pages,
            currentPageId = state.pageId,
            addPageSize = state.pageSize,
            // 图片/PDF 背景不可复制到新页，新页面菜单的默认草稿回落到白底。
            currentBackground = state.background.takeUnless { it is PageBackground.Asset }
                ?: PageBackground.Solid(PageBackground.WHITE),
            onSelectPage = { targetPageId ->
                pagesPanelOpen = false
                viewModel.selectPage(targetPageId)
            },
            onAddPage = { background -> viewModel.addPage(background) },
            onDismiss = { pagesPanelOpen = false },
            onDeletePage = viewModel::deletePage,
            onToggleBookmark = viewModel::togglePageBookmark,
            onMovePage = viewModel::movePageTo,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空当前页面？") },
            text = { Text("本次编辑会话中可以撤销此操作。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearPage() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    if (confirmDeletePage) {
        AlertDialog(
            onDismissRequest = { confirmDeletePage = false },
            title = { Text("删除当前页面？") },
            text = { Text("页面上的内容将一并删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmDeletePage = false; viewModel.deleteCurrentPage() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePage = false }) { Text("取消") } },
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
