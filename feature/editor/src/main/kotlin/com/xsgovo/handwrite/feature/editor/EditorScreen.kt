package com.xsgovo.handwrite.feature.editor

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xsgovo.handwrite.core.document.ResourceInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior
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

    LaunchedEffect(documentId) { viewModel.openDocument(documentId) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    BackHandler {
        if (state.backBehavior == BackBehavior.OPEN_LIBRARY) onLibrary() else onExitApplication()
    }

    Scaffold(
        topBar = {
            EditorToolbar(
                state = state,
                onLibrary = onLibrary,
                onSettings = onSettings,
                onExport = { state.documentId?.value?.let(onExport) },
                onShare = {
                    if (!isSharing) {
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
                onColorSlot = viewModel::selectColorSlot,
                onWidth = viewModel::setWidthStep,
                onZoom = viewModel::setZoom,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = { confirmClear = true },
                onBackground = viewModel::setBackground,
                onImportBackground = {
                    backgroundPicker.launch(arrayOf("image/*", "application/pdf"))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        HandwriteCanvas(
            pageSize = state.pageSize,
            background = state.background,
            backgroundImage = backgroundImage,
            strokes = state.strokes,
            tool = state.tool,
            inputMode = state.inputMode,
            zoomPercent = state.zoomPercent,
            activeColor = state.activeColor,
            activeWidth = state.activeWidth,
            activeBrushId = state.activeBrushId,
            pressureSensitivity = state.pressureSensitivity,
            sideButtonAction = state.sideButtonAction,
            onZoomChanged = viewModel::setZoom,
            onStrokesFinished = viewModel::commitStrokes,
            onEraseFinished = viewModel::eraseElements,
            onToggleEraser = viewModel::toggleEraser,
            onUndo = viewModel::undo,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
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
}
