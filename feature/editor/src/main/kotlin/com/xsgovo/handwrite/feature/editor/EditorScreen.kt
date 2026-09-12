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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.xsgovo.handwrite.core.document.ResourceInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
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
    // 翻页手势已把旧页面顺滑出屏：切换时跳过退出动画，仅保留新页面的滑入。
    var swipeCommittedSurface by remember { mutableStateOf(false) }
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
                    onAddPage = viewModel::addPage,
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
            // 画布与待添加页共享同一过渡舞台：方向性滑动完成页面间切换。
            val surface = if (state.onPendingAddPage) {
                EditorSurface.PendingAddPage
            } else {
                EditorSurface.Page(state.pageId)
            }
            AnimatedContent(
                targetState = surface,
                transitionSpec = {
                    val direction = editorSurfaceNavDirection(initialState, targetState, state.pages)
                    when {
                        swipeCommittedSurface && targetState is EditorSurface.PendingAddPage ->
                            EnterTransition.None togetherWith ExitTransition.None
                        swipeCommittedSurface ->
                            enterAfterSwipe(direction) togetherWith ExitTransition.None
                        direction > 0 -> slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        direction < 0 -> slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        else -> fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    }
                },
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) {
                    when (target) {
                        is EditorSurface.Page ->
                            if (!state.onPendingAddPage && target.pageId == state.pageId) {
                                // 画布内部状态（平移、擦除预览、笔画包围盒缓存）以页面为生命周期。
                                HandwriteCanvas(
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
                                    canNavigatePrev = (state.pageIndex ?: 0) > 0,
                                    isLastPage = state.pageCount == 0 ||
                                        (state.pageIndex ?: 0) >= state.pageCount - 1,
                                    prevPageContent = state.pageIndex?.takeIf { it > 0 }
                                        ?.let { state.pages.getOrNull(it - 1) },
                                    nextPageContent = state.pageIndex
                                        ?.takeIf { it < state.pages.size - 1 }
                                        ?.let { state.pages.getOrNull(it + 1) },
                                    onSwipeNavigate = { direction ->
                                        swipeCommittedSurface = true
                                        val index = state.pageIndex
                                        when (direction) {
                                            SWIPE_NAVIGATE_PREV -> if (index != null && index > 0) {
                                                viewModel.selectPage(state.pages[index - 1].page.id)
                                            }
                                            SWIPE_NAVIGATE_NEXT -> if (index != null && index < state.pages.size - 1) {
                                                viewModel.selectPage(state.pages[index + 1].page.id)
                                            }
                                            SWIPE_NAVIGATE_ADD -> viewModel.enterPendingAddPage()
                                        }
                                    },
                                    onTemporaryEraserChanged = { temporaryEraserActive = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                StaticPageSurface(
                                    page = state.pages.firstOrNull { it.page.id == target.pageId },
                                    fallbackSize = state.pageSize,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        EditorSurface.PendingAddPage -> AddPagePlaceholder(
                            pendingBackground = state.pendingAddBackground,
                            onAddPage = viewModel::addPage,
                            onChangeBackground = viewModel::setPendingAddBackground,
                            onSwipeBack = viewModel::exitPendingAddPage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            LaunchedEffect(surface) { swipeCommittedSurface = false }
        }

        PagePanel(
            visible = pagesPanelOpen,
            pages = state.pages,
            currentPageId = state.pageId,
            addPageSize = state.pageSize,
            onSelectPage = { targetPageId ->
                pagesPanelOpen = false
                viewModel.selectPage(targetPageId)
            },
            onAddPage = viewModel::addPage,
            onDismiss = { pagesPanelOpen = false },
            onDeletePage = viewModel::deletePage,
            onToggleBookmark = viewModel::togglePageBookmark,
            onMovePage = viewModel::movePage,
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

// 过渡舞台：普通页面或待添加页。
private sealed interface EditorSurface {
    data class Page(val pageId: PageId?) : EditorSurface
    data object PendingAddPage : EditorSurface
}

// 由前后界面推断滑动方向：+1 内容右移（回到更早的页面），-1 内容左移（进入更后的页面/待添加页），0 未知（淡入淡出）。
private fun editorSurfaceNavDirection(
    initial: EditorSurface,
    target: EditorSurface,
    pages: List<PageContent>,
): Int = when {
    initial is EditorSurface.PendingAddPage -> 1
    target is EditorSurface.PendingAddPage -> -1
    initial is EditorSurface.Page && target is EditorSurface.Page -> {
        val from = pages.indexOfFirst { it.page.id == initial.pageId }
        val to = pages.indexOfFirst { it.page.id == target.pageId }
        if (from in pages.indices && to in pages.indices) {
            when {
                to > from -> -1
                to < from -> 1
                else -> 0
            }
        } else {
            0
        }
    }
    else -> 0
}

// 翻页手势切页时旧页面已滑出屏幕，新页面从拖拽方向的相邻侧滑入补位。
private fun enterAfterSwipe(direction: Int): EnterTransition = if (direction < 0) {
    slideInHorizontally { it }
} else {
    slideInHorizontally { -it }
}
