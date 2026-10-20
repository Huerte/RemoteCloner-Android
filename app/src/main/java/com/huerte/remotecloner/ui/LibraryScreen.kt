package com.huerte.remotecloner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.keyName

private sealed interface LibDialog {
    data object NewKey : LibDialog
    data class EditKey(val name: String) : LibDialog
    data class RenameKey(val name: String) : LibDialog
    data class DeleteKey(val name: String) : LibDialog
    data object RenameRemote : LibDialog
    data object DuplicateRemote : LibDialog
    data object DeleteRemote : LibDialog
}

@Composable
fun LibraryScreen(vm: RemoteViewModel, state: RemoteUiState) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<LibDialog?>(null) }

    val remote = state.remote
    val keys = remote?.keys.orEmpty()
    val sel = selected?.takeIf { it in keys }
    val q = query.trim().lowercase()
    val shown = keys.entries.filter { (k, c) -> q.isEmpty() || q in k.lowercase() || q in c.note.lowercase() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineSmall)

        RemoteSelector(state.names, state.current, vm::select, vm::newRemote)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val has = state.current != null
            OutlinedButton(onClick = { dialog = LibDialog.RenameRemote }, enabled = has) { Text("Rename") }
            OutlinedButton(onClick = { dialog = LibDialog.DuplicateRemote }, enabled = has) { Text("Duplicate") }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import") }
            OutlinedButton(onClick = { exporter.launch("${state.current}.json") }, enabled = has) { Text("Export") }
            OutlinedButton(onClick = { dialog = LibDialog.DeleteRemote }, enabled = has) { Text("Delete") }
        }

        if (remote != null) {
            var notes by remember(state.current) { mutableStateOf(remote.notes) }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; vm.setNotes(it) },
                label = { Text("Notes (brand, model, room)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search keys") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                remote == null -> EmptyHint("No remote selected. Create one with New remote.")
                shown.isEmpty() -> EmptyHint(
                    if (q.isNotEmpty()) "No keys match your search."
                    else "This remote has no keys yet. Use Add key, or record them in Clone."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.key }) { (k, c) ->
                        KeyRow(k, c, selected = k == sel, onClick = { selected = k })
                    }
                }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = LibDialog.NewKey }, enabled = remote != null) { Text("Add key") }
            OutlinedButton(onClick = { dialog = LibDialog.EditKey(sel!!) }, enabled = sel != null) { Text("Edit") }
            OutlinedButton(onClick = { dialog = LibDialog.RenameKey(sel!!) }, enabled = sel != null) { Text("Rename") }
            OutlinedButton(onClick = { vm.relearn(sel!!) }, enabled = sel != null) { Text("Re-learn") }
            OutlinedButton(onClick = { vm.testKey(sel!!) }, enabled = sel != null) { Text("Test send") }
            OutlinedButton(onClick = { vm.duplicateKey(sel!!) }, enabled = sel != null) { Text("Duplicate") }
            OutlinedButton(onClick = { dialog = LibDialog.DeleteKey(sel!!) }, enabled = sel != null) { Text("Delete") }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibDialog.NewKey -> EditKeyDialog(
            initialName = "",
            initial = null,
            onSave = { name, code -> vm.saveKey(null, name, code); selected = name; dialog = null },
            onDismiss = { dialog = null },
        )
        is LibDialog.EditKey -> EditKeyDialog(
            initialName = d.name,
            initial = keys[d.name],
            onSave = { name, code -> vm.saveKey(d.name, name, code); selected = name; dialog = null },
            onDismiss = { dialog = null },
        )
        is LibDialog.RenameKey -> TextPromptDialog(
            title = "Rename key",
            label = "New name",
            initial = d.name,
            confirmText = "Rename",
            onConfirm = { vm.renameKey(d.name, it); selected = keyName(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibDialog.DeleteKey -> ConfirmDialog(
            title = "Delete key",
            text = "Delete ${d.name}? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { vm.deleteKey(d.name); dialog = null },
            onDismiss = { dialog = null },
        )
        LibDialog.RenameRemote -> TextPromptDialog(
            title = "Rename remote",
            label = "New name",
            initial = state.current.orEmpty(),
            confirmText = "Rename",
            onConfirm = { vm.renameRemote(it); dialog = null },
            onDismiss = { dialog = null },
        )
        LibDialog.DuplicateRemote -> TextPromptDialog(
            title = "Duplicate remote",
            label = "Name of the copy",
            initial = state.current.orEmpty() + "_copy",
            confirmText = "Duplicate",
            onConfirm = { vm.duplicateRemote(it); dialog = null },
            onDismiss = { dialog = null },
        )
        LibDialog.DeleteRemote -> ConfirmDialog(
            title = "Delete remote",
            text = "Delete \u201c${state.current}\u201d and all its keys? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { vm.deleteRemote(); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyRow(name: String, code: IrCode, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(
            "${code.protocolLabel}  addr ${code.addr ?: "-"}  cmd ${code.cmd ?: "-"}  ${code.raw.size} pulses" +
                if (code.note.isNotEmpty()) "  ${code.note}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditKeyDialog(
    initialName: String,
    initial: IrCode?,
    onSave: (String, IrCode) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var pname by remember { mutableStateOf(initial?.name ?: "UNKNOWN") }
    var proto by remember { mutableStateOf((initial?.proto ?: 0).toString()) }
    var addr by remember { mutableStateOf((initial?.addr ?: 0).toString()) }
    var cmd by remember { mutableStateOf((initial?.cmd ?: 0).toString()) }
    var khz by remember { mutableStateOf((initial?.khz ?: 38).toString()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var raw by remember { mutableStateOf(initial?.raw?.joinToString(",") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val key = keyName(name)
        val timings = raw.replace(",", " ").split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() }
        val p = proto.toIntOrNull()
        val a = addr.toIntOrNull()
        val c = cmd.toIntOrNull()
        val f = khz.toIntOrNull()
        when {
            key.isEmpty() || timings.size < 2 || timings.any { it == null || it < 0 } ->
                error = "Enter a key name and at least two numeric raw timings."
            p == null || p !in 0..99 || a == null || a !in 0..65535 || c == null || c !in 0..65535 || f == null || f !in 20..60 ->
                error = "Protocol id is 0-99, address and command are 0-65535, frequency is 20-60 kHz."
            else -> onSave(
                key,
                IrCode(
                    proto = p, name = pname.ifBlank { "UNKNOWN" }, addr = a, cmd = c, khz = f,
                    raw = timings.filterNotNull(), note = note,
                ),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Add key" else "Edit key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Key name") }, singleLine = true)
                OutlinedTextField(pname, { pname = it }, label = { Text("Protocol name") }, singleLine = true)
                OutlinedTextField(proto, { proto = it }, label = { Text("Protocol id") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(addr, { addr = it }, label = { Text("Address") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(cmd, { cmd = it }, label = { Text("Command") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(khz, { khz = it }, label = { Text("Carrier frequency (kHz)") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
                OutlinedTextField(raw, { raw = it }, label = { Text("Raw timings (µs, comma separated)") }, minLines = 3, maxLines = 6)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val number = KeyboardOptions(keyboardType = KeyboardType.Number)
