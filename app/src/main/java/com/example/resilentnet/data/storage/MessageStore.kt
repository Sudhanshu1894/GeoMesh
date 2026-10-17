package com.example.resilentnet.data.storage

import android.content.Context
import android.util.Log
import com.example.resilentnet.data.model.DeliveryStatus
import com.example.resilentnet.data.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe message persistence store.
 * Retains chat history and pending offline messages across app restarts.
 */
class MessageStore(private val context: Context) {
    private val tag = "MessageStore"
    private val gson = Gson()
    private val storageFile = File(context.filesDir, "resilientnet_messages_v1.json")

    // In-memory fast cache of messages: id -> Message
    private val messageMap = ConcurrentHashMap<String, Message>()

    // Lock for disk operations
    private val diskLock = Any()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        synchronized(diskLock) {
            try {
                if (storageFile.exists()) {
                    val json = storageFile.readText()
                    val type = object : TypeToken<List<Message>>() {}.type
                    val list: List<Message>? = gson.fromJson(json, type)
                    list?.forEach { msg ->
                        messageMap[msg.id] = msg
                    }
                    Log.d(tag, "Loaded ${messageMap.size} persisted messages from disk.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load messages from disk: ${e.message}", e)
            }
        }
    }

    private fun persistToDisk() {
        synchronized(diskLock) {
            try {
                val list = messageMap.values.toList()
                val json = gson.toJson(list)
                storageFile.writeText(json)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist messages to disk: ${e.message}", e)
            }
        }
    }

    fun saveOrUpdate(message: Message) {
        messageMap[message.id] = message
        persistToDisk()
    }

    fun getMessage(id: String): Message? = messageMap[id]

    fun hasMessage(id: String): Boolean = messageMap.containsKey(id)

    fun getAllMessages(): List<Message> {
        return messageMap.values.sortedBy { it.timestamp }
    }

    fun updateStatus(id: String, status: DeliveryStatus) {
        val existing = messageMap[id]
        if (existing != null) {
            existing.deliveryStatus = status
            persistToDisk()
        }
    }

    /**
     * Retrieves all messages currently marked as QUEUED, ordered by priority (CRITICAL first).
     */
    fun getPendingQueue(): List<Message> {
        return messageMap.values
            .filter { it.deliveryStatus == DeliveryStatus.QUEUED }
            .sortedWith(
                compareByDescending<Message> { it.priority.ordinal }
                    .thenBy { it.timestamp }
            )
    }

    fun clearAll() {
        messageMap.clear()
        persistToDisk()
    }
}
