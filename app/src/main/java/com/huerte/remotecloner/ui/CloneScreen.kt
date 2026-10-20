package com.huerte.remotecloner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun CloneScreen(vm: RemoteViewModel, state: RemoteUiState) {
    val c = state.clone
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Clone remote", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Name a key, press it on the original remote, and the signal is recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RemoteSelector(state.names, state.current, vm::select, vm::newRemote)

        OutlinedTextField(
            value = c.keyName,
            onValueChange = vm::setKeyName,
            label = { Text("Key name, for example POWER") },
            singleLine = true,
            enabled = !c.listening,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { vm.startLearn() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startLearn() }, enabled = !c.listening) { Text("Listen for signal") }
            OutlinedButton(onClick = vm::stopLearn, enabled = c.listening) { Text("Cancel") }
        }

        StatusPanel(c)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = c.auto, onCheckedChange = vm::setAuto)
            Text("Save captures automatically")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::savePending, enabled = c.pending != null) { Text("Save") }
            OutlinedButton(onClick = vm::testPending, enabled = c.pending != null) { Text("Test send") }
            OutlinedButton(onClick = vm::discardPending, enabled = c.pending != null) { Text("Discard") }
        }

        Text("Batch capture", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = c.batchText,
            onValueChange = vm::setBatchText,
            label = { Text("One key name per line") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::insertPresets) { Text("Insert common keys") }
            Button(onClick = vm::startBatch, enabled = !c.listening) { Text("Start batch") }
        }

        Text("Keys in this remote", style = MaterialTheme.typography.titleSmall)
        val keys = state.remote?.keys?.keys.orEmpty()
        if (state.remote == null) {
            Text(
                "No remote yet. Create one with New remote.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (keys.isEmpty()) {
            Text(
                "No keys yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Tap a key to reuse its name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            keys.forEach { k ->
                Text(
                    k,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !c.listening) { vm.setKeyName(k) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }

    c.askOverwrite?.let { name ->
        ConfirmDialog(
            title = "Overwrite key?",
            text = "$name already exists in this remote. Replace it with the new capture?",
            confirmText = "Overwrite",
            onConfirm = vm::confirmOverwrite,
            onDismiss = vm::dismissOverwrite,
        )
    }
}

@Composable
private fun StatusPanel(c: CloneUi) {
    val colors = MaterialTheme.colorScheme
    val container = when (c.tone) {
        Tone.IDLE -> colors.surfaceVariant
        Tone.WAITING -> colors.tertiaryContainer
        Tone.SUCCESS -> colors.primaryContainer
        Tone.ERROR -> colors.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
            if (c.listening) LinearProgressIndicator(Modifier.fillMaxWidth())
            c.pending?.let { Text(it.summary(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
