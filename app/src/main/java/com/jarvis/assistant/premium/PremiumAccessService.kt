package com.jarvis.assistant.premium

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Premium access key validation. Keys are validated on a trusted backend (Base44 function).
 * Never stores secret credentials client-side. The validation endpoint URL is user-configurable.
 */
object PremiumAccessService {

    private const val WHATSAPP_NUMBER_LOCAL = "01612509753"
    private const val WHATSAPP_COUNTRY_CODE = "880"
    private const val PREFS = "jarvis_premium"
    private const val KEY_VALIDATED = "premium_validated"
    private const val KEY_FEATURES = "premium_features"
    private const val KEY_VALIDATED_AT = "premium_validated_at"
    private const val KEY_VALIDATION_ENDPOINT = "premium_validation_endpoint"

    data class ValidationResult(val valid: Boolean, val features: List<String>, val error: String?, val isNetworkError: Boolean)

    fun setValidationEndpoint(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VALIDATION_ENDPOINT, url.trim()).apply()
    }

    fun getValidationEndpoint(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VALIDATION_ENDPOINT, "").orEmpty()

    fun isValidated(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VALIDATED, false)

    fun getFeatures(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FEATURES, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun hasFeature(ctx: Context, feature: String): Boolean = isValidated(ctx) && getFeatures(ctx).any { it.equals(feature, true) }

    fun validate(ctx: Context, key: String): ValidationResult {
        val endpoint = getValidationEndpoint(ctx)
        if (endpoint.isBlank()) {
            return ValidationResult(false, emptyList(), "Set a validation endpoint in Settings first.", false)
        }
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(JSONObject().put("key", key.trim()).toString().toByteArray()) }
            val code = conn.responseCode
            val body = (conn.inputStream.bufferedReader()?.readText() ?: conn.errorStream?.bufferedReader()?.readText() ?: "")
            conn.disconnect()
            if (code in 200..299) {
                val json = JSONObject(body)
                val valid = json.optBoolean("valid", false)
                if (valid) {
                    val features = json.optJSONArray("features")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: listOf("premium")
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(KEY_VALIDATED, true)
                        .putString(KEY_FEATURES, features.joinToString(","))
                        .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                        .apply()
                    ValidationResult(true, features, null, false)
                } else {
                    ValidationResult(false, emptyList(), json.optString("error", "Invalid key."), false)
                }
            } else {
                val json = runCatching { JSONObject(body) }.getOrNull()
                ValidationResult(false, emptyList(), json?.optString("error") ?: "Server error ($code)", code in 500..599)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ValidationResult(false, emptyList(), "Network timeout. Check your connection.", true)
        } catch (e: java.net.UnknownHostException) {
            ValidationResult(false, emptyList(), "Could not reach the validation server.", true)
        } catch (e: Exception) {
            ValidationResult(false, emptyList(), "Network error: " + e.message, true)
        }
    }

    fun revoke(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VALIDATED, false)
            .putString(KEY_FEATURES, "")
            .putLong(KEY_VALIDATED_AT, 0)
            .apply()
    }

    fun whatsappKeyRequestUrl(): String {
        val stripped = WHATSAPP_NUMBER_LOCAL.removePrefix("0")
        val international = WHATSAPP_COUNTRY_CODE + stripped
        val message = Uri.encode("Hello Ayan Sir! I would like to get a Jarvis AI Premium Access Key.")
        return "https://wa.me/" + international + "?text=" + message
    }

    fun openWhatsAppForKey(ctx: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappKeyRequestUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }
}
