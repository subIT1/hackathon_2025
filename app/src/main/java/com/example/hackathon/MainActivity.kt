package com.example.hackathon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.hackathon.ble.BleManager
import com.example.hackathon.ui.screens.AppScaffold
import com.example.hackathon.ui.theme.ResqnetTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (allBlePermissionsGranted()) {
                maybeEnableBluetooth()
                bleManager.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleManager = BleManager(this)

        setContent {
            ResqnetTheme {
                AppScaffold(
                    bleManager = bleManager,
                    requestPermissions = { requestBlePermissions() },
                    enableBluetooth = { maybeEnableBluetooth() }
                )
            }
        }

        if (allBlePermissionsGranted()) {
            maybeEnableBluetooth()
            bleManager.start()
        } else {
            requestBlePermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        if (allBlePermissionsGranted()) {
            maybeEnableBluetooth()
            bleManager.start()
        }
    }

    override fun onStop() {
        super.onStop()
        bleManager.stop()
    }

    private fun maybeEnableBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            val intent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(intent)
        }
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun allBlePermissionsGranted(): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        val perms = requiredPermissions()
        if (perms.isNotEmpty()) permissionLauncher.launch(perms)
    }
}
