package com.magen.player.repository.subtitle

import android.content.Context
import com.magen.player.preferences.SubtitlesPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Translates downloaded subtitle files through the user's own OpenAI API key. */
class OpenAiSubtitleTranslator(
    private val context: Context,
    private val client: OkHttpClient,
    private val preferences: SubtitlesPreferences,
) {
    suspend fun translateFile(input: File, targetLanguage: String = "Arabic"): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(input.exists()) { "Subtitle file does not exist" }
            val key = preferences.openAiApiKey.get().trim()
            require(key.isNotBlank()) { "OpenAI API key is not set in Subtitle Settings" }
            val source = input.readText(Charsets.UTF_8)
            require(source.isNotBlank()) { "Subtitle file is empty" }
            val prompt = "Translate only the dialogue text in this subtitle file to $targetLanguage. Preserve SRT numbering, timestamps, line breaks, and formatting exactly. Return only valid SRT text, without Markdown fences.\n\n$source"
            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("temperature", 0.1)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }))
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                check(response.isSuccessful) { "OpenAI request failed (${response.code})" }
                val translated = JSONObject(payload).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                    .replace("```srt", "", ignoreCase = true)
                    .replace("```", "")
                    .trim() + "\n"
                val output = File(input.parentFile ?: context.filesDir, input.nameWithoutExtension + ".ar.srt")
                output.writeText(translated, Charsets.UTF_8)
                output
            }
        }
    }
}
