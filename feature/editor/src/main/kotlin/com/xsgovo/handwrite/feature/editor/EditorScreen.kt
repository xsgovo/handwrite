package com.xsgovo.handwrite.feature.editor

import android.content.Context
import android.content.ContextWrapper
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.PDF_MIME_TYPE
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
    val resources = LocalResources.current
    val shareFailedMessage = stringResource(R.string.editor_share_image_failed)
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
            pageSize = state.pageSize,
            interactive = state.onPendingAddPage,
            onAddPage = { viewModel.addPage() },
            onChangeBackground = viewModel::setPendingAddBackground,
        )
    }

    // 状态栏与纯白/纯黑顶部工具栏底色保持一致。
    val toolbarContainerColor = LocalHandwriteColors.current.toolbarBackground
    DisposableEffect(toolbarContainerColor) {
        val activity = context.findComponentActivity()
        if (activity != null) {
            val barArgb = toolbarContainerColor.toArgb()
            val style = if (toolbarContainerColor.luminance() < 0.5f) {
                SystemBarStyle.dark(barArgb)
            } else {
                SystemBarStyle.light(barArgb, barArgb)
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
                is EditorUiEffect.ShowMessage -> snackbar.showSnackbar(resources.getString(effect.messageResId))
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

    val toolbarContent: @Composable () -> Unit = {
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
                        if (!shared) snackbar.showSnackbar(shareFailedMessage)
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
            onBackground = { bg ->
                if (state.onPendingAddPage) {
                    viewModel.setPendingAddBackground(bg)
                } else {
                    viewModel.setBackground(bg)
                }
            },
            onImportBackground = {
                backgroundPicker.launch(arrayOf("image/*", PDF_MIME_TYPE))
            },
        )
    }

    // 切换顶部/侧边停靠时复用同一个可移动画布组合，避免销毁重建低延迟湿墨 SurfaceView。
    val movableCanvas = remember {
        movableContentOf<EditorUiState> { currentState ->
            val prevNeighbor = when (val index = currentState.pageIndex) {
                null -> currentState.pages.lastOrNull()?.let { EditorNeighbor.Page(it) }
                else -> if (index > 0) EditorNeighbor.Page(currentState.pages[index - 1]) else null
            }
            val nextNeighbor = when (val index = currentState.pageIndex) {
                null -> null
                else -> if (index < currentState.pages.size - 1) {
                    EditorNeighbor.Page(currentState.pages[index + 1])
                } else {
                    EditorNeighbor.PendingAdd
                }
            }
            HandwriteCanvas(
                surface = currentState.surface,
                canvasSession = currentState.canvasSession,
                inkingEnabled = !currentState.onPendingAddPage,
                pageSize = currentState.pageSize,
                background = currentState.background,
                backgroundImage = backgroundImage,
                strokes = currentState.strokes,
                tool = currentState.tool,
                eraserMode = currentState.eraserMode,
                eraserSize = currentState.eraserSize,
                inputMode = currentState.inputMode,
                zoomPercent = currentState.zoomPercent,
                zoomLocked = currentState.zoomLocked,
                activeColor = currentState.activeColor,
                activeWidth = currentState.activeWidth,
                activeBrushId = currentState.activeBrushId,
                pressureSensitivity = currentState.pressureSensitivity,
                sideButtonAction = currentState.sideButtonAction,
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
                pageIndex = currentState.pageIndex,
                pageCount = currentState.pageCount,
                onOpenPages = { pagesPanelOpen = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // 面板悬浮于整个界面（含工具栏）之上，因此与主容器同级包在一个 Box 内。
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().zIndex(1f)) {
                toolbarContent()
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                movableCanvas(state)
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )

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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text(stringResource(R.string.editor_clear_page_dialog_title)) },
            text = { Text(stringResource(R.string.editor_clear_page_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearPage() }) {
                    Text(
                        stringResource(R.string.editor_action_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.editor_action_cancel)) } },
        )
    }

    if (confirmDeletePage) {
        AlertDialog(
            onDismissRequest = { confirmDeletePage = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text(stringResource(R.string.editor_delete_page_dialog_title)) },
            text = { Text(stringResource(R.string.editor_delete_page_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDeletePage = false; viewModel.deleteCurrentPage() }) {
                    Text(
                        stringResource(R.string.editor_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePage = false }) { Text(stringResource(R.string.editor_action_cancel)) } },
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
