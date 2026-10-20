package com.huerte.remotecloner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.SendMode

// Same wording as the Windows mode dropdown.
private fun modeLabel(mode: SendMode) = when (mode) {
    SendMode.RAW -> "Raw replay (most compatible)"
    SendMode.DECODED -> "Decoded (compact, if protocol known)"
}

private enum class Panel { KEYS, MACRO }

private fun appendStep(text: String, step: String) = if (text.isBlank()) step else "${text.trimEnd().trimEnd(',')}, $step"

@Composable
fun ControlScreen(vm: RemoteViewModel, state: RemoteUiState) {
    val remote = state.remote
    val keys = remote?.keys?.keys?.toList().orEmpty()
    val macros = remote?.macros.orEmpty()

    var macroName by remember(state.current) { mutableStateOf("") }
    var macroText by remember(state.current) { mutableStateOf("") }
    var pickMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(Panel.KEYS) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // The whole page is one scrolling grid, so on a short or landscape screen nothing gets squeezed out.
    // Header and macro section span all four columns; the keys are the 4-column cells in between.
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = full) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Control panel", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Send keys from a stored remote or run a saved macro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RemoteSelector(state.names, state.current, vm::select, vm::newRemote)
                Box {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mode: " + modeLabel(state.sendMode), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        SendMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(modeLabel(m)) },
                                onClick = { vm.setSendMode(m); modeMenu = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeats", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { vm.setRepeats(state.repeats - 1) }, enabled = state.repeats > 0) { Text("-") }
                    Text(state.repeats.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { vm.setRepeats(state.repeats + 1) }, enabled = state.repeats < 10) { Text("+") }
                }
                // Result of the last send, kept next to the keys so it is visible without scrolling.
                if (state.sendNote.isNotEmpty()) {
                    Text(
                        state.sendNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                // Keys and Macro are two views of the same page, so macros are never buried below a long key list.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = panel == Panel.KEYS, onClick = { panel = Panel.KEYS }, label = { Text("Keys") })
                    FilterChip(selected = panel == Panel.MACRO, onClick = { panel = Panel.MACRO }, label = { Text("Macro") })
                }
            }
        }

        if (panel == Panel.MACRO) {
            item(span = full) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        if (state.macroRunning) {
                            Button(
                                onClick = vm::stopMacro,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) { Text("Stop") }
                        } else {
                            Button(onClick = { vm.runMacro(macroText) }, enabled = remote != null) { Text("Run") }
                        }
                        OutlinedButton(onClick = { saving = true }, enabled = remote != null && !state.macroRunning) { Text("Save macro") }
                        OutlinedButton(
                            onClick = { deleting = true },
                            enabled = macroName in macros && !state.macroRunning,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete macro") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { macroText = appendStep(macroText, "500") }) { Text("+ 500 ms") }
                        OutlinedButton(onClick = { macroText = ""; macroName = "" }, enabled = macroText.isNotEmpty()) { Text("Clear") }
                    }
                    Text(
                        "Tap a key below to add it to the macro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (keys.isEmpty()) {
            item(span = full) {
                Text(
                    "No keys to send yet. Record keys in Clone remote first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(keys, key = { it }) { k ->
                FilledTonalButton(
                    onClick = { if (panel == Panel.KEYS) vm.fire(k) else macroText = appendStep(macroText, k) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        k,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
