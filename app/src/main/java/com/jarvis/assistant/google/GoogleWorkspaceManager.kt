package com.jarvis.assistant.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevocationRequest
import com.google.android.gms.common.api.Scope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Stores the connection STATUS only - never the raw access token. */
class GoogleWorkspacePreferences(context: Context) {
    companion object {
        private const val FILE = "jarvis_google_workspace_secure_prefs"
        private const val KEY_CONNECTED = "key_gw_connected"
        private const val KEY_EMAIL = "key_gw_account_email"
        private const val KEY_CONNECTED_AT = "key_gw_connected_at"
    }

    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    private fun edit(block: (SharedPreferences.Editor) -> Unit): Boolean {
        val s = securePrefs ?: return false
        return s.edit().also { block(it) }.commit()
    }

    var connected: Boolean
        get() = securePrefs?.getBoolean(KEY_CONNECTED, false) ?: false
        set(value) { check(edit { it.putBoolean(KEY_CONNECTED, value) }) { "Could not save Google Workspace status." } }

    var accountEmail: String
        get() = securePrefs?.getString(KEY_EMAIL, "") ?: ""
        set(value) { check(edit { it.putString(KEY_EMAIL, value.trim()) }) { "Could not save the Google account." } }

    var connectedAtMs: Long
        get() = securePrefs.getLong(KEY_CONNECTED_AT, 0L)
        set(value) { check(edit { it.putLong(KEY_CONNECTED_AT, value) }) { "Could not save the connection time." } }

    fun clear() {
        runCatching { connected = false }
        runCatching { accountEmail = "" }
        runCatching { connectedAtMs = 0L }
    }
}

/**
 * Google Workspace authorization through the official AuthorizationClient API.
 * Only read-only scopes are requested: Gmail, Calendar and Drive.
 * The access token is short-lived and is never stored - whenever Google data is
 * needed, authorize() is called again and Play services returns a fresh token
 * silently while the grants exist, or asks for renewed consent when required.
 */
object GoogleWorkspaceManager {
    const val SCOPE_GMAIL = "https://www.googleapis.com/auth/gmail.readonly"
    const val SCOPE_CALENDAR = "https://www.googleapis.com/auth/calendar.readonly"
    const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive.readonly"

    private val SCOPES = listOf(Scope(SCOPE_GMAIL), Scope(SCOPE_CALENDAR), Scope(SCOPE_DRIVE))
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun preferences(context: Context) = GoogleWorkspacePreferences(context)

    /** First step of every authorization: silent token if grants exist, else a consent intent. */
    fun requestAuthorization(context: Context, onToken: (String) -> Unit, onNeedsConsent: (android.content.IntentSender) -> Unit, onError: (String) -> Unit) {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(SCOPES)
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                val pending = result.pendingIntent
                when {
                    !token.isNullOrBlank() -> onToken(token)
                    pending != null -> onNeedsConsent(pending.intentSender)
                    else -> onError("Google did not return a token or a consent request.")
                }
            }
            .addOnFailureListener { e -> onError("Google authorization failed: " + (e.message ?: "unknown error")) }
    }

    /** Called when the consent screen closes. */
    fun handleConsentResult(context: Context, data: Intent?, onToken: (String) -> Unit, onError: (String) -> Unit) {
        if (data == null) {
            onError("Google consent was cancelled.")
            return
        }
        try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                onError("Google did not return an access token.")
            } else {
                onToken(token)
            }
        } catch (e: Exception) {
            onError("Google consent failed: " + (e.message ?: "unknown error"))
        }
    }

    /** Any feature needing Google data calls this: fresh token, renewed consent handled. */
    fun getAccessToken(context: Context, onToken: (String) -> Unit, onError: (String) -> Unit) {
        requestAuthorization(
            context,
            onToken,
            { onNeedsConsent -> onError("Google access needs your approval again - open Google Workspace and connect once more.") },
            onError
        )
    }

    /** Verify the fresh token by reading the Google profile, then remember the account (an email, not a token). */
    fun fetchAccountEmail(token: String, onEmail: (String) -> Unit, onError: (String) -> Unit) {
        executor.submit {
            try {
                val email = http.newCall(
                    Request.Builder()
                        .url("https://gmail.googleapis.com/gmail/v1/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP " + resp.code)
                    JSONObject(resp.body?.string() ?: "{}").optString("emailAddress")
                }
                if (email.isBlank()) throw Exception("Google returned no email address")
                main.post { onEmail(email) }
            } catch (e: Exception) {
                main.post { onError("Could not read the Google profile: " + e.message) }
            }
        }
    }

    fun setConnected(context: Context, email: String) {
        val p = preferences(context)
        p.connected = true
        p.accountEmail = email
        p.connectedAtMs = System.currentTimeMillis()
    }

    fun isConnected(context: Context): Boolean = preferences(context).connected

    // ---- One test per API: Gmail / Calendar / Drive ----

    fun testGmail(context: Context, onDone: (String) -> Unit) = testApi(
        context,
        "https://gmail.googleapis.com/gmail/v1/users/me/profile",
        "Gmail"
    ) { response ->
        "OK - connected to " + response.optString("emailAddress") + " (" + response.optInt("messagesTotal") + " messages)"
    }

    fun testCalendar(context: Context, onDone: (String) -> Unit) = testApi(
        context,
        "https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=1",
        "Calendar"
    ) { response ->
        val count = response.optJSONArray("items")?.length() ?: 0
        "OK - calendar list reachable, " + count + " calendar(s) returned"
    }

    fun testDrive(context: Context, onDone: (String) -> Unit) = testApi(
        context,
        "https://www.googleapis.com/drive/v3/about?fields=user,storageQuota",
        "Drive"
    ) { response ->
        val user = response.optJSONObject("user")
        val email = user?.optString("emailAddress") ?: "unknown"
        val quota = response.optJSONObject("storageQuota")
        val usage = quota?.optLong("usage") ?: 0L
        val limit = quota?.optLong("limit") ?: 0L
        "OK - " + email + ", " + humanBytes(usage) + " of " + (if (limit > 0) humanBytes(limit) else "unlimited") + " used"
    }

    private fun testApi(context: Context, url: String, label: String, summarize: (JSONObject) -> String) {
        getAccessToken(context, { token ->
            executor.submit {
                try {
                    val body = http.newCall(
                        Request.Builder().url(url).header("Authorization", "Bearer " + token).build()
                    ).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw Exception("HTTP " + resp.code + (if (text.isNotBlank()) " - " + text.take(160) else ""))
                        }
                        JSONObject(text)
                    }
                    val message = summarize(body)
                    main.post { onDone(label + ": " + message) }
                } catch (e: Exception) {
                    main.post { onDone(label + ": FAILED - " + e.message) }
                }
            }
        }, { error -> onDone(label + ": FAILED - " + error) })
    }

    fun disconnect(context: Context, onDone: (String) -> Unit) {
        val prefs = preferences(context)
        val email = prefs.accountEmail
        prefs.clear()
        if (email.isBlank()) {
            onDone("Disconnected.")
            return
        }
        Identity.getAuthorizationClient(context)
            .revokeAccess(RevocationRequest.Builder().setAccount(Account(email, "com.google")).build())
            .addOnSuccessListener { onDone("Disconnected. Google access revoked.") }
            .addOnFailureListener { e ->
                onDone("Disconnected locally. Revoke was not confirmed (" + (e.message ?: "error") + ") - remove Jarvis from myaccount.google.com/permissions if needed.")
            }
    }

    private fun humanBytes(value: Long): String {
        if (value <= 0) return "0 MB"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
    }
}
