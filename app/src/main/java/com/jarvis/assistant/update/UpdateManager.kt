package com.jarvis.assistant.update

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.jarvis.assistant.JarvisApp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Checks for and installs app updates. The same signing key is required —
 *  Android rejects an APK signed differently and asks the user to uninstall first. */
object UpdateManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).build()

    fun currentVersionCode(): Int = try {
        val pm = JarvisApp.instance.packageManager
        val info = pm.getPackageInfo(JarvisApp.instance.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 0 }

    fun currentVersionName(): String = try {
        JarvisApp.instance.packageManager
            .getPackageInfo(JarvisApp.instance.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    data class UpdateInfo(val versionCode: Int, val versionName: String, val downloadUrl: String, val changelog: String)

    fun checkForUpdate(url: String): UpdateInfo? {
        if (url.isBlank()) return null
        val response = client.newCall(Request.Builder().url(url.trim()).build()).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val remoteCode = json.optInt("versionCode", 0)
        if (remoteCode <= currentVersionCode()) return null
        val downloadUrl = json.optString("downloadUrl").trim()
        if (downloadUrl.isBlank() || !downloadUrl.startsWith("https://")) return null
        return UpdateInfo(remoteCode, json.optString("versionName"), downloadUrl, json.optString("changelog", ""))
    }

    fun downloadApk(url: String, onProgress: (Float) -> Unit): File? {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        val total = body.contentLength()
        val dir = File(JarvisApp.instance.cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, "jarvis-update.apk")
        if (file.exists()) file.delete()
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var read = 0
                var downloaded = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress(downloaded.toFloat() / total)
                }
            }
        }
        return file
    }

    fun installApk(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val intent = Intent(if (Build.VERSION.SDK_INT >= 24) Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
}
