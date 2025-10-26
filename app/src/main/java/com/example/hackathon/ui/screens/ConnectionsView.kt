package com.example.hackathon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hackathon.ble.BleManager
import com.example.hackathon.ble.DeviceIdProvider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.hackathon.model.DisasterCatalog

@Composable
fun ConnectionsView(
    bleManager: BleManager,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit
) {
    val peers by bleManager.peers.collectAsState(initial = emptyList())
    val logs by bleManager.logs.collectAsState(initial = emptyList())
    val messages by bleManager.messages.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val deviceId = remember { DeviceIdProvider.getDeviceId(ctx) }

    var selectedCode by rememberSaveable { mutableIntStateOf(DisasterCatalog.items.first().code) }
        var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Your ID: ${deviceId.take(12)}…", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                bleManager.uiLog("UI: Grant Permissions tapped")
                requestPermissions()
            }) { Text("Grant Permissions") }
            OutlinedButton(onClick = {
                bleManager.uiLog("UI: Enable Bluetooth tapped")
                enableBluetooth()
            }) { Text("Enable BT") }
        }
        val btOn = bleManager.isBluetoothEnabled()
        val permsOk = bleManager.hasAllRuntimePermissions()
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.AssistChip(onClick = {}, label = { Text(if (btOn) "BT ON" else "BT OFF") })
            androidx.compose.material3.AssistChip(onClick = {}, label = { Text(if (permsOk) "Permissions OK" else "Perms Missing") })
            androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Peers ${peers.size}") })
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedLabel = DisasterCatalog.items.firstOrNull { it.code == selectedCode }?.label ?: "#$selectedCode"
                    Text(text = "$selectedLabel (#$selectedCode)")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DisasterCatalog.items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                selectedCode = item.code
                                expanded = false
                            }
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val addresses = peers.map { it.address }.filter { it.isNotBlank() }
                    if (addresses.isEmpty()) {
                        android.widget.Toast.makeText(
                            ctx,
                            "No reachable peers yet. Grant Bluetooth Connect and keep the app open.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        bleManager.sendMessageToAll(addresses, selectedCode.toString()) { _, _ -> }
                    }
                }
            ) { Text("Send to all") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Discovered peers:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(peers) { peer ->
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                peer.name ?: "(no name)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${peer.address} • id=${peer.deviceIdHex}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            enabled = peer.address.isNotBlank(),
                            onClick = {
                                bleManager.sendMessageTo(peer.address, selectedCode.toString()) { }
                            }
                        ) { Text("Send") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Messages:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.height(120.dp)) {
            items(messages.takeLast(20)) { m ->
                val dir = if (m.toId == null) "sent" else "recv"
                val gps = if (dir == "recv" && m.lat != null && m.lon != null) {
                    val latStr = String.format(java.util.Locale.US, "%.5f", m.lat)
                    val lonStr = String.format(java.util.Locale.US, "%.5f", m.lon)
                    " • lat=$latStr, lon=$lonStr"
                } else ""
                val label = DisasterCatalog.labelForText(m.text)
                val codeSuffix = m.text.toIntOrNull()?.let { if (DisasterCatalog.hasCode(it)) " • #$it" else "" } ?: ""
                Text("${dir.uppercase()} • ${m.timestamp} • ${m.fromId.take(6)}: $label$codeSuffix$gps")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Connection log:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.height(120.dp)) {
            items(logs.takeLast(30)) { e ->
                Text("[${e.type}] ${e.message}")
            }
        }
    }
}
