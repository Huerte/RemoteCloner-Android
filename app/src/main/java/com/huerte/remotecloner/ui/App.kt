package com.huerte.remotecloner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.serial.Role
import com.huerte.remotecloner.ui.theme.Muted
import com.huerte.remotecloner.ui.theme.Ok
import com.huerte.remotecloner.ui.theme.Warn
import androidx.compose.ui.graphics.vector.ImageVector

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Default.Home
    Tab.CLONE -> Icons.Default.Add
    Tab.LIBRARY -> Icons.AutoMirrored.Filled.List
    Tab.CONTROL -> Icons.Default.PlayArrow
    Tab.CONNECTION -> Icons.Default.Settings
}

@Composable
fun App(connection: ConnectionViewModel, remotes: RemoteViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val state by remotes.state.collectAsState()
    val conn by connection.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            remotes.consumeMessage()
        }
    }
    LaunchedEffect(state.navigate) {
        state.navigate?.let {
            tab = it
            remotes.consumeNavigate()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            val receiverConnected = conn.roles[Role.RECEIVER]?.connected == true
            val senderConnected = conn.roles[Role.SENDER]?.connected == true
            ConnectionStatusLine(
                receiver = receiverConnected,
                sender = senderConnected,
                // Tap the bar to jump to Setup when boards are not yet fully connected.
                onTap = { if (!(receiverConnected && senderConnected)) tab = Tab.CONNECTION },
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(state, onOpen = { tab = it })
                    Tab.CLONE -> CloneScreen(remotes, state)
                    Tab.LIBRARY -> LibraryScreen(remotes, state)
                    Tab.CONTROL -> ControlScreen(remotes, state)
                    Tab.CONNECTION -> ConnectionScreen(connection)
                }
            }
        }
    }

    state.importing?.let { p ->
        ConfirmDialog(
            title = "Replace remote",
            text = "A remote named \u201c${p.name}\u201d already exists. Replace it?",
            confirmText = "Replace",
            onConfirm = remotes::confirmImport,
            onDismiss = remotes::cancelImport,
        )
    }
}

/**
 * Compact connection status bar. When boards are not fully connected the whole row is tappable so
 * beginners can immediately find the Setup screen without hunting through the nav bar.
 */
@Composable
private fun ConnectionStatusLine(receiver: Boolean, sender: Boolean, onTap: () -> Unit) {
    val text = when {
        receiver && sender -> "Receiver and sender connected"
        receiver -> "Receiver connected \u00b7 sender not connected"
        sender -> "Sender connected \u00b7 receiver not connected"
        else -> "No boards connected \u2014 tap to set up"
    }
    val color = when {
        receiver && sender -> Ok
        receiver || sender -> Warn
        else -> Muted
    }
    val allConnected = receiver && sender
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                enabled = !allConnected,
                onClick = onTap,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (allConnected) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}
