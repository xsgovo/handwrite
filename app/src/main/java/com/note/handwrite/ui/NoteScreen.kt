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
import com.note.handwrite.util.exportToClipboard
import com.note.handwrite.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

@Composable
fun NoteScreen(viewModel: NoteViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val strokes by viewModel.strokes.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    val currentColor by viewModel.currentColor.collectAsState()
    val currentWidth by viewModel.currentWidth.collectAsState()
    val backgroundType by viewModel.backgroundType.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopToolbar(
                currentTool = currentTool,
                currentColor = currentColor,
                currentWidth = currentWidth,
                currentBackground = backgroundType,
                canUndo = strokes.isNotEmpty(),
                onToolChange = viewModel::switchTool,
                onColorChange = viewModel::switchColor,
                onWidthChange = viewModel::switchWidth,
                onBackgroundChange = viewModel::switchBackground,
                onUndo = viewModel::undo,
                onClear = { showClearDialog = true },
                onExport = {
                    scope.launch {
                        val success = exportToClipboard(
                            context = context,
                            strokes = strokes,
                            backgroundType = backgroundType,
                            canvasWidth = canvasSize.width.toInt(),
                            canvasHeight = canvasSize.height.toInt(),
                            density = density
                        )
                        snackbarHostState.showSnackbar(
                            message = if (success) "已复制到剪贴板，可粘贴使用" else "导出失败，请重试",
                            duration = if (success) SnackbarDuration.Short else SnackbarDuration.Long
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        DrawingCanvas(
            strokes = strokes,
            currentColor = currentColor,
            currentWidth = currentWidth,
            currentTool = currentTool,
            backgroundType = backgroundType,
            useSpenMode = false,
            onStrokeComplete = viewModel::addStroke,
            onStrokesErased = viewModel::removeStrokes,
            onTemporaryEraserChanged = {},
            onCanvasSizeChanged = { canvasSize = it },
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
