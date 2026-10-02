package com.xsgovo.handwrite.feature.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDocument,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_new_document)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { paddingValues ->
        if (state.documents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.library_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                items(state.documents, key = { it.id.value }) { document ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { deleteTarget = document },
                                    enabled = state.deletingId == null,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.library_delete_document))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.library_delete_dialog_title)) },
            text = { Text(stringResource(R.string.library_delete_dialog_message, document.name.value)) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; viewModel.delete(document.id) }) {
                    Text(stringResource(R.string.library_action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.library_action_cancel)) } },
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
