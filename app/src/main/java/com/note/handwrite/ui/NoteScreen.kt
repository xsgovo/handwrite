package com.note.handwrite.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.note.handwrite.ui.components.ClearConfirmDialog
import com.note.handwrite.ui.components.DrawingCanvas
import com.note.handwrite.ui.components.TopToolbar
import com.note.handwrite.util.saveNoteToGallery
import com.note.handwrite.util.shareNoteDirectly
import com.note.handwrite.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val strokes by viewModel.strokes.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    val currentColor by viewModel.currentColor.collectAsState()
    val currentWidth by viewModel.currentWidth.collectAsState()
    val backgroundType by viewModel.backgroundType.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val logicalCanvasSize by viewModel.documentSize.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var temporaryEraser by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopToolbar(
                currentTool = currentTool,
                currentColor = currentColor,
                currentWidth = currentWidth,
                currentBackground = backgroundType,
                canUndo = canUndo,
                temporaryEraser = temporaryEraser,
                onToolChange = viewModel::switchTool,
                onColorChange = viewModel::switchColor,
                onWidthChange = viewModel::switchWidth,
                onBackgroundChange = viewModel::switchBackground,
                onUndo = viewModel::undo,
                onClear = { showClearDialog = true },
                onExport = {
                    scope.launch {
                        val success = shareNoteDirectly(
                            context = context,
                            strokes = strokes,
                            backgroundType = backgroundType,
                            canvasWidth = canvasSize.width.toInt(),
                            canvasHeight = canvasSize.height.toInt(),
                            logicalCanvasWidth = logicalCanvasSize.width.toInt(),
                            logicalCanvasHeight = logicalCanvasSize.height.toInt(),
                            density = density
                        )
                        if (!success) {
                            snackbarHostState.showSnackbar(
                                message = "分享失败，请重试",
                                duration = SnackbarDuration.Long
                            )
                        }
                    }
                },
                onSave = {
                    scope.launch {
                        val uri = saveNoteToGallery(
                            context = context,
                            strokes = strokes,
                            backgroundType = backgroundType,
                            canvasWidth = canvasSize.width.toInt(),
                            canvasHeight = canvasSize.height.toInt(),
                            logicalCanvasWidth = logicalCanvasSize.width.toInt(),
                            logicalCanvasHeight = logicalCanvasSize.height.toInt(),
                            density = density
                        )
                        snackbarHostState.showSnackbar(
                            message = if (uri != null) "已保存到相册" else "保存失败，请重试",
                            duration = if (uri != null) SnackbarDuration.Short else SnackbarDuration.Long
                        )
                    }
                },
                onOpenSettings = onOpenSettings
            )
        }
    ) { paddingValues ->
        DrawingCanvas(
            strokes = strokes,
            currentColor = currentColor,
            currentWidth = currentWidth,
            currentTool = currentTool,
            backgroundType = backgroundType,
            logicalCanvasSize = logicalCanvasSize,
            useSpenMode = inputMode.name == "SPEN",
            onStrokeComplete = viewModel::addStroke,
            onEraseStart = viewModel::beginErase,
            onEraseEnd = viewModel::endErase,
            onStrokesErased = viewModel::eraseStrokes,
            onTemporaryEraserChanged = { temporaryEraser = it },
            onCanvasSizeChanged = {
                canvasSize = it
                viewModel.setDocumentSize(it)
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        )
    }

    if (showClearDialog) {
        ClearConfirmDialog(
            onConfirm = {
                viewModel.clearAll()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}
