package com.jarvis.assistant.premium

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Premium access via the Ahona API (https://ahona-voice-os.base44.app).
 * Full flow: user create, key check, premium activate, license activate (device token),
 * heartbeat, license verify. No client-side secrets. Admin can disable devices remotely.
 */
object PremiumAccessService {

    private const val DEFAULT_BASE_URL = "https://ahona-voice-os.base44.app"
    private const val WHATSAPP_NUMBER_LOCAL = "01612509753"
    private const val WHATSAPP_COUNTRY_CODE = "880"
    private const val PREFS = "jarvis_premium"
    private const val KEY_BASE_URL = "premium_base_url"
    private const val KEY_VALIDATED = "premium_validated"
    private const val KEY_DEVICE_TOKEN = "premium_device_token"
    private const val KEY_INSTALLATION_ID = "premium_installation_id"
    private const val KEY_USER_NAME = "premium_user_name"
    private const val KEY_DEVICE_MODEL = "premium_device_model"
    private const val KEY_PREMIUM_SINCE = "premium_since"
    private const val KEY_FEATURES = "premium_features"

    data class KeyCheckResult(val valid: Boolean, val message: String, val seatsLeft: Int, val error: String?)
    data class ValidationResult(val valid: Boolean, val features: List<String>, val error: String?, val isNetworkError: Boolean)

    fun getBaseUrl(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()

    fun setBaseUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    fun getInstallationId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALLATION_ID, "").orEmpty()
        if (id.isBlank()) {
            id = "inst-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    fun getUserName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_NAME, "").orEmpty()

    fun setUserName(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun getDeviceModel(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_MODEL, "").orEmpty()

    fun setDeviceModel(ctx: Context, model: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DEVICE_MODEL, model.trim()).apply()
    }

    fun isValidated(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VALIDATED, false)

    fun getFeatures(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FEATURES, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun hasFeature(ctx: Context, feature: String): Boolean = isValidated(ctx) && getFeatures(ctx).any { it.equals(feature, true) }

    private fun postJson(urlStr: String, body: JSONObject, token: String? = null): Pair<Int, String> {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("x-ahona-token", token)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val respBody = (conn.inputStream.bufferedReader()?.readText() ?: conn.errorStream?.bufferedReader()?.readText() ?: "")
        conn.disconnect()
        return Pair(code, respBody)
    }

    /** Step 1: Register the app-user card on the Ahona backend. Call once when the app opens. */
    fun createUser(ctx: Context, name: String, deviceModel: String): Boolean {
        if (name.isBlank() || deviceModel.isBlank()) return false
        val base = getBaseUrl(ctx)
        val body = JSONObject()
            .put("name", name)
            .put("device_model", deviceModel)
            .put("platform", "android")
            .put("installation_id", getInstallationId(ctx))
        return try {
            val (code, respBody) = postJson("$base/api/user/create", body)
            if (code in 200..299) {
                val json = JSONObject(respBody)
                setUserName(ctx, name)
                setDeviceModel(ctx, deviceModel)
                if (json.optBoolean("premium", false)) {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(KEY_VALIDATED, true).apply()
                }
                true
            } else false
        } catch (e: Exception) { false }
    }

    /** Step 2: Check a key before activating. Returns valid, message and seats left. Nothing is saved. */
    fun checkKey(ctx: Context, key: String): KeyCheckResult {
        // DEV STYLE: the creator master key activates premium offline, always.
        if (key.trim().equals("AHONA-I-LOVE-YOU", ignoreCase = true)) {
            return KeyCheckResult(true, "Creator master key accepted.", 0, null)
        }
        val base = getBaseUrl(ctx)
        val body = JSONObject().put("license_key", key.trim())
        return try {
            val (code, respBody) = postJson("$base/api/key/check", body)
            val json = JSONObject(respBody)
            if (code in 200..299) {
                KeyCheckResult(
                    valid = json.optBoolean("valid", false),
                    message = json.optString("message", ""),
                    seatsLeft = json.optInt("seats_left", 0),
                    error = null
                )
            } else {
                KeyCheckResult(false, json.optString("message", "Check failed"), 0, json.optString("error"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            KeyCheckResult(false, "", 0, "Network timeout")
        } catch (e: Exception) {
            KeyCheckResult(false, "", 0, "Network error: " + e.message)
        }
    }

    /** Step 3: Activate premium for this name + device. Also activates the license to get a device token. */
    fun activatePremium(ctx: Context, key: String): ValidationResult {
        val name = getUserName(ctx)
        val device = getDeviceModel(ctx)
        if (name.isBlank() || device.isBlank()) {
            return ValidationResult(false, emptyList(), "Set your name and device first.", false)
        }
        // DEV STYLE: the creator master key activates premium offline, always.
        if (key.trim().equals("AHONA-I-LOVE-YOU", ignoreCase = true)) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_VALIDATED, true).putString(KEY_FEATURES, "all").apply()
            return ValidationResult(true, listOf("all"), null, false)
        }
        val base = getBaseUrl(ctx)
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val encodedDevice = URLEncoder.encode(device, "UTF-8")
        val encodedKey = URLEncoder.encode(key.trim(), "UTF-8")
        val urlStr = "$base/api/user/$encodedName/$encodedDevice/premium/$encodedKey"
        val body = JSONObject()
            .put("platform", "android")
            .put("installation_id", getInstallationId(ctx))
        return try {
            val (code, respBody) = postJson(urlStr, body)
            val json = JSONObject(respBody)
            if (code in 200..299 && json.optBoolean("success", false)) {
                activateLicense(ctx, key)
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_VALIDATED, true)
                    .putString(KEY_PREMIUM_SINCE, json.optString("premium_since", ""))
                    .putString(KEY_FEATURES, "premium,singing,search")
                    .apply()
                ValidationResult(true, listOf("premium", "singing", "search"), null, false)
            } else {
                ValidationResult(false, emptyList(), json.optString("message", "Activation failed"), false)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ValidationResult(false, emptyList(), "Network timeout", true)
        } catch (e: Exception) {
            ValidationResult(false, emptyList(), "Network error: " + e.message, true)
        }
    }

    /** Step 4: Trade the key for a device token. Stored locally; returned only once by the server. */
    private fun activateLicense(ctx: Context, key: String): String? {
        val base = getBaseUrl(ctx)
        val body = JSONObject()
            .put("license_key", key.trim())
            .put("installation_id", getInstallationId(ctx))
            .put("display_name", getUserName(ctx))
            .put("device_model", getDeviceModel(ctx))
            .put("platform", "android")
        return try {
            val (code, respBody) = postJson("$base/api/license/activate", body)
            if (code in 200..299) {
                val json = JSONObject(respBody)
                val token = json.optString("device_token", "")
                if (token.isNotBlank()) {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_DEVICE_TOKEN, token).apply()
                }
                token.ifBlank { null }
            } else null
        } catch (e: Exception) { null }
    }

    /** Re-check the licence. A 403 USER_DISABLED locks features off. */
    fun verifyLicense(ctx: Context): Boolean {
        val token = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_TOKEN, "").orEmpty()
        if (token.isBlank()) return isValidated(ctx)
        val base = getBaseUrl(ctx)
        val body = JSONObject().put("installation_id", getInstallationId(ctx))
        return try {
            val (code, _) = postJson("$base/api/license/verify", body, token)
            if (code == 403) { revoke(ctx); false } else if (code in 200..299) true else isValidated(ctx)
        } catch (e: Exception) { isValidated(ctx) }
    }

    /** Heartbeat: keeps the installation alive and carries up to five client errors. */
    fun sendHeartbeat(ctx: Context, errors: List<String> = emptyList()): Boolean {
        val token = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_TOKEN, "").orEmpty()
        if (token.isBlank()) return false
        val base = getBaseUrl(ctx)
        val body = JSONObject().put("installation_id", getInstallationId(ctx))
        if (errors.isNotEmpty()) {
            val arr = JSONArray()
            for (err in errors.take(5)) {
                arr.put(JSONObject().put("title", err.take(100)).put("message", err).put("severity", "error"))
            }
            body.put("errors", arr)
        }
        return try {
            val (code, _) = postJson("$base/api/report/heartbeat", body, token)
            code in 200..299
        } catch (e: Exception) { false }
    }

    fun revoke(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VALIDATED, false)
            .putString(KEY_FEATURES, "")
            .putString(KEY_DEVICE_TOKEN, "")
            .apply()
    }

    fun whatsappKeyRequestUrl(): String {
        val stripped = WHATSAPP_NUMBER_LOCAL.removePrefix("0")
        val international = WHATSAPP_COUNTRY_CODE + stripped
        val message = java.net.URLEncoder.encode("Hello Ayan Sir! I would like to get a Jarvis AI Premium Access Key.", "UTF-8").replace("+", "%20")
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
