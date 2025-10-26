package com.example.hackathon.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hackathon.ble.BleManager
import com.example.hackathon.model.Message
import com.example.hackathon.model.DisasterCatalog
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val mapViewMarkerCache = java.util.WeakHashMap<MapView, MutableMap<String, Marker>>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMapView(bleManager: BleManager) {
    val ctx = LocalContext.current

    DisposableEffect(ctx) {
        Configuration.getInstance().userAgentValue = ctx.packageName
        onDispose { }
    }

    val messages by bleManager.messages.collectAsState(initial = emptyList())
    val receivedWithCoords = messages.filter { it.toId != null && it.lat != null && it.lon != null }

    val peers by bleManager.peers.collectAsState(initial = emptyList())

    var mapView: MapView? by remember { mutableStateOf(null) }
    var centeredToMessage by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(DisasterCatalog.items.first().code) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setHorizontalMapRepetitionEnabled(true)
                    setVerticalMapRepetitionEnabled(false)

                    val startPoint =
                        receivedWithCoords.lastOrNull()?.let { GeoPoint(it.lat!!, it.lon!!) }
                            ?: GeoPoint(51.1657, 10.4515) // Germany
                    controller.setZoom(if (receivedWithCoords.isNotEmpty()) 13.0 else 6.0)
                    controller.setCenter(startPoint)

                    onResume()
                }.also { mapView = it }
            },
            update = { map ->
                // Incremental overlay updates to avoid full reloads/flicker
                // Keep a map of message-key -> Marker, add/remove only diffs
                val keyFor: (Message) -> String = { m ->
                    val lat = m.lat?.toString() ?: ""
                    val lon = m.lon?.toString() ?: ""
                    "${m.fromId}|${m.timestamp}|$lat,$lon|${m.text}"
                }

                // Remember marker map across updates
                val markerMap = mapViewMarkerCache.getOrPut(map) { mutableMapOf<String, Marker>() }

                val desiredKeys = receivedWithCoords.map(keyFor).toSet()

                // Remove markers that are no longer present
                val toRemove = markerMap.keys.filter { it !in desiredKeys }
                toRemove.forEach { key ->
                    markerMap.remove(key)?.let { map.overlays.remove(it) }
                }

                // Add or update markers for current messages
                for (m in receivedWithCoords) {
                    val key = keyFor(m)
                    if (!markerMap.containsKey(key)) {
                        val marker = Marker(map).apply {
                            position = GeoPoint(m.lat!!, m.lon!!)
                            val label = DisasterCatalog.labelForText(m.text)
                            title = label
                            val codeSuffix = m.text.toIntOrNull()
                                ?.let { if (DisasterCatalog.hasCode(it)) " • #$it" else "" } ?: ""
                            subDescription = "from ${m.fromId.take(6)} • ${formatTs(m)}$codeSuffix"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                        markerMap[key] = marker
                    }
                }

                // Center once when the first message with coords appears
                if (!centeredToMessage && receivedWithCoords.isNotEmpty()) {
                    val latest = receivedWithCoords.last()
                    map.controller.setZoom(13.0)
                    map.controller.setCenter(GeoPoint(latest.lat!!, latest.lon!!))
                    centeredToMessage = true
                }

                map.invalidate()
            }
        )

        FloatingActionButton(
            onClick = { showCategoryDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .semantics { contentDescription = "Add disaster point" }
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
        }

        if (showCategoryDialog) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { showCategoryDialog = false },
                sheetState = sheetState
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Meldung auswählen")
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .padding(top = 8.dp)
                            .selectableGroup()
                    ) {
                        items(DisasterCatalog.items) { item ->
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .selectable(
                                        selected = selectedCode == item.code,
                                        onClick = { selectedCode = item.code },
                                        role = Role.RadioButton
                                    )
                            ) {
                                RadioButton(
                                    selected = selectedCode == item.code,
                                    onClick = { selectedCode = item.code }
                                )
                                androidx.compose.foundation.layout.Column(
                                    Modifier
                                        .padding(start = 12.dp)
                                        .weight(1f)
                                ) {
                                    Text(item.label)
                                }
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCategoryDialog = false }) { Text("Abbrechen") }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            val addresses = peers.map { it.address }.filter { it.isNotBlank() }
                            if (addresses.isEmpty()) {
                                Toast.makeText(ctx, "Keine erreichbaren Peers", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                bleManager.sendMessageToAll(
                                    addresses,
                                    selectedCode.toString()
                                ) { _, _ -> }
                                val sentLabel =
                                    DisasterCatalog.items.firstOrNull { it.code == selectedCode }?.label
                                        ?: "#$selectedCode"
                                Toast.makeText(ctx, "Gesendet: $sentLabel", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            showCategoryDialog = false
                        }) { Text("Senden") }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onPause() }
    }
}

private fun formatTs(m: Message): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(m.timestamp))
}

