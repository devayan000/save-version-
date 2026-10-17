package com.jarvis.assistant.automation.whatsapp

import android.app.Notification.Action
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * DEV STYLE — WhatsApp Auto Reply module.
 * Evaluates incoming WhatsApp notifications against user-defined rules and
 * answers them directly through WhatsApp's own remote-input reply action,
 * without opening WhatsApp.
 *
 * Toggle + rules live in their own SharedPreferences so the module works
 * from any notification listener the user granted notification access to.
 */
class WhatsAppAutoReplyManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppAutoReply"
        private const val PREFS = "whatsapp_auto_reply"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RULES = "rules"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        const val DEFAULT_COOLDOWN_MS = 60_000L
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        @Volatile private var instance: WhatsAppAutoReplyManager? = null
        fun get(context: Context): WhatsAppAutoReplyManager =
            instance ?: synchronized(this) {
                instance ?: WhatsAppAutoReplyManager(context.applicationContext).also { instance = it }
            }
    }

    private val throttle = AutoReplyThrottle(getCooldownMsInternal())
    private val lastReplies = mutableMapOf<String, String>()

    fun isEnabled(): Boolean = prefs().getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) = prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getCooldownMs(): Long = prefs().getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)

    fun setCooldownMs(ms: Long) {
        val safe = ms.coerceAtLeast(0L)
        prefs().edit().putLong(KEY_COOLDOWN_MS, safe).apply()
        throttle.cooldownMs = safe
    }

    fun getRules(): List<AutoReplyRule> = decode(prefs().getString(KEY_RULES, null))

    fun setRules(rules: List<AutoReplyRule>) = prefs().edit().putString(KEY_RULES, encode(rules)).apply()

    fun addRule(rule: AutoReplyRule) = setRules(getRules() + rule)

    fun removeRule(id: String) = setRules(getRules().filterNot { it.id == id })

    /**
     * Entry point called from notification listeners. Returns true when an
     * auto reply was sent for this message. [replyAction] is WhatsApp's own
     * direct-reply action carrying the remote input.
     */
    fun handleNotification(sender: String, message: String, replyAction: Action?): Boolean {
        if (!isEnabled()) return false
        val rule = AutoReplyMatcher.match(getRules(), sender, message) ?: return false
        val reply = rule.replyText.replace("{name}", sender.trim()).trim()
        if (reply.isBlank()) return false
        // Never echo our own reply back — breaks auto-reply loops
        if (lastReplies[sender.trim()] == reply) return false
        if (replyAction == null || replyAction.remoteInputs.isNullOrEmpty()) return false
        if (!throttle.allow(sender.trim(), System.currentTimeMillis())) return false
        return try {
            val intent = Intent()
            val results = Bundle()
            results.putCharSequence(replyAction.remoteInputs[0].resultKey, reply)
            RemoteInput.addResultsToIntent(replyAction.remoteInputs, intent, results)
            replyAction.actionIntent?.send(context, 0, intent)
            lastReplies[sender.trim()] = reply
            Log.i(TAG, "Auto-replied to $sender")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto reply failed for $sender: ${e.message}", e)
            throttle.forget(sender.trim())
            false
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getCooldownMsInternal(): Long = prefs().getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)

    private fun encode(rules: List<AutoReplyRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("contactPattern", r.contactPattern)
                    .put("keywordPattern", r.keywordPattern)
                    .put("replyText", r.replyText)
                    .put("enabled", r.enabled)
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String?): List<AutoReplyRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AutoReplyRule(
                    id = o.optString("id"),
                    contactPattern = o.optString("contactPattern"),
                    keywordPattern = o.optString("keywordPattern"),
                    replyText = o.optString("replyText"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }
}
