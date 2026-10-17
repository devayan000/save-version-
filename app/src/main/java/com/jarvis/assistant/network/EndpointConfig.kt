package com.jarvis.assistant.network

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** No built-in chat endpoint or shared key. Each installation supplies its own. */
object EndpointConfig {
    fun httpEndpoint(apiKey: String, configured: String): String? = configured.trim()
        .takeIf { it.startsWith("https://", true) && validationError(it) == null }

    fun validationError(endpoint: String): String? {
        if (endpoint.isBlank()) return null // A personal Gemini key may be used instead.
        val uri = runCatching { URI(endpoint.trim().replace("YOUR_PROMPT_HERE", "test").replace("{prompt}", "test")) }.getOrNull()
        return if (endpoint.length > 4096 || uri?.host.isNullOrBlank() ||
            uri?.scheme?.lowercase(Locale.ROOT) !in setOf("https", "wss") ||
            uri?.rawUserInfo != null || uri?.rawFragment != null) {
            "Enter your own HTTPS chat endpoint or Gemini WSS relay."
        } else null
    }

    fun promptUrl(endpoint: String, prompt: String): String {
        require(endpoint.isNotBlank() && validationError(endpoint) == null && endpoint.startsWith("https://", true)) { "Configure your own HTTPS endpoint in Settings." }
        require(prompt.isNotBlank() && prompt.length <= 8000) { "Please use 1 to 8000 characters." }
        val encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        if (endpoint.contains("YOUR_PROMPT_HERE")) return endpoint.replace("YOUR_PROMPT_HERE", encoded)
        if (endpoint.contains("{prompt}")) return endpoint.replace("{prompt}", encoded)
        val uri = URI(endpoint)
        val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() && it.substringBefore('=') != "prompt" }
        return endpoint.substringBefore('?') + "?" + (query + "prompt=".plus(encoded)).joinToString("&")
    }

    fun usesGet(endpoint: String): Boolean = endpoint.contains("YOUR_PROMPT_HERE") || endpoint.contains("{prompt}") ||
        URI(endpoint).rawQuery.orEmpty().split('&').any { it.substringBefore('=') == "prompt" }

    fun speechLocale(language: String, text: String = ""): Locale {
        val normalized = language.trim().lowercase(Locale.ROOT)
        return when {
            normalized.contains("bengali") || normalized in setOf("bn", "bn-bd", "bangla", "বাংলা") -> Locale.forLanguageTag("bn-BD")
            normalized.contains("hindi") || normalized in setOf("hi", "hi-in", "हिन्दी", "हिंदी") -> Locale.forLanguageTag("hi-IN")
            normalized.startsWith("auto") || normalized.isEmpty() -> when {
                text.any { it in '\u0980'..'\u09ff' } -> Locale.forLanguageTag("bn-BD")
                text.any { it in '\u0900'..'\u097f' } -> Locale.forLanguageTag("hi-IN")
                else -> Locale.getDefault()
            }
            normalized.contains("english") -> Locale.US
            else -> Locale.forLanguageTag(language).takeIf { it.language.length in 2..3 } ?: Locale.getDefault()
        }
    }
}
