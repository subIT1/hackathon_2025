package com.example.hackathon.ble

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("8b2c79a1-4c7e-4c49-8b73-7b5e5d1f4c1a")
    val CHARACTERISTIC_MESSAGE_UUID: UUID = UUID.fromString("f6a2f5b1-9a52-4c8e-9b5d-2f4bb5a1e7a2")

    const val MANUFACTURER_ID: Int = 0x00E0
}
