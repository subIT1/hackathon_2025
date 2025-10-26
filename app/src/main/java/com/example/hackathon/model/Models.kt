package com.example.hackathon.model

import org.json.JSONArray
import org.json.JSONObject

data class Message(
    val fromId: String,
    val toId: String?,
    val text: String,
    val timestamp: Long,
    val lat: Double? = null,
    val lon: Double? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fromId", fromId)
        .put("toId", toId)
        .put("text", text)
        .put("timestamp", timestamp)
        .apply {
            if (lat != null) put("lat", lat)
            if (lon != null) put("lon", lon)
        }

    companion object {
        fun fromJson(obj: JSONObject): Message = Message(
            fromId = obj.optString("fromId"),
            toId = obj.optString("toId", null),
            text = obj.optString("text"),
            timestamp = obj.optLong("timestamp"),
            lat = if (obj.has("lat")) obj.optDouble("lat") else null,
            lon = if (obj.has("lon")) obj.optDouble("lon") else null
        )
    }
}

enum class EventType { SCAN, ADVERTISE, GATT_SERVER, GATT_CLIENT, PERMISSION, ERROR, MESSAGE_RECEIVED, MESSAGE_SENT }

data class ConnectionEvent(
    val type: EventType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name)
        .put("message", message)
        .put("timestamp", timestamp)

    companion object {
        fun fromJson(obj: JSONObject): ConnectionEvent = ConnectionEvent(
            type = EventType.valueOf(obj.optString("type", EventType.ERROR.name)),
            message = obj.optString("message"),
            timestamp = obj.optLong("timestamp")
        )
    }
}

fun List<Message>.messagesToJson(): JSONArray =
    JSONArray().also { arr -> this.forEach { arr.put(it.toJson()) } }

fun List<ConnectionEvent>.toJson(): JSONArray =
    JSONArray().also { arr -> this.forEach { arr.put(it.toJson()) } }

fun messagesFromJsonArray(arr: JSONArray): List<Message> = buildList {
    for (i in 0 until arr.length()) add(Message.fromJson(arr.getJSONObject(i)))
}

fun eventsFromJsonArray(arr: JSONArray): List<ConnectionEvent> = buildList {
    for (i in 0 until arr.length()) add(ConnectionEvent.fromJson(arr.getJSONObject(i)))
}
