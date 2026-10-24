package com.huerte.remotecloner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.Remote
import com.huerte.remotecloner.data.SendMode
import com.huerte.remotecloner.ui.theme.Err
import com.huerte.remotecloner.ui.theme.Ok

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun modeShortLabel(mode: SendMode) = when (mode) {
    SendMode.RAW -> "Raw"
    SendMode.DECODED -> "Decoded"
}

private fun modeDescription(mode: SendMode) = when (mode) {
    SendMode.RAW -> "Raw replay — most compatible, works with any device"
    SendMode.DECODED -> "Decoded — compact signal, requires a known IR protocol"
}

private enum class ControlPanel { KEYS, MACRO }

private fun appendStep(text: String, step: String) =
    if (text.isBlank()) step else "${text.trimEnd().trimEnd(',')}, $step"

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ControlScreen(vm: RemoteViewModel, state: RemoteUiState) {
    val remote = state.remote
    val keys = remote?.keys?.keys?.toList().orEmpty()
    val macros = remote?.macros.orEmpty()

    var macroName by remember(state.current) { mutableStateOf("") }
    var macroText by remember(state.current) { mutableStateOf("") }
    var modeMenu by remember { mutableStateOf(false) }
    var pickMenu by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(ControlPanel.KEYS) }
    var showSendOptions by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item(span = full) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Control",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                )

                // Remote selector
                RemoteSelector(state.names, state.current, vm::select, vm::newRemote)

                // Send result feedback
                if (state.sendNote.isNotEmpty()) {
                    val noteColor = when {
                        state.sendNote.startsWith("Error") || state.sendNote.startsWith("error") -> Err
                        state.sendNote.startsWith("Sent") -> Ok
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.sendNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = noteColor,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                // Send options (collapsed for beginners)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        TextButton(
                            onClick = { showSendOptions = !showSendOptions },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        ) {
                            Text(
                                "Send options: ${modeShortLabel(state.sendMode)}" +
                                    if (state.repeats > 0) " · ${state.repeats}× repeat" else "",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (showSendOptions) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(
                            visible = showSendOptions,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider()

                                // Mode picker
                                Text(
                                    "Signal mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box {
                                    OutlinedButton(
                                        onClick = { modeMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text(
                                            modeDescription(state.sendMode),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                                        SendMode.entries.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(modeDescription(m)) },
                                                onClick = { vm.setSendMode(m); modeMenu = false },
                                            )
                                        }
                                    }
                                }

                                // Repeat counter
                                Text(
                                    "Repeat count",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { vm.setRepeats(state.repeats - 1) },
                                        enabled = state.repeats > 0,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) { Text("−") }
                                    Text(
                                        state.repeats.toString(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                    OutlinedButton(
                                        onClick = { vm.setRepeats(state.repeats + 1) },
                                        enabled = state.repeats < 10,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) { Text("+") }
                                    Text(
                                        if (state.repeats == 0) "Send once" else "Extra sends after first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Panel tabs: Keys / Macro
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ControlPanel.KEYS to "Keys", ControlPanel.MACRO to "Macro").forEach { (p, label) ->
                        if (panel == p) {
                            Button(
                                onClick = { panel = p },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { panel = p },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            ) { Text(label) }
                        }
                    }
                    if (panel == ControlPanel.MACRO) {
                        Text(
                            "Tap a key below to add it to the macro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }

        // ── Macro editor ──────────────────────────────────────────────────
        if (panel == ControlPanel.MACRO) {
            item(span = full) {
                MacroEditor(
                    macros = macros,
                    macroName = macroName,
                    macroText = macroText,
                    running = state.macroRunning,
                    remote = remote,
                    onPickMacro = { n -> macroName = n; macroText = macros[n].orEmpty() },
                    onTextChange = { macroText = it },
                    onRun = { vm.runMacro(macroText) },
                    onStop = vm::stopMacro,
                    onAdd500 = { macroText = appendStep(macroText, "500") },
                    onClear = { macroText = ""; macroName = "" },
                    onSave = { saving = true },
                    onDelete = { deleting = true },
                    pickMenu = pickMenu,
                    onPickMenu = { pickMenu = it },
                )
            }
        }

        // ── Empty state ───────────────────────────────────────────────────
        if (keys.isEmpty()) {
            item(span = full) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (remote == null)
                            "Select a remote above, or create one in My remotes."
                        else
                            "No keys yet. Record buttons in the Clone tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }

        // ── Key grid ──────────────────────────────────────────────────────
        items(keys, key = { it }) { k ->
            FilledTonalButton(
                onClick = {
                    if (panel == ControlPanel.KEYS) vm.fire(k)
                    else macroText = appendStep(macroText, k)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
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

        item(span = full) { Spacer(Modifier.height(16.dp)) }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
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
            text = "Delete \"$macroName\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { vm.deleteMacro(macroName); macroName = ""; macroText = ""; deleting = false },
            onDismiss = { deleting = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro editor panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroEditor(
    macros: Map<String, String>,
    macroName: String,
    macroText: String,
    running: Boolean,
    remote: Remote?,
    onPickMacro: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onAdd500: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    pickMenu: Boolean,
    onPickMenu: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Saved macros picker
            if (macros.isNotEmpty()) {
                Box {
                    OutlinedButton(
                        onClick = { onPickMenu(true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            macroName.ifEmpty { "Load a saved macro" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = pickMenu, onDismissRequest = { onPickMenu(false) }) {
                        macros.keys.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n) },
                                onClick = { onPickMacro(n); onPickMenu(false) },
                            )
                        }
                    }
                }
            }

            // Macro text field
            OutlinedTextField(
                value = macroText,
                onValueChange = onTextChange,
                label = { Text("Steps: POWER, 500, VOL_UP") },
                supportingText = { Text("Key names and numbers (delays in ms), comma-separated.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )

            // Run / Stop
            if (running) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Stop macro") }
            } else {
                Button(
                    onClick = onRun,
                    enabled = remote != null && macroText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Run macro") }
            }

            // Action row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAdd500,
                    enabled = !running,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("+ 500 ms") }
                OutlinedButton(
                    onClick = onSave,
                    enabled = remote != null && !running,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Save") }
                if (macroName.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !running,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                if (macroText.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !running,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text("Clear") }
                }
            }
        }
    }
}
