package com.xsgovo.handwrite.feature.editor

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior

@Composable
fun EditorRoute(
    documentId: Long?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onExitApplication: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

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
                onTool = viewModel::setTool,
                onColorSlot = viewModel::selectColorSlot,
                onWidth = viewModel::setWidthStep,
                onZoom = viewModel::setZoom,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = { confirmClear = true },
                onBackground = viewModel::setBackground,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        HandwriteCanvas(
            pageSize = state.pageSize,
            background = state.background,
            strokes = state.strokes,
            tool = state.tool,
            inputMode = state.inputMode,
            zoomPercent = state.zoomPercent,
            activeColor = state.activeColor,
            activeWidth = state.activeWidth,
            onZoomChanged = viewModel::setZoom,
            onStrokeFinished = viewModel::commitStroke,
            onEraseFinished = viewModel::eraseElements,
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
