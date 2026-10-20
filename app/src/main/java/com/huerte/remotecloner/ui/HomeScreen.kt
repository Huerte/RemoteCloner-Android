package com.huerte.remotecloner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.plural

private data class HomeCard(val title: String, val text: String, val tab: Tab)

private val CARDS = listOf(
    HomeCard(
        "Clone remote",
        "Name a key, press it on the original remote and record the signal. Batch mode records a whole list.",
        Tab.CLONE,
    ),
    HomeCard("Library", "Rename, edit, duplicate, import and export remotes and their keys.", Tab.LIBRARY),
    HomeCard("Control panel", "Send keys and run macros made of key sequences and delays.", Tab.CONTROL),
    HomeCard("Connection", "Choose the receiver and sender boards and read the serial log.", Tab.CONNECTION),
    HomeCard(
        "Set up board",
        "Install the firmware on an Arduino Uno or Nano straight from your phone. Found in the Config tab.",
        Tab.CONNECTION,
    ),
)

/** Same dashboard as the Windows Home page: one card per page plus a line with the remote and key counts. */
@Composable
fun HomeScreen(state: RemoteUiState, onOpen: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("IRRemoteCloner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Clone, organise, edit and replay infrared remotes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CARDS.forEach { c ->
            Card(
                onClick = { onOpen(c.tab) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        c.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            if (state.names.isEmpty()) "No remotes yet. Open Clone remote to record your first keys."
            else "${plural(state.names.size, "remote")} with ${plural(state.keyCount, "stored key")}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
