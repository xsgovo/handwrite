package com.note.handwrite.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ClearConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空画布") },
        text = { Text("确定要清空所有内容吗？此操作不可撤销。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定清空") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
