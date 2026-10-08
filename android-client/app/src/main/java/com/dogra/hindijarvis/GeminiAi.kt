package com.dogra.hindijarvis

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiAi {
    @Volatile private var active: HttpsURLConnection? = null
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; active?.disconnect() }
    fun reply(key: String, model: String, history: List<Pair<String, String>>): String {
        cancelled = false
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/interactions").openConnection() as HttpsURLConnection
        active = connection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 90000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            val conversation = history.joinToString("\n\n") { (role, text) -> "$role: $text" }
            val body = JSONObject().put("model", model)
                .put("system_instruction", "You are JARVIS. Reply helpfully and briefly in the user's language. Do not claim to have performed phone actions or live web searches. The input contains a conversation; answer the last user message.")
                .put("input", conversation)
                .put("store", false)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (cancelled) error("Reply रोक दी गई")
            if (code !in 200..299) error(when (code) {
                400, 401, 403 -> "API key या model की जाँच करें (HTTP $code)"
                404 -> "यह model उपलब्ध नहीं है; AI settings में model बदलें"
                429 -> "Free quota या rate limit पूरी हुई; बाद में कोशिश करें"
                else -> "Google server error (HTTP $code)"
            })
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (cancelled) error("Reply रोक दी गई")
            val json = JSONObject(response)
            val outputs = json.optJSONArray("steps")
            val answer = buildString {
                if (outputs != null) for (i in 0 until outputs.length()) {
                    val item = outputs.optJSONObject(i) ?: continue
                    if (item.optString("type") == "model_output") {
                        val parts = item.optJSONArray("content") ?: continue
                        for (j in 0 until parts.length()) {
                            val part = parts.optJSONObject(j) ?: continue
                            if (part.optString("type") == "text") append(part.optString("text"))
                        }
                    }
                }
            }.trim()
            require(answer.isNotBlank()) { "Google से text reply नहीं मिली। फिर पूछें।" }
            return answer
        } finally { active = null; connection.disconnect() }
    }
}
