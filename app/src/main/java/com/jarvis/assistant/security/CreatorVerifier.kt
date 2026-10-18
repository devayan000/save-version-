package com.jarvis.assistant.security

import com.jarvis.assistant.JarvisApp

/** A local creator-greeting shortcut, not authentication or a privilege grant. */
object CreatorVerifier {
    private const val MASTER_KEY = "AHONA-I-LOVE-YOU"
    private val prefs get() = JarvisApp.instance.preferences

    private fun normalize(value: String): String = value.trim()
        .lowercase(java.util.Locale.ROOT)
        .normalize(java.text.Normalizer.Form.NFKC)
        .filter { it.isLetterOrDigit() }

    fun isConfigured(): Boolean = creatorSecretPhrase.isNotBlank()

    fun isCreatorPhrase(text: String): Boolean {
        val provided = normalize(text)
        if (provided.isEmpty()) return false
        // DEV STYLE: the master creator key always recognizes Ayan Sir.
        if (provided == normalize(MASTER_KEY)) return true
        val saved = creatorSecretPhrase.trim()
        if (saved.isBlank()) return false
        return provided == normalize(saved)
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
