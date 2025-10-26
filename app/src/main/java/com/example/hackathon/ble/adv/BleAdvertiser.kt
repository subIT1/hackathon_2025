package com.example.hackathon.ble.adv

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.example.hackathon.ble.BleConstants.MANUFACTURER_ID
import com.example.hackathon.ble.BleConstants.SERVICE_UUID
import com.example.hackathon.ble.DeviceIdProvider
import com.example.hackathon.model.EventType

class BleAdvertiser(
    private val context: Context,
    private val bluetoothAdapterProvider: () -> BluetoothAdapter?,
    private val log: (EventType, String) -> Unit,
    private val hasAdvertisePermission: () -> Boolean,
) {
    private var advertiseCallback: AdvertiseCallback? = null

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapterProvider()?.bluetoothLeAdvertiser

    fun startAdvertising() {
        if (!hasAdvertisePermission()) {
            log(EventType.PERMISSION, "Missing ADVERTISE permission; cannot advertise")
            return
        }
        val adapter = bluetoothAdapterProvider()
        if (adapter == null || !adapter.isEnabled) {
            log(EventType.ERROR, "Bluetooth adapter not available or disabled; cannot advertise")
            return
        }
        if (adapter.isMultipleAdvertisementSupported.not()) {
            log(EventType.ERROR, "BLE multiple advertisement not supported on this device")
        }
        val adv = advertiser ?: run {
            log(EventType.ERROR, "No advertiser available (device may not support BLE advertising)")
            return
        }
        if (advertiseCallback != null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val idBytes = DeviceIdProvider.getDeviceIdBytes(context)
        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_ID, idBytes)
            .setIncludeDeviceName(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                log(
                    EventType.ADVERTISE,
                    "Advertising started (UUID in ADV, manufacturer data in SCAN_RSP)"
                )
            }

            override fun onStartFailure(errorCode: Int) {
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                    else -> errorCode.toString()
                }
                log(EventType.ERROR, "Advertising failed: $reason ($errorCode)")
                advertiseCallback = null
            }
        }
        try {
            adv.startAdvertising(settings, advData, scanResponse, advertiseCallback)
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException starting advertising: ${se.message}")
            advertiseCallback = null
        }
    }

    fun stopAdvertising() {
        try {
            advertiser?.let { adv ->
                advertiseCallback?.let { adv.stopAdvertising(it) }
                advertiseCallback = null
                log(EventType.ADVERTISE, "Advertising stopped")
            }
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException stopping advertising: ${se.message}")
        }
    }
}
