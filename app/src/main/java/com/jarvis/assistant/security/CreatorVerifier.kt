package com.jarvis.assistant.security

import com.jarvis.assistant.JarvisApp

/** A local creator-greeting shortcut, not authentication or a privilege grant. */
object CreatorVerifier {
    private val prefs get() = JarvisApp.instance.preferences

    fun isConfigured(): Boolean = creatorSecretPhrase.isNotBlank()

    fun isCreatorPhrase(text: String): Boolean {
        val saved = creatorSecretPhrase.trim()
        if (saved.isBlank()) return false
        fun normalize(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        val normalized = normalize(saved)
        return normalized.isNotEmpty() && normalize(text) == normalized
    }

    var creatorSecretPhrase: String
        get() = prefs.creatorSecretPhrase
        set(value) { prefs.creatorSecretPhrase = value }

    var creatorName: String
        get() = prefs.creatorName
        set(value) { prefs.creatorName = value }

    var creatorEmail: String
        get() = prefs.creatorEmail
        set(value) { prefs.creatorEmail = value }

    fun greeting(): String = "Hello " + creatorName.ifBlank { "Ayan Sir" } + "! You are my creator. I am at your service."
}
