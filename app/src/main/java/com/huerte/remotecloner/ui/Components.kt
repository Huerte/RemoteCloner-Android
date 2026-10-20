package com.huerte.remotecloner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String = "OK",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Remote dropdown plus a "New remote" button. Used on the Clone and Control tabs. */
@Composable
fun RemoteSelector(
    names: List<String>,
    current: String?,
    onSelect: (String) -> Unit,
    onNew: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menu = true },
                enabled = names.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(current ?: "No remote", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                names.forEach { n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { onSelect(n); menu = false })
                }
            }
        }
        Button(onClick = { creating = true }) { Text("New remote") }
    }
    if (creating) {
        TextPromptDialog(
            title = "New remote",
            label = "Name (for example Living room TV)",
            initial = "",
            confirmText = "Create",
            onConfirm = { onNew(it); creating = false },
            onDismiss = { creating = false },
        )
    }
}
