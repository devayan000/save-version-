package com.jarvis.assistant.backend

import android.content.Context
import android.os.Build
import com.jarvis.assistant.network.AhonaApi
import com.jarvis.assistant.security.InstallationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * DEV STYLE licence flow: register → premium → activate → verify → heartbeat.
 * Blocking network calls — run them on a background thread or in appScope.
 * The device token never leaves the app; no admin tokens are stored here.
 */
object LicenseManager {

    data class ActivationOutcome(val success: Boolean, val message: String, val status: String = "")

    private fun deviceModel(): String = (Build.MODEL ?: "unknown").replace("/", "-")

    // Announces name + device to the backend once per launch.
    // Returns true when a user record was actually created by this launch.
    fun registerOnLaunch(context: Context, ownerName: String = "DEV STYLE"): Boolean {
        return runCatching {
            AhonaApi.createUser(ownerName, deviceModel(), InstallationManager.installationId(context))
            InstallationManager.setRegistered(context, true)
            true
        }.getOrDefault(false)
    }

    fun checkKey(licenseKey: String): AhonaApi.KeyCheckResult = AhonaApi.checkKey(licenseKey)

    // check → premium → activate → store the one-time device token.
    fun activate(context: Context, licenseKey: String): ActivationOutcome {
        val key = licenseKey.trim()
        if (key.isEmpty()) return ActivationOutcome(false, "Enter a license key first")
        return try {
            val check = AhonaApi.checkKey(key)
            if (!check.valid) return ActivationOutcome(false, check.message.ifBlank { "Invalid key" }, check.status)
            val inst = InstallationManager.installationId(context)
            AhonaApi.unlockPremium("DEV STYLE", deviceModel(), key, inst)
            val activation = AhonaApi.activateLicense(key, inst, deviceModel(), appVersion(context))
            if (activation.status == "active" && activation.deviceToken != null) {
                InstallationManager.saveActivation(context, key, activation.deviceToken)
                ActivationOutcome(true, activation.message.ifBlank { "Premium is active" }, activation.status)
            } else {
                InstallationManager.setPremium(context, false)
                ActivationOutcome(false, activation.message.ifBlank { "Activation failed (" + activation.status + ")" }, activation.status)
            }
        } catch (e: AhonaApi.ApiError) {
            InstallationManager.setPremium(context, false)
            ActivationOutcome(false, e.message, e.code)
        } catch (e: Exception) {
            ActivationOutcome(false, "Connection failed — check your internet")
        }
    }

    // Live re-check — a 403 (USER_DISABLED) switches licensed features off.
    fun verify(context: Context): Boolean {
        val token = InstallationManager.deviceToken(context) ?: return InstallationManager.isPremium(context)
        return try {
            val v = AhonaApi.verifyLicense(token, InstallationManager.installationId(context))
            val ok = v.status == "active"
            InstallationManager.setPremium(context, ok)
            ok
        } catch (e: AhonaApi.ApiError) {
            if (e.httpStatus == 403) InstallationManager.setPremium(context, false)
            false
        } catch (e: Exception) {
            false
        }
    }

    // One heartbeat — returns null when no token is stored or the call fails.
    fun heartbeatOnce(context: Context, errors: List<AhonaApi.ClientError> = emptyList()): AhonaApi.HeartbeatResult? {
        val token = InstallationManager.deviceToken(context) ?: return null
        return try {
            AhonaApi.heartbeat(token, InstallationManager.installationId(context), appVersion(context), deviceModel(), errors)
        } catch (e: AhonaApi.ApiError) {
            if (e.httpStatus == 403) InstallationManager.setPremium(context, false)
            null
        } catch (e: Exception) {
            null
        }
    }

    // Keeps the installation alive at the backend-configured interval.
    suspend fun runHeartbeatLoop(context: Context) {
        while (true) {
            val r = withContext(Dispatchers.IO) { heartbeatOnce(context) }
            delay((r?.nextHeartbeatSeconds ?: 300).coerceAtLeast(30) * 1000L)
        }
    }

    // A premium badge/chip is only real when a license device token exists.
    // Normal users (no successful activation) never qualify, even if the stored flag is stale.
    fun isPremium(context: Context): Boolean =
        InstallationManager.isPremium(context) && InstallationManager.deviceToken(context) != null

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }
}
