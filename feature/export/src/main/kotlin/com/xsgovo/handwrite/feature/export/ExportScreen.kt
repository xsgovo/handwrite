package com.xsgovo.handwrite.feature.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import com.xsgovo.handwrite.core.designsystem.icons.CuteFlatIconButton
import com.xsgovo.handwrite.core.designsystem.icons.CuteIconBadge
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcon
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcons
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.PDF_MIME_TYPE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRoute(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val longImageFileSuffix = stringResource(R.string.export_long_image_file_suffix)
    val snackbar = remember { SnackbarHostState() }

    fun acceptDestination(uri: Uri?) {
        uri ?: return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        viewModel.export(documentId, uri)
    }

    val imageTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*"), ::acceptDestination)
    val pdfTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE), ::acceptDestination)
    val packageTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), ::acceptDestination)

    LaunchedEffect(documentId) { viewModel.openDocument(documentId) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportUiEffect.ShowMessage -> snackbar.showSnackbar(resources.getString(effect.messageResId))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.export_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        CuteFlatIconButton(
                            onClick = onBack,
                            icon = HandwriteIcons.ArrowBack,
                            contentDescription = stringResource(R.string.export_back),
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    state.documentName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                DocumentExportFormat.entries.forEach { format ->
                    ExportFormatCard(
                        format = format,
                        selected = state.format == format,
                        enabled = !state.isExporting,
                        onClick = { viewModel.setFormat(format) },
                    )
                }
                Button(
                    enabled = !state.isExporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    onClick = {
                        val baseName = state.documentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        when (state.format) {
                            DocumentExportFormat.PAGE_IMAGE -> imageTarget.launch("$baseName.${state.settings.imageExtension()}")
                            DocumentExportFormat.LONG_IMAGE -> imageTarget.launch("$baseName-$longImageFileSuffix.${state.settings.imageExtension()}")
                            DocumentExportFormat.HYBRID_PDF -> pdfTarget.launch("$baseName.pdf")
                            DocumentExportFormat.NATIVE_PACKAGE -> packageTarget.launch("$baseName.handwrite.zip")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    } else {
                        HandwriteIcon(
                            HandwriteIcons.Download,
                            contentDescription = null,
                            size = 20.dp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(stringResource(if (state.isExporting) R.string.export_button_exporting else R.string.export_button_ready))
                }
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    format: DocumentExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            CuteIconBadge(
                imageVector = format.icon(),
                contentDescription = null,
                size = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(11.dp),
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 4.dp, end = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(format.labelRes()), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(format.descRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DocumentExportFormat.icon() = when (this) {
    DocumentExportFormat.PAGE_IMAGE -> HandwriteIcons.Background
    DocumentExportFormat.LONG_IMAGE -> HandwriteIcons.PageManager
    DocumentExportFormat.HYBRID_PDF -> HandwriteIcons.NoteDocument
    DocumentExportFormat.NATIVE_PACKAGE -> HandwriteIcons.FolderOpen
}

private fun DocumentExportFormat.labelRes(): Int = when (this) {
    DocumentExportFormat.PAGE_IMAGE -> R.string.export_format_page_image
    DocumentExportFormat.LONG_IMAGE -> R.string.export_format_long_image
    DocumentExportFormat.HYBRID_PDF -> R.string.export_format_hybrid_pdf
    DocumentExportFormat.NATIVE_PACKAGE -> R.string.export_format_native_package
}

private fun DocumentExportFormat.descRes(): Int = when (this) {
    DocumentExportFormat.PAGE_IMAGE -> R.string.export_format_page_image_desc
    DocumentExportFormat.LONG_IMAGE -> R.string.export_format_long_image_desc
    DocumentExportFormat.HYBRID_PDF -> R.string.export_format_hybrid_pdf_desc
    DocumentExportFormat.NATIVE_PACKAGE -> R.string.export_format_native_package_desc
}

private fun com.xsgovo.handwrite.core.model.AppSettings.imageExtension(): String = when (imageFormat) {
    ImageFormat.PNG -> "png"
    ImageFormat.JPEG -> "jpg"
    ImageFormat.AUTO, ImageFormat.WEBP -> "webp"
}
