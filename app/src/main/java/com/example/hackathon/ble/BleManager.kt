package com.example.hackathon.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.hackathon.ble.BleConstants.CHARACTERISTIC_MESSAGE_UUID
import com.example.hackathon.ble.BleConstants.MANUFACTURER_ID
import com.example.hackathon.ble.BleConstants.SERVICE_UUID
import com.example.hackathon.ble.util.LocationProvider
import com.example.hackathon.ble.util.MessageCodec
import com.example.hackathon.data.ConnectionLogStore
import com.example.hackathon.data.MessageStore
import com.example.hackathon.model.ConnectionEvent
import com.example.hackathon.model.EventType
import com.example.hackathon.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class BleManager(private val context: Context) {

    private val incomingWriteBuffers = mutableMapOf<String, ByteArrayOutputStream>()

    private fun bufferKey(device: BluetoothDevice): String {
        return try {
            if (hasConnectPermission()) device.address else "dev_${System.identityHashCode(device)}"
        } catch (se: SecurityException) {
            "dev_${System.identityHashCode(device)}"
        }
    }

    private fun parseAndStoreIncomingMessage(value: ByteArray) {
        try {
            val str = String(value, Charset.forName("UTF-8"))
            val myId = DeviceIdProvider.getDeviceId(context)
            val msg = MessageCodec.parseIncoming(str, myId)
            if (msg != null) {
                scope.launch {
                    messageStore.append(msg)
                    _messages.value = messageStore.readAll()
                }
                log(
                    EventType.MESSAGE_RECEIVED,
                    "From ${msg.fromId}: ${msg.text} (ts=${msg.timestamp}${if (msg.lat != null && msg.lon != null) ", lat=${msg.lat}, lon=${msg.lon}" else ""})"
                )
            } else {
                log(EventType.ERROR, "Malformed message received: $str")
            }
        } catch (e: Exception) {
            log(EventType.ERROR, "Failed to parse incoming message: ${e.message}")
        }
    }

    private val tag = "BleManager"

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var gattServer: BluetoothGattServer? = null

    private var retentionJob: Job? = null

    private val messageStore = MessageStore(context)
    private val logStore = ConnectionLogStore(context)

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _logs = MutableStateFlow(logStore.readAll())
    val logs: StateFlow<List<ConnectionEvent>> = _logs

    private val _messages = MutableStateFlow(messageStore.readAll())
    val messages: StateFlow<List<Message>> = _messages

    private fun log(type: EventType, msg: String) {
        val event = ConnectionEvent(type, msg)
        scope.launch {
            logStore.append(event)
            _logs.value = logStore.readAll()
            Log.d(tag, "[$type] $msg")
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Public helper so UI can reflect current permission status. */
    fun hasAllRuntimePermissions(): Boolean =
        hasScanPermission() && hasConnectPermission() && hasAdvertisePermission() && hasLocationPermission()

    /** Allows UI to append a note into the connection log for debugging taps/state. */
    fun uiLog(message: String) {
        log(EventType.PERMISSION, message)
    }

    /** Human-friendly list of missing permissions for current API level. */
    fun missingPermissionsLabels(): List<String> {
        val labels = mutableListOf<String>()
        if (!hasScanPermission()) labels.add("SCAN")
        if (!hasConnectPermission()) labels.add("CONNECT")
        if (!hasAdvertisePermission()) labels.add("ADVERTISE")
        if (!hasLocationPermission()) labels.add("LOCATION")
        return labels
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        has(android.Manifest.permission.BLUETOOTH_SCAN)
    } else true

    private fun hasConnectPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        has(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else true

    private fun hasAdvertisePermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        has(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    } else true

    private fun hasLocationPermission(): Boolean = if (Build.VERSION.SDK_INT >= 23) {
        has(android.Manifest.permission.ACCESS_FINE_LOCATION) || has(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    } else true

    private fun statusCodeName(code: Int): String = when (code) {
        BluetoothStatusCodes.SUCCESS -> "SUCCESS"
        BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED -> "BT_NOT_ENABLED"
        BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED -> "DEVICE_NOT_BONDED"
        BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "GATT_WRITE_NOT_ALLOWED"
        BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> "MISSING_CONNECT_PERMISSION"
        BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> "PROFILE_NOT_BOUND"
        else -> code.toString()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gps != null && net != null -> if (gps.time >= net.time) gps else net
                gps != null -> gps
                else -> net
            }
        } catch (_: Exception) {
            null
        }
    }

    fun start() {
        setupGattServer()
        startAdvertising()
        startScanning()

        // Start periodic retention pruning so long-running sessions drop expired messages
        retentionJob?.cancel()
        retentionJob = scope.launch {
            // Immediate prune and publish once
            _messages.value = messageStore.readAll()
            while (true) {
                delay(60 * 60 * 1000L) // hourly
                _messages.value = messageStore.readAll()
            }
        }
    }

    fun stop() {
        stopScanning()
        stopAdvertising()
        teardownGattServer()
        retentionJob?.cancel()
        retentionJob = null
    }

    // --- GATT Server ---
    private fun setupGattServer() {
        if (gattServer != null) return
        if (!hasConnectPermission()) {
            log(
                EventType.PERMISSION,
                "Missing BLUETOOTH_CONNECT permission; cannot start GATT server"
            )
            return
        }
        try {
            val server =
                bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
                    override fun onConnectionStateChange(
                        device: BluetoothDevice,
                        status: Int,
                        newState: Int
                    ) {
                        super.onConnectionStateChange(device, status, newState)
                        val state =
                            if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
                        val addr = try {
                            if (hasConnectPermission()) device.address else "(addr hidden)"
                        } catch (se: SecurityException) {
                            "(addr hidden)"
                        }
                        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            val keyPrefix = bufferKey(device)
                            val keys = incomingWriteBuffers.keys.filter { it.startsWith(keyPrefix) }
                            keys.forEach { incomingWriteBuffers.remove(it) }
                        }
                        log(EventType.GATT_SERVER, "Server $state: $addr")
                    }

                    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                        super.onServiceAdded(status, service)
                        val ok = if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "ERROR $status"
                        log(
                            EventType.GATT_SERVER,
                            "Service added callback: $ok uuid=${service.uuid}"
                        )
                    }

                    override fun onCharacteristicWriteRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        characteristic: BluetoothGattCharacteristic,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray
                    ) {
                        if (CHARACTERISTIC_MESSAGE_UUID == characteristic.uuid) {
                            val key = bufferKey(device)
                            try {
                                if (preparedWrite || offset > 0) {
                                    val buf =
                                        incomingWriteBuffers.getOrPut(key) { ByteArrayOutputStream() }
                                    buf.write(value)
                                    if (responseNeeded) gattServer?.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_SUCCESS,
                                        offset,
                                        null
                                    )
                                } else {
                                    parseAndStoreIncomingMessage(value)
                                    if (responseNeeded) gattServer?.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_SUCCESS,
                                        0,
                                        null
                                    )
                                }
                            } catch (e: Exception) {
                                log(
                                    EventType.ERROR,
                                    "onCharacteristicWriteRequest error: ${e.message}"
                                )
                                if (responseNeeded) gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    offset,
                                    null
                                )
                            }
                        } else {
                            if (responseNeeded) gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                                0,
                                null
                            )
                        }
                    }

                    override fun onExecuteWrite(
                        device: BluetoothDevice,
                        requestId: Int,
                        execute: Boolean
                    ) {
                        val key = bufferKey(device)
                        val buf = incomingWriteBuffers.remove(key)
                        if (execute) {
                            if (buf != null) {
                                parseAndStoreIncomingMessage(buf.toByteArray())
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    null
                                )
                            } else {
                                log(EventType.ERROR, "onExecuteWrite: no buffer for device")
                                gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    0,
                                    null
                                )
                            }
                        } else {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                null
                            )
                        }
                    }
                })

            val service =
                BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_MESSAGE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            val added = server.addService(service)
            gattServer = server
            log(EventType.GATT_SERVER, "Server started, service added: $added")
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException starting GATT server: ${se.message}")
        }
    }

    private fun teardownGattServer() {
        try {
            gattServer?.close()
            gattServer = null
            log(EventType.GATT_SERVER, "Server stopped")
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException stopping GATT server: ${se.message}")
        }
    }

    // --- Advertising ---
    private var advertiseCallback: AdvertiseCallback? = null

    private var advPlanCurrent: Int = 0

    private fun startAdvertising() {
        if (!hasAdvertisePermission()) {
            log(EventType.PERMISSION, "Missing ADVERTISE permission; cannot advertise")
            return
        }
        val adapter = bluetoothAdapter
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

    private fun stopAdvertising() {
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

    // --- Scanning ---
    private var scanCallback: ScanCallback? = null

    private fun startScanning() {
        if (!hasScanPermission() || ((Build.VERSION.SDK_INT in 23..30) && !hasLocationPermission())) {
            log(EventType.PERMISSION, "Missing SCAN/LOCATION permission; cannot scan")
            return
        }
        val sc = scanner ?: run {
            log(EventType.ERROR, "No scanner available")
            return
        }
        if (scanCallback != null) return

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                log(EventType.ERROR, "Scan failed: $errorCode")
            }
        }
        try {
            sc.startScan(filters, settings, scanCallback)
            log(EventType.SCAN, "Scanning started")
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException starting scan: ${se.message}")
            scanCallback = null
        }
    }

    private fun stopScanning() {
        try {
            scanner?.let { sc -> scanCallback?.let { sc.stopScan(it) } }
            scanCallback = null
            log(EventType.SCAN, "Scanning stopped")
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException stopping scan: ${se.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val record = result.scanRecord ?: return
        val mfr = record.getManufacturerSpecificData(MANUFACTURER_ID)
        val deviceIdHex = mfr?.joinToString("") { String.format("%02X", it) } ?: ""
        val name = try {
            if (hasConnectPermission()) device.name else null
        } catch (se: SecurityException) {
            null
        }
        val address = try {
            if (hasConnectPermission()) device.address else ""
        } catch (se: SecurityException) {
            ""
        }
        val peer = Peer(address = address, deviceIdHex = deviceIdHex, name = name)
        val list = _peers.value.toMutableList()
        if (address.isNotEmpty()) {
            val existingIdx = list.indexOfFirst { it.address == address }
            if (existingIdx == -1) {
                list.add(peer)
                _peers.value = list
                log(EventType.SCAN, "Found ${peer.address} id=$deviceIdHex name=${peer.name}")
            } else {
                val ex = list[existingIdx]
                if (ex.deviceIdHex != deviceIdHex || ex.name != name) {
                    list[existingIdx] = peer
                    _peers.value = list
                }
            }
        } else {
            if (deviceIdHex.isNotEmpty()) {
                val existingIdx = list.indexOfFirst { it.deviceIdHex == deviceIdHex }
                if (existingIdx == -1) {
                    list.add(peer)
                    _peers.value = list
                    log(
                        EventType.SCAN,
                        "Found peer (address hidden) id=$deviceIdHex name=${peer.name}"
                    )
                } else {
                    val ex = list[existingIdx]
                    if (name != null && ex.name != name) {
                        list[existingIdx] = ex.copy(name = name)
                        _peers.value = list
                    }
                }
            } else {
                log(EventType.SCAN, "Found peer (address hidden)")
            }
        }
    }

    // --- Client write ---
    @SuppressLint("MissingPermission")
    fun sendMessageTo(address: String, text: String, onDone: (Boolean) -> Unit) {
        if (!hasConnectPermission()) {
            log(EventType.PERMISSION, "Missing CONNECT permission; cannot send message")
            onDone(false)
            return
        }
        val adapter = bluetoothAdapter ?: return onDone(false)
        val device = adapter.getRemoteDevice(address)
        val startTs = System.currentTimeMillis()
        val loc = LocationProvider.getLastKnownLocation(context)
        val lat = loc?.latitude
        val lon = loc?.longitude
        try {
            var completed = false
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        } catch (_: SecurityException) {
                        }
                        val requested = try {
                            gatt.requestMtu(185)
                        } catch (_: Exception) {
                            false
                        }
                        if (!requested) {
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        onDone(false)
                        gatt.close()
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    log(
                        EventType.GATT_CLIENT,
                        "MTU changed to ${mtu} on ${gatt.device.address} (status=${status})"
                    )
                    gatt.discoverServices()
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        log(
                            EventType.ERROR,
                            "Service discovery failed on ${gatt.device.address}: ${status}"
                        )
                        onDone(false)
                        gatt.disconnect(); gatt.close();
                        return
                    }
                    val service = gatt.getService(SERVICE_UUID)
                    val ch = service?.getCharacteristic(CHARACTERISTIC_MESSAGE_UUID)
                    if (ch == null) {
                        log(
                            EventType.ERROR,
                            "Target service/characteristic not found on ${gatt.device.address}"
                        )
                        onDone(false)
                        gatt.disconnect(); gatt.close();
                        return
                    }
                    val fromId = DeviceIdProvider.getDeviceId(context)
                    val payload = MessageCodec.buildPayload(fromId, text, startTs, lat, lon)
                    try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val props = ch.properties
                            val supportsWrite =
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                            val supportsWriteNoRsp =
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                            val primaryType = if (supportsWrite) {
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            } else if (supportsWriteNoRsp) {
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            } else null

                            if (primaryType == null) {
                                log(
                                    EventType.ERROR,
                                    "Characteristic not writable on ${gatt.device.address} (props=0x${
                                        props.toString(16)
                                    })"
                                )
                                onDone(false)
                                gatt.disconnect(); gatt.close();
                                return
                            }

                            var code = gatt.writeCharacteristic(
                                ch,
                                payload,
                                primaryType
                            )

                            if (code == BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED &&
                                primaryType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && supportsWriteNoRsp
                            ) {
                                code = gatt.writeCharacteristic(
                                    ch,
                                    payload,
                                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                )
                                if (code == BluetoothStatusCodes.SUCCESS) {
                                    val myId = DeviceIdProvider.getDeviceId(context)
                                    scope.launch {
                                        messageStore.append(
                                            Message(
                                                fromId = myId,
                                                toId = null,
                                                text = text,
                                                timestamp = startTs,
                                                lat = lat,
                                                lon = lon
                                            )
                                        )
                                        _messages.value = messageStore.readAll()
                                    }
                                    log(
                                        EventType.MESSAGE_SENT,
                                        "To ${gatt.device.address}: $text (NO_RESPONSE)"
                                    )
                                    onDone(true)
                                    gatt.disconnect(); gatt.close();
                                    return
                                }
                            } else if (code == BluetoothStatusCodes.SUCCESS &&
                                primaryType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            ) {
                                val myId = DeviceIdProvider.getDeviceId(context)
                                scope.launch {
                                    messageStore.append(
                                        Message(
                                            fromId = myId,
                                            toId = null,
                                            text = text,
                                            timestamp = startTs,
                                            lat = lat,
                                            lon = lon
                                        )
                                    )
                                    _messages.value = messageStore.readAll()
                                }
                                log(
                                    EventType.MESSAGE_SENT,
                                    "To ${gatt.device.address}: $text (NO_RESPONSE)"
                                )
                                onDone(true)
                                gatt.disconnect(); gatt.close();
                                return
                            }

                            if (code != BluetoothStatusCodes.SUCCESS) {
                                log(
                                    EventType.ERROR,
                                    "writeCharacteristic immediate failure ${statusCodeName(code)} ($code) to ${gatt.device.address}"
                                )
                                onDone(false)
                                gatt.disconnect(); gatt.close();
                            }
                        } else {
                            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            ch.value = payload
                            val ok = gatt.writeCharacteristic(ch)
                            if (!ok) {
                                log(
                                    EventType.ERROR,
                                    "writeCharacteristic returned false to ${gatt.device.address}"
                                )
                                onDone(false)
                                gatt.disconnect(); gatt.close();
                            }
                        }
                    } catch (se: SecurityException) {
                        log(EventType.ERROR, "SecurityException writeCharacteristic: ${se.message}")
                        onDone(false)
                        gatt.disconnect(); gatt.close();
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    val success = status == BluetoothGatt.GATT_SUCCESS
                    if (success) {
                        val myId = DeviceIdProvider.getDeviceId(context)
                        scope.launch {
                            messageStore.append(
                                Message(
                                    fromId = myId,
                                    toId = null,
                                    text = text,
                                    timestamp = startTs,
                                    lat = lat,
                                    lon = lon
                                )
                            )
                            _messages.value = messageStore.readAll()
                        }
                        log(EventType.MESSAGE_SENT, "To ${gatt.device.address}: $text")
                    } else {
                        log(EventType.ERROR, "Write failed to ${gatt.device.address}: $status")
                    }
                    onDone(success)
                    gatt.disconnect(); gatt.close()
                }
            })
        } catch (se: SecurityException) {
            log(EventType.ERROR, "SecurityException connectGatt: ${se.message}")
            onDone(false)
        }
    }

    private fun buildMessagePayload(
        text: String,
        timestamp: Long,
        lat: Double?,
        lon: Double?
    ): ByteArray {
        val fromId = DeviceIdProvider.getDeviceId(context)
        val obj = JSONObject()
            .put("fromId", fromId)
            .put("text", text)
            .put("timestamp", timestamp)
            .apply {
                if (lat != null) put("lat", lat)
                if (lon != null) put("lon", lon)
            }
        return obj.toString().toByteArray(Charset.forName("UTF-8"))
    }

    fun sendMessageToAll(
        addresses: List<String>,
        text: String,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val distinct = addresses.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) {
            onProgress(0, 0)
            return
        }
        fun step(index: Int) {
            if (index >= distinct.size) {
                onProgress(distinct.size, distinct.size)
                return
            }
            val addr = distinct[index]
            sendMessageTo(addr, text) {
                onProgress(index + 1, distinct.size)
                scope.launch {
                    delay(300)
                    step(index + 1)
                }
            }
        }
        step(0)
    }
}
