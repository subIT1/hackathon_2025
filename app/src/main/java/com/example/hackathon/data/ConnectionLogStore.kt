package com.example.hackathon.data

import android.content.Context
import com.example.hackathon.model.ConnectionEvent
import com.example.hackathon.model.eventsFromJsonArray
import com.example.hackathon.model.toJson
import org.json.JSONArray
import java.io.File
import java.nio.charset.Charset

class ConnectionLogStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "connection_log.json")

    @Synchronized
    fun readAll(): List<ConnectionEvent> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText(Charset.forName("UTF-8"))
            if (text.isBlank()) emptyList() else eventsFromJsonArray(JSONArray(text))
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun append(event: ConnectionEvent) {
        val current = readAll().toMutableList()
        current.add(event)
        file.writeText(current.toJson().toString())
    }
}
