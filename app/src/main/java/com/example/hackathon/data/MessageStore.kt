package com.example.hackathon.data

import android.content.Context
import com.example.hackathon.model.Message
import com.example.hackathon.model.messagesFromJsonArray
import com.example.hackathon.model.messagesToJson
import org.json.JSONArray
import java.io.File
import java.nio.charset.Charset

class MessageStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "messages.json")

    companion object {
        // Retain messages for 48 hours (in milliseconds)
        const val RETENTION_MS: Long = 48L * 60L * 60L * 1000L
    }

    private fun filterRetained(all: List<Message>, now: Long = System.currentTimeMillis()): List<Message> {
        return all.filter { (now - it.timestamp) <= RETENTION_MS }
    }

    @Synchronized
    fun readAll(): List<Message> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText(Charset.forName("UTF-8"))
            val all = if (text.isBlank()) emptyList() else messagesFromJsonArray(JSONArray(text))
            val retained = filterRetained(all)
            if (retained.size != all.size) {
                // Rewrite file to physically purge old entries
                file.writeText(retained.messagesToJson().toString())
            }
            retained
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun append(message: Message) {
        // readAll() already prunes old messages and compacts the file
        val current = readAll().toMutableList()
        current.add(message)
        val arr = current.messagesToJson()
        file.writeText(arr.toString())
    }
}
