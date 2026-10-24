package com.huerte.remotecloner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.keyName

// ─────────────────────────────────────────────────────────────────────────────
// Dialog state
// ─────────────────────────────────────────────────────────────────────────────

private sealed interface LibDialog {
    data object NewKey : LibDialog
    data class EditKey(val name: String) : LibDialog
    data class RenameKey(val name: String) : LibDialog
    data class DeleteKey(val name: String) : LibDialog
    data object RenameRemote : LibDialog
    data object DuplicateRemote : LibDialog
    data object DeleteRemote : LibDialog
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: RemoteViewModel, state: RemoteUiState) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<LibDialog?>(null) }
    var remoteMenu by remember { mutableStateOf(false) }

    val remote = state.remote
    val keys = remote?.keys.orEmpty()
    val sel = selected?.takeIf { it in keys }
    val q = query.trim().lowercase()
    val shown = keys.entries.filter { (k, c) ->
        q.isEmpty() || q in k.lowercase() || q in c.note.lowercase()
    }

    var notes by remember(state.current) { mutableStateOf(remote?.notes.orEmpty()) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            // ── Page header ───────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "My remotes",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    )

                    // Remote selector + overflow menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            RemoteSelector(state.names, state.current, vm::select, vm::newRemote)
                        }
                        // Overflow menu for remote-level actions
                        Box {
                            IconButton(
                                onClick = { remoteMenu = true },
                                enabled = state.current != null,
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Remote options")
                            }
                            DropdownMenu(expanded = remoteMenu, onDismissRequest = { remoteMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { dialog = LibDialog.RenameRemote; remoteMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = { dialog = LibDialog.DuplicateRemote; remoteMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from file") },
                                    onClick = { importer.launch(arrayOf("*/*")); remoteMenu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to file") },
                                    onClick = { exporter.launch("${state.current}.json"); remoteMenu = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete remote",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = { dialog = LibDialog.DeleteRemote; remoteMenu = false },
                                )
                            }
                        }
                    }

                    // Notes field
                    if (remote != null) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it; vm.setNotes(it) },
                            label = { Text("Notes (brand, model, room)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )

                        // Search
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search buttons") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── Key list ──────────────────────────────────────────────────
            when {
                remote == null -> item {
                    EmptyHint("No remote selected. Create one with New remote above.")
                }
                shown.isEmpty() -> item {
                    EmptyHint(
                        if (q.isNotEmpty()) "No buttons match \"$q\"."
                        else "No buttons yet. Tap Add button, or record them in the Clone tab."
                    )
                }
                else -> itemsIndexed(shown, key = { _, e -> e.key }) { _, e ->
                    KeyCard(
                        name = e.key,
                        code = e.value,
                        selected = e.key == sel,
                        onClick = { selected = if (sel == e.key) null else e.key },
                        onEdit = { selected = e.key; dialog = LibDialog.EditKey(e.key) },
                        onRename = { dialog = LibDialog.RenameKey(e.key) },
                        onRelearn = { vm.relearn(e.key) },
                        onTest = { vm.testKey(e.key) },
                        onDuplicate = { vm.duplicateKey(e.key) },
                        onDelete = { dialog = LibDialog.DeleteKey(e.key) },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Add button pinned at bottom ────────────────────────────────────
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = { dialog = LibDialog.NewKey },
                enabled = remote != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Add button")
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
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
            title = "Rename button",
            label = "New name",
            initial = d.name,
            confirmText = "Rename",
            onConfirm = { vm.renameKey(d.name, it); selected = keyName(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is LibDialog.DeleteKey -> ConfirmDialog(
            title = "Delete button",
            text = "Delete \"${d.name}\"? This cannot be undone.",
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
            label = "Name for the copy",
            initial = "${state.current.orEmpty()}_copy",
            confirmText = "Duplicate",
            onConfirm = { vm.duplicateRemote(it); dialog = null },
            onDismiss = { dialog = null },
        )
        LibDialog.DeleteRemote -> ConfirmDialog(
            title = "Delete remote",
            text = "Delete \u201c${state.current}\u201d and all its buttons? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { vm.deleteRemote(); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Key card (replaces horizontal-scroll table row)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCard(
    name: String,
    code: IrCode,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onRelearn: () -> Unit,
    onTest: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var contextMenu by remember { mutableStateOf(false) }

    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { contextMenu = true },
                onDoubleClick = onEdit,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Key name
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    append(code.protocolLabel)
                    if (code.note.isNotBlank()) append(" · ${code.note}")
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Signal info badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    "${code.raw.size}p",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // More options
            Box {
                IconButton(onClick = { contextMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options for $name",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = contextMenu, onDismissRequest = { contextMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit signal") }, onClick = { onEdit(); contextMenu = false })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { onRename(); contextMenu = false })
                    DropdownMenuItem(text = { Text("Re-learn") }, onClick = { onRelearn(); contextMenu = false })
                    DropdownMenuItem(text = { Text("Test send") }, onClick = { onTest(); contextMenu = false })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDuplicate(); contextMenu = false })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); contextMenu = false },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit key dialog
// ─────────────────────────────────────────────────────────────────────────────

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
        val timings = raw.replace(",", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() }
        val p = proto.toIntOrNull()
        val a = addr.toIntOrNull()
        val c = cmd.toIntOrNull()
        val f = khz.toIntOrNull()
        when {
            key.isEmpty() || timings.size < 2 || timings.any { it == null || it < 0 } ->
                error = "Enter a button name and at least two raw timing values."
            p == null || p !in 0..99 || a == null || a !in 0..65535 ||
                c == null || c !in 0..65535 || f == null || f !in 20..60 ->
                error = "Protocol: 0–99 · Address/Command: 0–65535 · Frequency: 20–60 kHz"
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
        title = { Text(if (initialName.isEmpty()) "Add button" else "Edit button") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Button name") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())

                HorizontalDivider()
                Text(
                    "Signal details (filled automatically when recorded)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(pname, { pname = it }, label = { Text("Protocol name") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(proto, { proto = it }, label = { Text("Protocol id") }, singleLine = true, keyboardOptions = number, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                    OutlinedTextField(khz, { khz = it }, label = { Text("kHz") }, singleLine = true, keyboardOptions = number, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(addr, { addr = it }, label = { Text("Address") }, singleLine = true, keyboardOptions = number, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                    OutlinedTextField(cmd, { cmd = it }, label = { Text("Command") }, singleLine = true, keyboardOptions = number, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    raw, { raw = it },
                    label = { Text("Raw timings (µs, comma-separated)") },
                    minLines = 3, maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val number = KeyboardOptions(keyboardType = KeyboardType.Number)
