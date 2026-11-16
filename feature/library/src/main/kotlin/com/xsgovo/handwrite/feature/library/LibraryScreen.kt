package com.xsgovo.handwrite.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import com.xsgovo.handwrite.core.designsystem.icons.CuteFlatIconButton
import com.xsgovo.handwrite.core.designsystem.icons.CuteIconBadge
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcon
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.Document
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onNewDocument: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<Document?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryUiEffect.ShowMessage -> snackbar.showSnackbar(resources.getString(effect.messageResId))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    actions = {
                        CuteFlatIconButton(
                            onClick = onSettings,
                            icon = HandwriteIcons.Settings,
                            contentDescription = stringResource(R.string.library_settings),
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDocument,
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                icon = { HandwriteIcon(HandwriteIcons.Add, contentDescription = null, size = 20.dp) },
                text = { Text(stringResource(R.string.library_new_document)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        if (state.documents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CuteIconBadge(
                        imageVector = HandwriteIcons.NoteDocument,
                        contentDescription = null,
                        size = 96.dp,
                        iconSize = 44.dp,
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.library_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                ) {
                    items(state.documents, key = { it.id.value }) { document ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDocument(document.id.value) },
                    ) {
                        ListItem(
                            headlineContent = { Text(document.name.value, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.library_modified_at,
                                        DATE_FORMAT.format(Instant.ofEpochMilli(document.modifiedAtEpochMillis)),
                                    ),
                                )
                            },
                            leadingContent = {
                                CuteIconBadge(
                                    imageVector = HandwriteIcons.NoteDocument,
                                    contentDescription = null,
                                    size = 44.dp,
                                    iconSize = 22.dp,
                                    shape = RoundedCornerShape(14.dp),
                                )
                            },
                            trailingContent = {
                                CuteFlatIconButton(
                                    onClick = { deleteTarget = document },
                                    enabled = state.deletingId == null,
                                    icon = HandwriteIcons.Delete,
                                    contentDescription = stringResource(R.string.library_delete_document),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                }
            }
        }
    }

    deleteTarget?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text(stringResource(R.string.library_delete_dialog_title)) },
            text = { Text(stringResource(R.string.library_delete_dialog_message, document.name.value)) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; viewModel.delete(document.id) }) {
                    Text(
                        stringResource(R.string.library_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.library_action_cancel)) } },
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
