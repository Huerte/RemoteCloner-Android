package com.huerte.remotecloner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.SendMode

@Composable
fun ControlScreen(vm: RemoteViewModel, state: RemoteUiState) {
    val remote = state.remote
    val keys = remote?.keys?.keys?.toList().orEmpty()
    val macros = remote?.macros.orEmpty()

    var macroName by remember(state.current) { mutableStateOf("") }
    var macroText by remember(state.current) { mutableStateOf("") }
    var pickMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Control panel", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Send keys from a stored remote or run a saved macro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RemoteSelector(state.names, state.current, vm::select, vm::newRemote)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.sendMode == SendMode.RAW,
                onClick = { vm.setSendMode(SendMode.RAW) },
                label = { Text("Raw replay") },
            )
            FilterChip(
                selected = state.sendMode == SendMode.DECODED,
                onClick = { vm.setSendMode(SendMode.DECODED) },
                label = { Text("Decoded") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Repeats", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { vm.setRepeats(state.repeats - 1) }, enabled = state.repeats > 0) { Text("-") }
            Text(state.repeats.toString(), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { vm.setRepeats(state.repeats + 1) }, enabled = state.repeats < 10) { Text("+") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (keys.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No keys to send yet. Record keys in Clone first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(keys, key = { it }) { k ->
                        FilledTonalButton(
                            onClick = { vm.fire(k) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (state.sendNote.isNotEmpty()) {
            Text(
                state.sendNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("Macro", style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(
                onClick = { pickMenu = true },
                enabled = macros.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(macroName.ifEmpty { "Saved macros" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = pickMenu, onDismissRequest = { pickMenu = false }) {
                macros.keys.forEach { n ->
                    DropdownMenuItem(
                        text = { Text(n) },
                        onClick = { macroName = n; macroText = macros[n].orEmpty(); pickMenu = false },
                    )
                }
            }
        }
        OutlinedTextField(
            value = macroText,
            onValueChange = { macroText = it },
            label = { Text("POWER, 500, VOL_UP  (numbers are delays in ms)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runMacro(macroText) }, enabled = remote != null) { Text("Run") }
            OutlinedButton(onClick = { saving = true }, enabled = remote != null) { Text("Save macro") }
            OutlinedButton(onClick = { deleting = true }, enabled = macroName in macros) { Text("Delete macro") }
        }
    }

    if (saving) {
        TextPromptDialog(
            title = "Save macro",
            label = "Macro name",
            initial = macroName,
            confirmText = "Save",
            onConfirm = { vm.saveMacro(it, macroText); macroName = it.trim(); saving = false },
            onDismiss = { saving = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete macro",
            text = "Delete macro \u201c$macroName\u201d?",
            confirmText = "Delete",
            onConfirm = { vm.deleteMacro(macroName); macroName = ""; macroText = ""; deleting = false },
            onDismiss = { deleting = false },
        )
    }
}
