package dev.local.smsforwarder.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Sends messages through Telegram Bot API over HTTPS without logging message bodies. */
class TelegramClient(
    private val httpClient: OkHttpClient = defaultClient(),
) {
    suspend fun sendMessage(token: String, chatId: String, text: String): TelegramSendResult =
        withContext(Dispatchers.IO) {
            if (token.isBlank() || chatId.isBlank()) {
                return@withContext TelegramSendResult.Failure("Telegram token or chat id is empty")
            }

            val requestBody = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("disable_web_page_preview", "true")
                .build()
            val request = Request.Builder()
                .url("https://api.telegram.org/bot${token.trim()}/sendMessage")
                .post(requestBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext TelegramSendResult.Failure(
                            "Telegram API returned HTTP ${response.code}",
                        )
                    }

                    val ok = runCatching {
                        JSONObject(responseBody).optBoolean("ok", false)
                    }.getOrDefault(false)

                    if (ok) {
                        TelegramSendResult.Success
                    } else {
                        TelegramSendResult.Failure("Telegram API returned ok=false")
                    }
                }
            } catch (error: IOException) {
                TelegramSendResult.Failure(error.message ?: "Network error")
            }
        }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
