package com.note.handwrite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.InputMode
import com.note.handwrite.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit
) {
    val inputMode by viewModel.inputMode.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            androidx.compose.foundation.layout.Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("S Pen 模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (inputMode == InputMode.SPEN) {
                            "仅接受 S Pen，并启用侧键临时橡皮擦"
                        } else {
                            "接受全部输入，包括手指和 S Pen"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = inputMode == InputMode.SPEN,
                    onCheckedChange = { enabled ->
                        viewModel.setInputMode(if (enabled) InputMode.SPEN else InputMode.FINGER)
                    }
                )
            }
        }
    }
}
