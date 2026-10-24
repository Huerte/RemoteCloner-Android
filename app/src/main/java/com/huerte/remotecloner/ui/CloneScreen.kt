package com.huerte.remotecloner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huerte.remotecloner.ui.theme.Err
import com.huerte.remotecloner.ui.theme.Ok
import com.huerte.remotecloner.ui.theme.Warn

@Composable
fun CloneScreen(vm: RemoteViewModel, state: RemoteUiState) {
    val c = state.clone
    var showBatch by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Page header ───────────────────────────────────────────────────
        Text("Record keys", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Name a button on your remote, then tap Listen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // ── Remote selector ───────────────────────────────────────────────
        RemoteSelector(state.names, state.current, vm::select, vm::newRemote)

        Spacer(Modifier.height(12.dp))

        // ── Capture card ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Key name field
                OutlinedTextField(
                    value = c.keyName,
                    onValueChange = vm::setKeyName,
                    label = { Text("Button name (e.g. POWER, VOL_UP)") },
                    singleLine = true,
                    enabled = !c.listening,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { vm.startLearn() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )

                // Listen / Cancel row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.startLearn() },
                        enabled = !c.listening && state.current != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Listen for signal")
                    }
                    if (c.listening) {
                        OutlinedButton(
                            onClick = vm::stopLearn,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }

                // Status panel
                CaptureStatusPanel(c)

                // Auto-save toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.setAuto(!c.auto) }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = c.auto, onCheckedChange = vm::setAuto)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("Auto-save captures", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Saves immediately after a successful capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Manual save actions (visible when capture is pending and auto-save is off)
                AnimatedVisibility(
                    visible = c.pending != null && !c.auto,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        Text(
                            "Signal captured. What would you like to do?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = vm::savePending,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Save") }
                            OutlinedButton(
                                onClick = vm::testPending,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Test") }
                            OutlinedButton(
                                onClick = vm::discardPending,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Discard") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Existing keys chip row ─────────────────────────────────────────
        val keys = state.remote?.keys?.keys.orEmpty().toList()
        if (keys.isNotEmpty()) {
            Text(
                "Tap to reuse a key name",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keys) { k ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (c.keyName == k) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable(enabled = !c.listening) { vm.setKeyName(k) },
                    ) {
                        Text(
                            k,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        } else if (state.remote != null) {
            Text(
                "No keys yet. Record your first button above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Batch capture (collapsible) ────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                TextButton(
                    onClick = { showBatch = !showBatch },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    Text(
                        "Batch capture",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showBatch) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(
                    visible = showBatch,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider()
                        Text(
                            "Enter one button name per line. The app will record them one by one automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = c.batchText,
                            onValueChange = vm::setBatchText,
                            label = { Text("One button name per line") },
                            placeholder = { Text("POWER\nVOL_UP\nVOL_DOWN\nMUTE") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = vm::insertPresets,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Insert common keys") }
                            Button(
                                onClick = vm::startBatch,
                                enabled = !c.listening && state.current != null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("Start batch") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Overwrite confirmation dialog ─────────────────────────────────────
    c.askOverwrite?.let { name ->
        ConfirmDialog(
            title = "Replace key?",
            text = "\"$name\" is already saved in this remote. Record it again and replace the stored signal?",
            confirmText = "Replace",
            onConfirm = vm::confirmOverwrite,
            onDismiss = vm::dismissOverwrite,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Capture status panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureStatusPanel(c: CloneUi) {
    val containerColor = when (c.tone) {
        Tone.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
        Tone.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
        Tone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        Tone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (c.tone) {
        Tone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.WAITING -> MaterialTheme.colorScheme.onTertiaryContainer
        Tone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        Tone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            if (c.listening) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            c.pending?.let { code ->
                Text(
                    code.summary(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}
