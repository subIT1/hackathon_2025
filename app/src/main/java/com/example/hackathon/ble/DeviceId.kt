package com.example.hackathon.ble

import android.content.Context
import android.provider.Settings
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Provides a stable device ID for P2P identification.
 * Prefers a generated UUID stored in SharedPreferences, falling back to ANDROID_ID.
 */
object DeviceIdProvider {
    private const val PREFS = "p2p_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrEmpty()) {
            val seed =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            id = if (!seed.isNullOrEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
                val bb = ByteBuffer.wrap(digest.copyOf(16))
                val most = bb.long
                val least = bb.long
                UUID(most, least).toString()
            } else {
                UUID.randomUUID().toString()
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    /** 16 bytes derived from the ID string, suitable for manufacturer data. */
    fun getDeviceIdBytes(context: Context): ByteArray {
        val id = getDeviceId(context)
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return digest.copyOf(16)
    }
}
