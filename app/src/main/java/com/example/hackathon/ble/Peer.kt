package com.example.hackathon.ble

data class Peer(
    val address: String,
    val deviceIdHex: String,
    val name: String? = null
)
