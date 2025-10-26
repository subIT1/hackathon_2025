package com.example.hackathon.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.hackathon.ble.BleManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    bleManager: BleManager,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var btOn by remember { mutableStateOf(bleManager.isBluetoothEnabled()) }
    var permsOk by remember { mutableStateOf(bleManager.hasAllRuntimePermissions()) }
    LaunchedEffect(Unit) {
        while (true) {
            val newBt = bleManager.isBluetoothEnabled()
            val newPerms = bleManager.hasAllRuntimePermissions()
            if (newBt != btOn) btOn = newBt
            if (newPerms != permsOk) permsOk = newPerms
            delay(750)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("ResQNet") },
                actions = {
                    if (!permsOk) {
                        IconButton(
                            onClick = requestPermissions,
                            modifier = Modifier.semantics { contentDescription = "Permissions" }) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                        }
                    }
                    if (!btOn) {
                        IconButton(
                            onClick = enableBluetooth,
                            modifier = Modifier.semantics { contentDescription = "Bluetooth" }) {
                            Icon(imageVector = Icons.Outlined.Bluetooth, contentDescription = null)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                    label = { Text("User") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Developer") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> UserMapView(bleManager)
                1 -> ConnectionsView(bleManager, requestPermissions, enableBluetooth)
            }
        }
    }
}
