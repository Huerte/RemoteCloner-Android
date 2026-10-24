package com.huerte.remotecloner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huerte.remotecloner.serial.Firmware
import com.huerte.remotecloner.serial.Role
import com.huerte.remotecloner.ui.theme.Err
import com.huerte.remotecloner.ui.theme.Ok
import com.huerte.remotecloner.ui.theme.Warn

private val BAUDS = listOf(115200, 57600, 9600)

// ─────────────────────────────────────────────────────────────────────────────
// Screen root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectionScreen(vm: ConnectionViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Page header ───────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "Setup",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "Connect your Arduino boards and install firmware.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── Step 1: Install firmware ──────────────────────────────
                SetupStepCard(
                    step = 1,
                    title = "Install firmware on your board",
                    subtitle = "Only needed once per Arduino Uno or Nano (ATmega328P).",
                    complete = false,
                ) {
                    FlashSection(
                        ui = state.flash,
                        devices = state.devices,
                        onDevice = vm::selectFlashDevice,
                        onFirmware = vm::selectFirmware,
                        onInstall = vm::install,
                    )
                }

                // ── Step 2: Connect boards ────────────────────────────────
                SetupStepCard(
                    step = 2,
                    title = "Connect your board(s)",
                    subtitle = "The Combined firmware means one board fills both roles.",
                    complete = state.roles.values.any { it.connected },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Refresh row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.devices.isEmpty()) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Warn,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "No USB devices found. Plug in your board via an OTG adapter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Warn,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Text(
                                    "${state.devices.size} USB device${if (state.devices.size > 1) "s" else ""} found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IconButton(onClick = vm::refresh, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh USB devices")
                            }
                        }

                        Role.entries.forEach { role ->
                            val ui = state.roles.getValue(role)
                            RoleBoardCard(
                                role = role,
                                ui = ui,
                                devices = state.devices,
                                flashRunning = state.flash.running,
                                onSelect = { vm.select(role, it) },
                                onToggle = { vm.toggle(role) },
                            )
                        }
                    }
                }

                // ── Advanced settings ─────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        ) {
                            Text(
                                "Advanced settings",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider()
                                Text(
                                    "Baud rate",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Leave at 115200 unless your Nano uses the old bootloader (57600).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BAUDS.forEach { b ->
                                        FilterChip(
                                            selected = state.baud == b,
                                            onClick = { vm.setBaud(b) },
                                            label = { Text(b.toString()) },
                                        )
                                    }
                                }
                                HorizontalDivider()
                                OutlinedButton(
                                    onClick = vm::ping,
                                    enabled = state.roles.values.any { it.connected },
                                ) {
                                    Text("Ping board")
                                }
                                Text(
                                    "Sends a test command to verify the serial link is alive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── Serial log ────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = { showLog = !showLog }) {
                                Text(
                                    "Serial log${if (state.log.isEmpty()) "" else " (${state.log.size})"}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (showLog) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (state.log.isNotEmpty()) {
                                TextButton(onClick = vm::clearLog) { Text("Clear") }
                            }
                        }
                        AnimatedVisibility(
                            visible = showLog,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            LogView(
                                lines = state.log,
                                modifier = Modifier
                                    .height(220.dp)
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup step card wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupStepCard(
    step: Int,
    title: String,
    subtitle: String,
    complete: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Step badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (complete) Ok else MaterialTheme.colorScheme.primary),
                ) {
                    if (complete) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            step.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Flash / firmware section (Step 1 content)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FlashSection(
    ui: FlashUi,
    devices: List<DeviceOption>,
    onDevice: (String) -> Unit,
    onFirmware: (Firmware) -> Unit,
    onInstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Board picker
        DevicePicker(
            devices = devices,
            selectedId = ui.selectedId,
            enabled = !ui.running,
            onSelect = onDevice,
            placeholder = "Select board (connect via USB-OTG)",
        )

        // Firmware type
        Text(
            "Firmware type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Firmware.entries.forEach { fw ->
                FilterChip(
                    selected = ui.firmware == fw,
                    onClick = { onFirmware(fw) },
                    enabled = !ui.running,
                    label = { Text(fw.short) },
                )
            }
        }

        // Firmware blurb
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                ui.firmware.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp),
            )
        }

        // Install button
        Button(
            onClick = onInstall,
            enabled = !ui.running && devices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(if (ui.running) "Installing…" else "Install firmware")
        }

        // Progress bar
        val p = ui.progress
        if (ui.running) {
            if (p != null) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Status line
        if (ui.status.isNotBlank()) {
            val statusColor = when (ui.ok) {
                true -> Ok
                false -> Err
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                ui.status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Role board card (Step 2 content)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoleBoardCard(
    role: Role,
    ui: RoleUi,
    devices: List<DeviceOption>,
    flashRunning: Boolean,
    onSelect: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val borderColor = when {
        ui.connected -> Ok
        ui.busy -> Warn
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Role title + status dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                ui.connected -> Ok
                                ui.busy -> Warn
                                else -> MaterialTheme.colorScheme.outline
                            }
                        ),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        when (role) {
                            Role.RECEIVER -> "Receiver (capture)"
                            Role.SENDER -> "Sender (transmit)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        when (role) {
                            Role.RECEIVER -> "Listens to your remote. IR receiver on pin D11."
                            Role.SENDER -> "Replays signals. IR LED on pin D3."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Status line (connected, connecting, error...)
            if (ui.status.isNotEmpty()) {
                val statusColor = when {
                    ui.connected -> Ok
                    ui.busy -> Warn
                    ui.status.lowercase().contains("denied") ||
                        ui.status.lowercase().contains("error") ||
                        ui.status.lowercase().contains("failed") -> Err
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    ui.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
            }

            // Device picker (only when not connected)
            if (!ui.connected) {
                DevicePicker(
                    devices = devices,
                    selectedId = ui.selectedId,
                    enabled = !ui.busy,
                    onSelect = onSelect,
                    placeholder = "Select USB device",
                )
            }

            // Connect / Disconnect button
            if (ui.connected) {
                OutlinedButton(
                    onClick = onToggle,
                    enabled = !flashRunning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onToggle,
                    enabled = !ui.busy && !flashRunning && devices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (ui.busy) "Connecting…" else "Connect")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Device picker dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DevicePicker(
    devices: List<DeviceOption>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    placeholder: String = "Select device",
) {
    var open by remember { mutableStateOf(false) }
    val current = devices.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled && devices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                current?.label ?: placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
// Serial log view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogView(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    if (lines.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No log output yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        ) {
            items(lines) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when {
                        line.startsWith("!") -> Err
                        line.startsWith("Done") -> Ok
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
