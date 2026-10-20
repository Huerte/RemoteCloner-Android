package com.huerte.remotecloner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huerte.remotecloner.serial.Role
import com.huerte.remotecloner.ui.theme.Ok

private val BAUDS = listOf(115200, 57600, 9600)

@Composable
fun ConnectionScreen(vm: ConnectionViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Connect the receiver board (capture) and the sender board (transmit). " +
                    "Each runs its own sketch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Baud", style = MaterialTheme.typography.labelLarge)
                BAUDS.forEach { b ->
                    FilterChip(
                        selected = state.baud == b,
                        onClick = { vm.setBaud(b) },
                        label = { Text(b.toString()) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::refresh) { Text("Refresh") }
                OutlinedButton(onClick = vm::ping) { Text("Ping") }
            }

            Role.entries.forEach { role ->
                val ui = state.roles.getValue(role)
                RoleCard(
                    role = role,
                    ui = ui,
                    devices = state.devices,
                    onSelect = { vm.select(role, it) },
                    onToggle = { vm.toggle(role) },
                )
            }

            if (state.devices.isEmpty()) {
                Text(
                    "No USB serial devices found. Plug the board in through an OTG adapter, then Refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Serial log", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = vm::clearLog) { Text("Clear log") }
            }
            LogView(state.log, Modifier.height(240.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun RoleCard(
    role: Role,
    ui: RoleUi,
    devices: List<DeviceOption>,
    onSelect: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (ui.connected) Ok else MaterialTheme.colorScheme.outline)
                )
                Column {
                    Text(role.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        ui.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DevicePicker(
                devices = devices,
                selectedId = ui.selectedId,
                enabled = !ui.busy && !ui.connected,
                onSelect = onSelect,
            )
            Button(onClick = onToggle, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.connected) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<DeviceOption>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = devices.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled && devices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(current?.label ?: "No device", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            devices.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.label) },
                    onClick = {
                        onSelect(d.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LogView(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
    ) {
        items(lines) { line ->
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
