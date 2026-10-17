package com.jarvis.assistant.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** GET prompt URLs and OpenAI-compatible POST; only the user's own optional key is sent. */
class HttpChatClient {
    companion object {
        private const val MAX_REPLY_BYTES = 65536L
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    suspend fun ask(endpoint: String, prompt: String, apiKey: String = "", model: String = ""): String = suspendCancellableCoroutine { continuation ->
        val request = runCatching {
            require(endpoint.isNotBlank() && EndpointConfig.validationError(endpoint) == null && endpoint.startsWith("https://", true)) { "Configure your own HTTPS endpoint in Settings." }
            require(prompt.isNotBlank() && prompt.length <= 8000)
            val builder = Request.Builder().header("Accept", "text/plain, application/json")
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer " + apiKey)
            if (EndpointConfig.usesGet(endpoint)) builder.url(EndpointConfig.promptUrl(endpoint, prompt)).get()
            else {
                val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                val payload = JSONObject().put("messages", messages).put("stream", false)
                if (model.isNotBlank()) payload.put("model", model)
                builder.url(endpoint).post(payload.toString().toRequestBody("application/json".toMediaType()))
            }
            builder.build()
        }.getOrElse { continuation.resumeWithException(it); return@suspendCancellableCoroutine }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "Custom endpoint returned HTTP " + it.code }
                    val body = checkNotNull(it.body) { "Empty endpoint response." }
                    check(body.contentLength() <= MAX_REPLY_BYTES) { "Response too large." }
                    val source = body.source()
                    source.request(MAX_REPLY_BYTES + 1)
                    check(source.buffer.size <= MAX_REPLY_BYTES) { "Response too large." }
                    decodeReply(source.readUtf8())
                } }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    private fun decodeReply(body: String): String {
        val text = body.trim()
        check(text.isNotEmpty() && !text.startsWith("<")) { "Endpoint did not return a chat response." }
        if (!text.startsWith("{") && !text.startsWith("\"")) return text
        val value = JSONTokener(text).nextValue()
        if (value is String) return value.trim().also { check(it.isNotEmpty()) }
        val json = value as? JSONObject ?: error("Unsupported response format.")
        check(!json.has("error") || json.isNull("error") || json.optString("error").isBlank()) { "Endpoint rejected this request. Check your settings." }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val compatible = choice?.optJSONObject("message")?.optString("content").orEmpty().ifBlank { choice?.optString("text").orEmpty() }
        if (compatible.isNotBlank()) return compatible.trim()
        for (obj in listOfNotNull(json, json.optJSONObject("data"))) {
            for (key in listOf("reply", "response", "answer", "text", "message", "content", "data")) {
                val reply = obj.opt(key)
                if (reply is String && reply.isNotBlank()) return reply.trim()
            }
        }
        error("Endpoint returned JSON without a text reply.")
    }
}
