package com.example.hackathon.ble.util

import com.example.hackathon.model.Message
import org.json.JSONObject
import java.nio.charset.Charset

object MessageCodec {
    fun buildPayload(
        fromId: String,
        text: String,
        timestamp: Long,
        lat: Double?,
        lon: Double?
    ): ByteArray {
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

    fun parseIncoming(raw: String, myId: String): Message? {
        var msg: Message? = null
        try {
            val str = raw.trim()
            if (str.startsWith("{")) {
                val obj = JSONObject(str)
                msg = Message(
                    fromId = obj.optString("fromId"),
                    toId = myId,
                    text = obj.optString("text"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    lat = if (obj.has("lat")) obj.optDouble("lat") else null,
                    lon = if (obj.has("lon")) obj.optDouble("lon") else null
                )
            }
        } catch (_: Exception) {
            // fall through to legacy
        }
        if (msg == null) {
            val parts = raw.split('|', limit = 3)
            if (parts.size >= 3) {
                val fromId = parts[0]
                val text = parts[1]
                val ts = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                msg = Message(
                    fromId = fromId,
                    toId = myId,
                    text = text,
                    timestamp = ts
                )
            }
        }
        return msg
    }
}