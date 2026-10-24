package com.huerte.remotecloner.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huerte.remotecloner.data.plural
import com.huerte.remotecloner.ui.theme.Accent
import com.huerte.remotecloner.ui.theme.Ok

// Each shortcut card on the home screen.
private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val tab: Tab,
)

private val ACTIONS = listOf(
    QuickAction(
        Icons.Default.Add,
        "Record keys",
        "Point your remote at the receiver and capture button signals.",
        Tab.CLONE,
    ),
    QuickAction(
        Icons.AutoMirrored.Filled.List,
        "My remotes",
        "Browse, edit, import and export your saved remotes.",
        Tab.LIBRARY,
    ),
    QuickAction(
        Icons.Default.PlayArrow,
        "Send & control",
        "Fire individual keys or run multi-step macros.",
        Tab.CONTROL,
    ),
    QuickAction(
        Icons.Default.Settings,
        "Setup boards",
        "Install firmware and connect your Arduino over USB.",
        Tab.CONNECTION,
    ),
)

@Composable
fun HomeScreen(state: RemoteUiState, onOpen: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val hasRemotes = state.names.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        // ── App title ─────────────────────────────────────────────────────
        Text(
            "IR Remote Cloner",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            "Clone, save and replay infrared remote controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        // ── Stats pill (when library has data) ────────────────────────────
        if (hasRemotes) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "${plural(state.names.size, "remote")} · ${plural(state.keyCount, "key")}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── "Getting started" hint (only when empty) ──────────────────────
        if (!hasRemotes) {
            GettingStartedBanner(onOpen)
            Spacer(Modifier.height(20.dp))
        }

        // ── Quick action grid (2 columns) ──────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ACTIONS.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { action ->
                        QuickActionCard(
                            action = action,
                            onClick = { onOpen(action.tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // If the row has only 1 item, balance with an empty weight
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Getting started banner (first-run only)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GettingStartedBanner(onOpen: (Tab) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Getting started",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val steps = listOf(
                "1. Open Setup boards and install firmware on your Arduino.",
                "2. Connect the board via USB-OTG and tap Connect.",
                "3. Go to Record keys, name a button, and press it on your remote.",
            )
            steps.forEach { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onOpen(Tab.CONNECTION) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Open Setup boards")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick action card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                action.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                action.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
