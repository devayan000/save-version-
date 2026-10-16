package com.jarvis.assistant.network

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEV STYLE Backend API client — https://ahona-voice-os.base44.app
 * Covers the official API reference: health, config, key check, user create,
 * premium unlock, license activate/verify and heartbeat.
 * No admin tokens or secrets live inside the app.
 */
object AhonaApi {

    const val BASE_URL = "https://ahona-voice-os.base44.app"
    private const val TIMEOUT_MS = 15_000

    class ApiError(val code: String, override val message: String, val httpStatus: Int) : Exception(message)

    data class KeyCheckResult(val valid: Boolean, val status: String, val message: String, val seatsLeft: Int)
    data class ActivationResult(val status: String, val deviceToken: String?, val message: String)
    data class VerifyResult(val status: String, val maintenanceMode: Boolean)
    data class HeartbeatResult(val status: String, val nextHeartbeatSeconds: Int, val maintenanceMode: Boolean)
    data class ClientError(val title: String, val message: String, val severity: String = "error")
    data class AppConfig(
        val assistantName: String,
        val wakeWord: String,
        val personality: String,
        val voiceEnabled: Boolean,
        val speechRate: Double,
        val maintenanceMode: Boolean,
        val maintenanceMessage: String,
        val minAppVersion: String,
        val heartbeatIntervalSeconds: Int,
        val maxInstallationsPerLicense: Int
    )

    // GET /api/health — liveness probe
    fun health(): JSONObject = request("GET", "/api/health")

    data class VersionCheckResult(
        val status: String,
        val updateAvailable: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val updateUrl: String,
        val message: String
    )

    // GET /api/version?current=1.0.0 — DEV STYLE update gate (every 2 hours + on app open)
    fun versionCheck(current: String): VersionCheckResult {
        val o = request("GET", "/api/version?current=" + Uri.encode(current))
        return VersionCheckResult(
            status = o.optString("status"),
            updateAvailable = o.optBoolean("update_available", false),
            currentVersion = o.optString("current_version"),
            latestVersion = o.optString("latest_version"),
            updateUrl = o.optString("update_url"),
            message = o.optString("message")
        )
    }

    // GET /api/config — public configuration
    fun config(): AppConfig {
        val o = request("GET", "/api/config")
        return AppConfig(
            assistantName = o.optString("assistant_name"),
            wakeWord = o.optString("wake_word"),
            personality = o.optString("personality"),
            voiceEnabled = o.optBoolean("voice_enabled", true),
            speechRate = o.optDouble("speech_rate", 1.0),
            maintenanceMode = o.optBoolean("maintenance_mode", false),
            maintenanceMessage = o.optString("maintenance_message"),
            minAppVersion = o.optString("min_app_version"),
            heartbeatIntervalSeconds = o.optInt("heartbeat_interval_seconds", 300),
            maxInstallationsPerLicense = o.optInt("max_installations_per_license", 1)
        )
    }

    // POST /api/key/check — friendly yes/no before anything is saved
    fun checkKey(licenseKey: String): KeyCheckResult {
        val o = post("/api/key/check", JSONObject().put("license_key", licenseKey.trim()))
        return KeyCheckResult(
            valid = o.optBoolean("valid", false),
            status = o.optString("status"),
            message = o.optString("message"),
            seatsLeft = o.optInt("seats_left", 0)
        )
    }

    // POST /api/user/create — announces the app to the backend
    fun createUser(name: String, deviceModel: String, installationId: String?): JSONObject {
        val body = JSONObject()
            .put("name", name)
            .put("device_model", deviceModel)
            .put("platform", "android")
        if (!installationId.isNullOrBlank()) body.put("installation_id", installationId)
        return post("/api/user/create", body)
    }

    // POST /api/user/{name}/{device}/premium/{key} — key in the URL is the credential
    fun unlockPremium(name: String, deviceModel: String, licenseKey: String, installationId: String?): JSONObject {
        val body = JSONObject().put("platform", "android")
        if (!installationId.isNullOrBlank()) body.put("installation_id", installationId)
        val path = "/api/user/" + Uri.encode(name) + "/" + Uri.encode(deviceModel) +
            "/premium/" + Uri.encode(licenseKey.trim())
        return post(path, body)
    }

    // POST /api/license/activate — trades the key for a one-time device token
    fun activateLicense(licenseKey: String, installationId: String, deviceModel: String, appVersion: String): ActivationResult {
        val o = post("/api/license/activate", JSONObject()
            .put("license_key", licenseKey.trim())
            .put("installation_id", installationId)
            .put("platform", "android")
            .put("device_model", deviceModel)
            .put("app_version", appVersion))
        return ActivationResult(
            status = o.optString("status"),
            deviceToken = o.optString("device_token", "").takeIf { it.isNotBlank() },
            message = o.optString("message")
        )
    }

    // POST /api/license/verify — re-check with the stored device token
    fun verifyLicense(deviceToken: String, installationId: String): VerifyResult {
        val o = post("/api/license/verify", JSONObject().put("installation_id", installationId), deviceToken)
        return VerifyResult(o.optString("status"), o.optBoolean("maintenance_mode", false))
    }

    // POST /api/report/heartbeat — proof of life, carries up to five client errors
    fun heartbeat(deviceToken: String, installationId: String, appVersion: String, deviceModel: String, errors: List<ClientError>): HeartbeatResult {
        val body = JSONObject()
            .put("installation_id", installationId)
            .put("app_version", appVersion)
            .put("device_model", deviceModel)
        if (errors.isNotEmpty()) {
            val arr = JSONArray()
            for (e in errors.take(5)) {
                arr.put(JSONObject().put("title", e.title).put("message", e.message).put("severity", e.severity))
            }
            body.put("errors", arr)
        }
        val o = post("/api/report/heartbeat", body, deviceToken)
        return HeartbeatResult(
            status = o.optString("status"),
            nextHeartbeatSeconds = o.optInt("next_heartbeat_seconds", 300),
            maintenanceMode = o.optBoolean("maintenance_mode", false)
        )
    }

    // ---- internals ----
    private fun request(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) conn.setRequestProperty("x-ahona-token", token)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
            val json = if (text.startsWith("{")) JSONObject(text) else JSONObject()
            if (status !in 200..299) {
                throw ApiError(
                    json.optString("error", "SERVER_ERROR"),
                    json.optString("message", "Request failed (HTTP " + status + ")"),
                    status
                )
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, body: JSONObject, token: String? = null): JSONObject =
        request("POST", path, body, token)
}
