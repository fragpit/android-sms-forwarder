package dev.local.smsforwarder.storage

import android.content.Context
import dev.local.smsforwarder.data.PendingMessage
import org.json.JSONArray
import org.json.JSONObject

/** Stores only undelivered SMS in encrypted preferences for bounded retries. */
class PendingMessageQueue(context: Context) {
    private val prefs = SecurePreferences.create(context.applicationContext)
    private val lock = Any()

    fun enqueue(message: PendingMessage) = synchronized(lock) {
        val next = loadLocked()
            .filterNot { it.id == message.id }
            .filter { System.currentTimeMillis() - it.createdAtMillis <= MAX_AGE_MILLIS }
            .plus(message)
            .takeLast(MAX_QUEUE_SIZE)
        saveLocked(next)
    }

    fun peekAll(): List<PendingMessage> = synchronized(lock) {
        loadLocked().filter { System.currentTimeMillis() - it.createdAtMillis <= MAX_AGE_MILLIS }
    }

    fun markAttempt(id: String) = synchronized(lock) {
        val updated = loadLocked().map {
            if (it.id == id) it.copy(attempts = it.attempts + 1) else it
        }
        saveLocked(updated)
    }

    fun remove(id: String) = synchronized(lock) {
        saveLocked(loadLocked().filterNot { it.id == id })
    }

    fun removeExpiredAndExhausted() = synchronized(lock) {
        val now = System.currentTimeMillis()
        saveLocked(
            loadLocked().filter {
                now - it.createdAtMillis <= MAX_AGE_MILLIS && it.attempts < MAX_ATTEMPTS
            },
        )
    }

    private fun loadLocked(): List<PendingMessage> {
        val raw = prefs.getString(KEY_QUEUE, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PendingMessage(
                        id = item.optString("id"),
                        sender = item.optString("sender"),
                        body = item.optString("body"),
                        receivedAtMillis = item.optLong("receivedAtMillis"),
                        attempts = item.optInt("attempts"),
                        createdAtMillis = item.optLong("createdAtMillis"),
                    ),
                )
            }
        }.filter { it.id.isNotBlank() }
    }

    private fun saveLocked(messages: List<PendingMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("sender", message.sender)
                    .put("body", message.body)
                    .put("receivedAtMillis", message.receivedAtMillis)
                    .put("attempts", message.attempts)
                    .put("createdAtMillis", message.createdAtMillis),
            )
        }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    companion object {
        private const val KEY_QUEUE = "pending_message_queue"
        private const val MAX_ATTEMPTS = 12
        private const val MAX_QUEUE_SIZE = 50
        private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
