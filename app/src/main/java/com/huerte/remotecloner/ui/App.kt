package com.huerte.remotecloner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private fun Tab.icon(): ImageVector = when (this) {
    Tab.CLONE -> Icons.Default.Add
    Tab.LIBRARY -> Icons.AutoMirrored.Filled.List
    Tab.CONTROL -> Icons.Default.PlayArrow
    Tab.CONNECTION -> Icons.Default.Settings
}

@Composable
fun App(connection: ConnectionViewModel, remotes: RemoteViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.CONNECTION) }
    val state by remotes.state.collectAsState()
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
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.CLONE -> CloneScreen(remotes, state)
                Tab.LIBRARY -> LibraryScreen(remotes, state)
                Tab.CONTROL -> ControlScreen(remotes, state)
                Tab.CONNECTION -> ConnectionScreen(connection)
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
