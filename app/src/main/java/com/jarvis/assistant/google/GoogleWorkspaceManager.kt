package com.jarvis.assistant.google

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import okhttp3.MediaType.Companion.toMediaType
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
        get() = securePrefs?.getLong(KEY_CONNECTED_AT, 0L) ?: 0L
        set(value) { check(edit { it.putLong(KEY_CONNECTED_AT, value) }) { "Could not save the connection time." } }

    fun clear() {
        runCatching { connected = false }
        runCatching { accountEmail = "" }
        runCatching { connectedAtMs = 0L }
    }
}

/**
 * Google Workspace authorization through the official AuthorizationClient API.
 * Gmail (read + send), Calendar (read + create events) and Drive (read).
 * The access token is short-lived and is never stored - whenever Google data is
 * needed, authorize() is called again and Play services returns a fresh token
 * silently while the grants exist, or asks for renewed consent when required.
 */
object GoogleWorkspaceManager {
    const val SCOPE_GMAIL = "https://www.googleapis.com/auth/gmail.readonly"
    const val SCOPE_GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"
    const val SCOPE_CALENDAR = "https://www.googleapis.com/auth/calendar.readonly"
    const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
    const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive.readonly"

    private val SCOPES = listOf(
        Scope(SCOPE_GMAIL), Scope(SCOPE_GMAIL_SEND),
        Scope(SCOPE_CALENDAR), Scope(SCOPE_CALENDAR_EVENTS),
        Scope(SCOPE_DRIVE)
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun preferences(context: Context) = GoogleWorkspacePreferences(context)

    /** First step of every authorization: silent token if grants exist, else a consent intent. */
    /** The Google "access blocked / app not verified" screen is fixed in Google Cloud Console. */
    private fun blockHelpText(base: String): String = base +
        "\n\nFIX: Google Cloud Console > APIs & Services > OAuth consent screen > Test users > add your Google email, save, wait 5 minutes, then retry."

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
            .addOnFailureListener { e ->
                val msg = e.message ?: "unknown error"
                val base = "Google authorization failed: " + msg
                onError(
                    if (msg.contains("blocked", true) || msg.contains("not verified", true) ||
                        msg.contains("access_denied", true) || msg.startsWith("10:")
                    ) blockHelpText(base) else base
                )
            }
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
            val cmsg = (e.message ?: "unknown error")
                        val cbase = "Google consent failed: " + cmsg
                        onError(
                            if (cmsg.contains("blocked", true) || cmsg.contains("not verified", true) || cmsg.contains("access_denied", true))
                                blockHelpText(cbase) else cbase
                        )
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
        "Gmail",
        onDone
    ) { response ->
        "OK - connected to " + response.optString("emailAddress") + " (" + response.optInt("messagesTotal") + " messages)"
    }

    fun testCalendar(context: Context, onDone: (String) -> Unit) = testApi(
        context,
        "https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=1",
        "Calendar",
        onDone
    ) { response ->
        val count = response.optJSONArray("items")?.length() ?: 0
        "OK - calendar list reachable, " + count + " calendar(s) returned"
    }

    fun testDrive(context: Context, onDone: (String) -> Unit) = testApi(
        context,
        "https://www.googleapis.com/drive/v3/about?fields=user,storageQuota",
        "Drive",
        onDone
    ) { response ->
        val user = response.optJSONObject("user")
        val email = user?.optString("emailAddress") ?: "unknown"
        val quota = response.optJSONObject("storageQuota")
        val usage = quota?.optLong("usage") ?: 0L
        val limit = quota?.optLong("limit") ?: 0L
        "OK - " + email + ", " + humanBytes(usage) + " of " + (if (limit > 0) humanBytes(limit) else "unlimited") + " used"
    }

    private fun testApi(context: Context, url: String, label: String, onDone: (String) -> Unit, summarize: (JSONObject) -> String) {
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

    /** Disconnect: clear local status, then revoke the grants at Google via the OAuth revoke endpoint. */
    fun disconnect(context: Context, onDone: (String) -> Unit) {
        val prefs = preferences(context)
        val email = prefs.accountEmail
        prefs.clear()
        if (email.isBlank()) {
            onDone("Disconnected.")
            return
        }
        requestAuthorization(
            context,
            { token ->
                executor.submit {
                    try {
                        val form = okhttp3.FormBody.Builder().add("token", token).build()
                        val revoked = http.newCall(
                            Request.Builder().url("https://oauth2.googleapis.com/revoke").post(form).build()
                        ).execute().use { it.isSuccessful }
                        main.post {
                            onDone(if (revoked) "Disconnected. Google access revoked." else "Disconnected. Google revoke not confirmed - check myaccount.google.com/permissions.")
                        }
                    } catch (e: Exception) {
                        main.post { onDone("Disconnected locally. Google revoke not confirmed (" + (e.message ?: "error") + ").") }
                    }
                }
            },
            { sender ->
                // Grants are already gone: a silent token is no longer returned.
                onDone("Disconnected.")
            },
            { error ->
                onDone("Disconnected locally. Google revoke not confirmed.")
            }
        )
    }

    // ---- DEV STYLE: real in-app actions - no external app or browser is ever opened ----

    private fun toBase64Url(text: String): String =
        android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

    /** Send an email directly through the connected Gmail. */
    fun sendEmail(context: Context, to: String, subject: String, body: String, onDone: (String) -> Unit) {
        getAccessToken(context, { token ->
            executor.submit {
                try {
                    val mime = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
                    val payload = JSONObject().put("raw", toBase64Url(mime))
                    http.newCall(
                        Request.Builder()
                            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                            .header("Authorization", "Bearer " + token)
                            .post(okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), payload.toString()))
                            .build()
                    ).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw Exception("HTTP " + r.code + " - " + text.take(160))
                    }
                    main.post { onDone("OK - email sent to " + to) }
                } catch (e: Exception) {
                    main.post { onDone("FAILED - " + (e.message ?: "error")) }
                }
            }
        }, { error -> onDone("FAILED - " + error) })
    }

    /** Create a calendar event directly - the Calendar app is never opened. */
    fun createCalendarEvent(context: Context, title: String, startIso: String, endIso: String, description: String, onDone: (String) -> Unit) {
        getAccessToken(context, { token ->
            executor.submit {
                try {
                    val tz = java.util.TimeZone.getDefault().id
                    val body = JSONObject()
                        .put("summary", title)
                        .put("description", description)
                        .put("start", JSONObject().put("dateTime", startIso).put("timeZone", tz))
                        .put("end", JSONObject().put("dateTime", endIso).put("timeZone", tz))
                    val link = http.newCall(
                        Request.Builder()
                            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                            .header("Authorization", "Bearer " + token)
                            .post(okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), body.toString()))
                            .build()
                    ).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw Exception("HTTP " + r.code + " - " + text.take(160))
                        JSONObject(text).optString("htmlUrl")
                    }
                    main.post { onDone("OK - '" + title + "' set on the calendar " + link) }
                } catch (e: Exception) {
                    main.post { onDone("FAILED - " + (e.message ?: "error")) }
                }
            }
        }, { error -> onDone("FAILED - " + error) })
    }

    /** Read the latest emails in-app. */
    fun readRecentEmails(context: Context, count: Int, onDone: (String) -> Unit) {
        getAccessToken(context, { token ->
            executor.submit {
                try {
                    val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=" + count.coerceIn(1, 10)
                    val list = JSONObject(http.newCall(Request.Builder().url(listUrl).header("Authorization", "Bearer " + token).build())
                        .execute().use { r -> if (!r.isSuccessful) throw Exception("HTTP " + r.code); r.body?.string().orEmpty() })
                        .optJSONArray("messages") ?: org.json.JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until list.length()) {
                        val id = list.getJSONObject(i).optString("id")
                        val msg = JSONObject(http.newCall(
                            Request.Builder()
                                .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + id + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From")
                                .header("Authorization", "Bearer " + token)
                                .build()
                        ).execute().use { r -> if (!r.isSuccessful) throw Exception("HTTP " + r.code); r.body?.string().orEmpty() })
                        var from = ""; var subject = ""
                        val headers = msg.optJSONObject("payload")?.optJSONArray("headers")
                        if (headers != null) for (h in 0 until headers.length()) {
                            val name = headers.getJSONObject(h).optString("name")
                            if (name.equals("From", true)) from = headers.getJSONObject(h).optString("value")
                            if (name.equals("Subject", true)) subject = headers.getJSONObject(h).optString("value")
                        }
                        sb.append("\u2022 ").append(if (subject.isBlank()) "(no subject)" else subject)
                            .append(" \u2014 ").append(from).append("\n")
                    }
                    main.post { onDone(if (sb.isBlank()) "Inbox is empty." else sb.toString().trim()) }
                } catch (e: Exception) {
                    main.post { onDone("FAILED - " + (e.message ?: "error")) }
                }
            }
        }, { error -> onDone("FAILED - " + error) })
    }

    /** List upcoming calendar events in-app. */
    fun listUpcomingEvents(context: Context, count: Int, onDone: (String) -> Unit) {
        getAccessToken(context, { token ->
            executor.submit {
                try {
                    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                    val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=" +
                        java.net.URLEncoder.encode(now, "UTF-8") +
                        "&maxResults=" + count.coerceIn(1, 10) + "&singleEvents=true&orderBy=startTime"
                    val items = JSONObject(http.newCall(Request.Builder().url(url).header("Authorization", "Bearer " + token).build())
                        .execute().use { r -> if (!r.isSuccessful) throw Exception("HTTP " + r.code); r.body?.string().orEmpty() })
                        .optJSONArray("items") ?: org.json.JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until items.length()) {
                        val ev = items.getJSONObject(i)
                        val start = ev.optJSONObject("start")?.optString("dateTime").orEmpty().replace("T", " ").take(16)
                        sb.append("\u2022 ").append(start).append(" \u2014 ").append(ev.optString("summary", "(no title)")).append("\n")
                    }
                    main.post { onDone(if (sb.isBlank()) "No upcoming events." else sb.toString().trim()) }
                } catch (e: Exception) {
                    main.post { onDone("FAILED - " + (e.message ?: "error")) }
                }
            }
        }, { error -> onDone("FAILED - " + error) })
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
