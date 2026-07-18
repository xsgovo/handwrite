package com.note.handwrite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
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
    val scope = rememberCoroutineScope()
    val strokes by viewModel.strokes.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    val currentColor by viewModel.currentColor.collectAsState()
    val currentWidth by viewModel.currentWidth.collectAsState()
    val currentWidthStep by viewModel.currentWidthStep.collectAsState()
    val backgroundType by viewModel.backgroundType.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val zoomPercent by viewModel.zoomPercent.collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var temporaryEraser by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var topAligned by remember { mutableStateOf(false) }
    LaunchedEffect(orientation) {
        pan = Offset.Zero
        topAligned = viewModel.resetZoomForOrientation(orientation)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopToolbar(
                currentTool = currentTool,
                currentColor = currentColor,
                currentWidthStep = currentWidthStep,
                currentBackground = backgroundType,
                canUndo = canUndo,
                temporaryEraser = temporaryEraser,
                onToolChange = viewModel::switchTool,
                onColorChange = viewModel::switchColor,
                onWidthChange = viewModel::switchWidthStep,
                zoomPercent = zoomPercent,
                onZoomDecrease = { pan = Offset.Zero; viewModel.decreaseZoom() },
                onZoomIncrease = { viewModel.increaseZoom() },
                onBackgroundChange = viewModel::switchBackground,
                onUndo = viewModel::undo,
                onClear = { showClearDialog = true },
                onExport = {
                    scope.launch {
                        val success = shareNoteDirectly(
                            context = context,
                            strokes = strokes,
                            backgroundType = backgroundType
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
                            backgroundType = backgroundType
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
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            DrawingCanvas(
            strokes = strokes,
            currentColor = currentColor,
            currentWidth = currentWidth,
            currentTool = currentTool,
            backgroundType = backgroundType,
            zoomPercent = zoomPercent.toFloat(),
            pan = pan,
            topAligned = topAligned,
            useSpenMode = inputMode.name == "SPEN",
            onViewportChanged = { zoom, nextPan ->
                viewModel.setZoomPercent(zoom)
                pan = nextPan
            },
            onStrokeComplete = viewModel::addStroke,
            onEraseStart = viewModel::beginErase,
            onEraseEnd = viewModel::endErase,
            onStrokesErased = viewModel::eraseStrokes,
            onTemporaryEraserChanged = { temporaryEraser = it },
            onGestureActiveChanged = { active ->
                (context as? Activity)?.requestedOrientation = if (active) {
                    ActivityInfo.SCREEN_ORIENTATION_LOCKED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            },
            onCanvasSizeChanged = {
                if (canvasSize != Size.Zero && canvasSize != it) pan = Offset.Zero
                canvasSize = it
            },
                modifier = Modifier.fillMaxSize()
            )
            AnimatedVisibility(
                visible = strokes.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.8f).padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.Text(
                        "随手写",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
