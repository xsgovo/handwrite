package com.xsgovo.handwrite.feature.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.ImageFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRoute(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    fun acceptDestination(uri: Uri?) {
        uri ?: return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        viewModel.export(documentId, uri)
    }

    val imageTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*"), ::acceptDestination)
    val pdfTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), ::acceptDestination)
    val packageTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), ::acceptDestination)

    LaunchedEffect(documentId) { viewModel.openDocument(documentId) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
        ) {
            Text(state.documentName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
            DocumentExportFormat.entries.forEach { format ->
                ExportFormatRow(
                    format = format,
                    selected = state.format == format,
                    enabled = !state.isExporting,
                    onClick = { viewModel.setFormat(format) },
                )
                HorizontalDivider()
            }
            Button(
                enabled = !state.isExporting,
                onClick = {
                    val baseName = state.documentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    when (state.format) {
                        DocumentExportFormat.PAGE_IMAGE -> imageTarget.launch("$baseName.${state.settings.imageExtension()}")
                        DocumentExportFormat.LONG_IMAGE -> imageTarget.launch("$baseName-长图.${state.settings.imageExtension()}")
                        DocumentExportFormat.HYBRID_PDF -> pdfTarget.launch("$baseName.pdf")
                        DocumentExportFormat.NATIVE_PACKAGE -> packageTarget.launch("$baseName.handwrite.zip")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (state.isExporting) "正在导出" else "选择保存位置")
            }
        }
    }
}

@Composable
private fun ExportFormatRow(
    format: DocumentExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(format.label(), modifier = Modifier.padding(start = 8.dp))
    }
}

private fun DocumentExportFormat.label(): String = when (this) {
    DocumentExportFormat.PAGE_IMAGE -> "当前页面图片"
    DocumentExportFormat.LONG_IMAGE -> "全部页面长图"
    DocumentExportFormat.HYBRID_PDF -> "混合 PDF"
    DocumentExportFormat.NATIVE_PACKAGE -> "原生 ZIP 文档包"
}

private fun com.xsgovo.handwrite.core.model.AppSettings.imageExtension(): String = when (imageFormat) {
    ImageFormat.PNG -> "png"
    ImageFormat.JPEG -> "jpg"
    ImageFormat.AUTO, ImageFormat.WEBP -> "webp"
}
