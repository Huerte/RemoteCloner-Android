package com.huerte.remotecloner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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

@OptIn(ExperimentalFoundationApi::class)
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

    val hScroll = rememberScrollState()           // shared, so the header and every row slide together
    var notes by remember(state.current) { mutableStateOf(remote?.notes.orEmpty()) }

    // One scrolling list holds the header controls and the table, so a short (landscape) screen still
    // shows plenty of rows; only the key action buttons stay pinned at the bottom.
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Library", style = MaterialTheme.typography.headlineSmall)

                    RemoteSelector(state.names, state.current, vm::select, vm::newRemote)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val has = state.current != null
                        OutlinedButton(onClick = { dialog = LibDialog.RenameRemote }, enabled = has) { Text("Rename") }
                        OutlinedButton(onClick = { dialog = LibDialog.DuplicateRemote }, enabled = has) { Text("Duplicate") }
                        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import") }
                        OutlinedButton(onClick = { exporter.launch("${state.current}.json") }, enabled = has) { Text("Export") }
                        OutlinedButton(onClick = { dialog = LibDialog.DeleteRemote }, enabled = has, colors = dangerColors()) { Text("Delete") }
                    }

                    if (remote != null) {
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
                }
            }

            when {
                remote == null -> item { EmptyHint("No remote selected. Create one with New remote.") }
                shown.isEmpty() -> item {
                    EmptyHint(
                        if (q.isNotEmpty()) "No keys match your search."
                        else "This remote has no keys yet. Use Add key, or record them in Clone."
                    )
                }
                else -> {
                    // Same six columns as the Windows table; it scrolls sideways on a narrow screen.
                    stickyHeader { TableHeader(hScroll) }
                    itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                        TableRow(
                            name = e.key,
                            code = e.value,
                            striped = i % 2 == 1,
                            selected = e.key == sel,
                            hScroll = hScroll,
                            onClick = { selected = e.key },
                            onDoubleClick = { selected = e.key; dialog = LibDialog.EditKey(e.key) },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { dialog = LibDialog.NewKey }, enabled = remote != null) { Text("Add key") }
            OutlinedButton(onClick = { dialog = LibDialog.EditKey(sel!!) }, enabled = sel != null) { Text("Edit") }
            OutlinedButton(onClick = { dialog = LibDialog.RenameKey(sel!!) }, enabled = sel != null) { Text("Rename") }
            OutlinedButton(onClick = { vm.relearn(sel!!) }, enabled = sel != null) { Text("Re-learn") }
            OutlinedButton(onClick = { vm.testKey(sel!!) }, enabled = sel != null) { Text("Test send") }
            OutlinedButton(onClick = { vm.duplicateKey(sel!!) }, enabled = sel != null) { Text("Duplicate") }
            OutlinedButton(onClick = { dialog = LibDialog.DeleteKey(sel!!) }, enabled = sel != null, colors = dangerColors()) { Text("Delete") }
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
private fun dangerColors() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val COLUMN_WIDTHS = listOf(130.dp, 100.dp, 80.dp, 80.dp, 70.dp, 180.dp)
@Composable
private fun Cells(values: List<String>, style: TextStyle, color: Color) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { i, v ->
            Text(
                v,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(COLUMN_WIDTHS[i]).padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun TableHeader(hScroll: ScrollState) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.horizontalScroll(hScroll)) {
            Cells(
                listOf("Key", "Protocol", "Address", "Command", "Pulses", "Note"),
                MaterialTheme.typography.labelMedium,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableRow(
    name: String,
    code: IrCode,
    striped: Boolean,
    selected: Boolean,
    hScroll: ScrollState,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        striped -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .heightIn(min = 40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.horizontalScroll(hScroll)) {
            Cells(
                listOf(
                    name,
                    code.protocolLabel,
                    code.addr?.toString().orEmpty(),
                    code.cmd?.toString().orEmpty(),
                    code.raw.size.toString(),
                    code.note,
                ),
                MaterialTheme.typography.bodyMedium,
                MaterialTheme.colorScheme.onSurface,
            )
        }
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
