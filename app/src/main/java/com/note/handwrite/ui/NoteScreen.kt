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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.note.handwrite.ui.components.ClearConfirmDialog
import com.note.handwrite.ui.components.DrawingCanvas
import com.note.handwrite.ui.components.TopToolbar
import com.note.handwrite.util.prepareNoteForSharing
import com.note.handwrite.util.saveNoteToGallery
import com.note.handwrite.util.shareImage
import com.note.handwrite.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

private const val NOTE_SCREEN_TAG = "NoteScreen"

@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val exportContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val strokes by viewModel.strokes.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()
    val currentColor by viewModel.currentColor.collectAsState()
    val colorSlots by viewModel.colorSlots.collectAsState()
    val activeColorSlot by viewModel.activeColorSlot.collectAsState()
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
    var exportInProgress by remember { mutableStateOf(false) }

    fun launchExport(block: suspend () -> Unit) {
        if (exportInProgress) return
        exportInProgress = true
        scope.launch {
            try {
                block()
            } finally {
                exportInProgress = false
            }
        }
    }

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
                colorSlots = colorSlots,
                activeColorSlot = activeColorSlot,
                currentWidthStep = currentWidthStep,
                currentBackground = backgroundType,
                canUndo = canUndo,
                temporaryEraser = temporaryEraser,
                onToolChange = viewModel::switchTool,
                onColorSlotSelected = viewModel::selectColorSlot,
                onActiveColorChanged = viewModel::updateActiveColor,
                onColorSlotsSaved = viewModel::saveColorSlots,
                onColorSlotsRestored = viewModel::restoreColorSlots,
                onWidthChange = viewModel::switchWidthStep,
                zoomPercent = zoomPercent,
                onZoomDecrease = { pan = Offset.Zero; viewModel.decreaseZoom() },
                onZoomIncrease = { viewModel.increaseZoom() },
                onBackgroundChange = viewModel::switchBackground,
                onUndo = viewModel::undo,
                onClear = { showClearDialog = true },
                onExport = {
                    val strokeSnapshot = strokes.toList()
                    val backgroundSnapshot = backgroundType
                    launchExport {
                        val result = prepareNoteForSharing(
                            context = exportContext,
                            strokes = strokeSnapshot,
                            backgroundType = backgroundSnapshot
                        )
                        val uri = result.getOrNull()
                        if (uri == null) {
                            snackbarHostState.showSnackbar(
                                message = "分享失败，请重试",
                                duration = SnackbarDuration.Long
                            )
                        } else {
                            try {
                                shareImage(context, uri)
                            } catch (exception: Exception) {
                                Log.e(NOTE_SCREEN_TAG, "Failed to launch share activity", exception)
                                snackbarHostState.showSnackbar(
                                    message = "分享失败，请重试",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    }
                },
                onSave = {
                    val strokeSnapshot = strokes.toList()
                    val backgroundSnapshot = backgroundType
                    launchExport {
                        val result = saveNoteToGallery(
                            context = exportContext,
                            strokes = strokeSnapshot,
                            backgroundType = backgroundSnapshot
                        )
                        val uri = result.getOrNull()
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
