package dev.local.smsforwarder.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Keeps a small encrypted cache of recent SMS fingerprints to avoid duplicates. */
class DuplicateGuard(context: Context) {
    private val prefs = SecurePreferences.create(context.applicationContext)
    private val lock = Any()

    fun isDuplicateAndRemember(sender: String, body: String, smsTimestampMillis: Long): Boolean =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val fingerprint = sha256("$sender|$smsTimestampMillis|$body")
            val recent = load()
                .filter { now - it.rememberedAtMillis <= TTL_MILLIS }
                .takeLast(MAX_ITEMS)

            if (recent.any { it.fingerprint == fingerprint }) {
                save(recent)
                true
            } else {
                save(recent + RecentFingerprint(fingerprint, now))
                false
            }
        }

    private fun load(): List<RecentFingerprint> {
        val raw = prefs.getString(KEY_RECENT, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    RecentFingerprint(
                        fingerprint = item.optString("fingerprint"),
                        rememberedAtMillis = item.optLong("rememberedAtMillis"),
                    ),
                )
            }
        }.filter { it.fingerprint.isNotBlank() }
    }

    private fun save(items: List<RecentFingerprint>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("fingerprint", item.fingerprint)
                    .put("rememberedAtMillis", item.rememberedAtMillis),
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private data class RecentFingerprint(
        val fingerprint: String,
        val rememberedAtMillis: Long,
    )

    companion object {
        private const val KEY_RECENT = "recent_sms_fingerprints"
        private const val MAX_ITEMS = 100
        private const val TTL_MILLIS = 6L * 60L * 60L * 1000L
    }
}
