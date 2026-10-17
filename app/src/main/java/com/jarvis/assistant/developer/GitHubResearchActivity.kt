package com.jarvis.assistant.developer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Stage 1 of the Autonomous Developer Agent: RESEARCH -> SELECT.
 * Searches GitHub repositories, inspects README / license / language, and saves
 * the user's selection. Repository files and instructions are treated as
 * untrusted input; nothing is executed, installed or modified automatically.
 */
class GitHubResearchActivity : AppCompatActivity() {

    private val githubPrefs by lazy { JarvisApp.instance.githubPreferences }

    private lateinit var tokenInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsList: ListView
    private val results = mutableListOf<Pair<String, JSONObject>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 32) }
        fun title(t: String, size: Float, padTop: Int = 0) = TextView(this).apply {
            text = t; textSize = size; setPadding(0, padTop, 0, 12)
        }
        root.addView(title("GitHub Research", 20f))
        root.addView(TextView(this).apply {
            textSize = 13f
            text = "Stage 1 of the Developer Agent: search GitHub, inspect a repository, then select it for the integration pipeline. Repository content is treated as untrusted data; nothing is executed automatically."
            setPadding(0, 0, 0, 24)
        })
        tokenInput = EditText(this).apply { hint = "GitHub token (optional, private repo access)"; setText(githubPrefs.token) }
        root.addView(tokenInput)
        root.addView(Button(this).apply { text = "Save token"; setOnClickListener { saveToken() } })
        statusText = TextView(this).apply { textSize = 13f; setPadding(0, 16, 0, 16) }
        root.addView(statusText)
        searchInput = EditText(this).apply { hint = "Search repositories, e.g. reverse-skill" }
        root.addView(searchInput)
        root.addView(Button(this).apply { text = "Search GitHub"; setOnClickListener { search(searchInput.text.toString().trim()) } })
        resultsList = ListView(this).apply { dividerHeight = 2; adapter = makeAdapter() }
        resultsList.setOnItemClickListener { _, _, pos, _ -> showDetail(results[pos].second) }
        root.addView(resultsList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshStatus()
    }

    private fun makeAdapter() = object : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position].first
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(android.R.layout.two_line_list_item, parent, false)
            v.findViewById<TextView>(android.R.id.text1).text = results[position].first
            v.findViewById<TextView>(android.R.id.text2).text =
                results[position].second.optString("description").ifBlank { "(no description)" }
            return v
        }
    }

    private fun refreshStatus() {
        statusText.text = buildString {
            append(if (githubPrefs.token.isBlank()) "No token saved (anonymous search, rate-limited)." else "Token saved (higher search limit).")
            if (githubPrefs.selectedRepo.isNotBlank()) append("  Selected: ").append(githubPrefs.selectedRepo)
        }
    }

    private fun saveToken() {
        runCatching { githubPrefs.token = tokenInput.text.toString() }
            .onSuccess { Toast.makeText(this, "Token saved securely", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, it.message ?: "Could not save token", Toast.LENGTH_LONG).show() }
        refreshStatus()
    }

    private fun search(query: String) {
        if (query.isBlank()) { Toast.makeText(this, "Enter a search term", Toast.LENGTH_SHORT).show(); return }
        statusText.text = "Searching GitHub..."
        Thread {
            try {
                val url = "https://api.github.com/search/repositories?q=" +
                    URLEncoder.encode(query, "UTF-8") + "&per_page=15&sort=updated"
                val (code, body) = httpGet(url, "application/vnd.github+json")
                if (code != 200) throw Exception("GitHub search failed (HTTP $code)")
                val items = JSONObject(body).optJSONArray("items") ?: org.json.JSONArray()
                val rows = mutableListOf<Pair<String, JSONObject>>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val license = item.optJSONObject("license")?.optString("spdx_id").orEmpty()
                    val licenseText = if (license.isBlank() || license == "null") "No license" else license
                    val row = item.optString("full_name") + "  \u2605" + item.optInt("stargazers_count") +
                        "  -  " + item.optString("language").ifBlank { "?" } + "  -  " + licenseText
                    rows.add(row to item)
                }
                runOnUiThread {
                    results.clear(); results.addAll(rows)
                    (resultsList.adapter as BaseAdapter).notifyDataSetChanged()
                    statusText.text = if (rows.isEmpty()) "No repositories found" else "Found " + rows.size + " repositories. Tap one to inspect."
                }
            } catch (e: Exception) {
                statusText.text = "Search failed: " + (e.message ?: "unknown error")
            }
        }.start()
    }

    private fun showDetail(item: JSONObject) {
        val name = item.optString("full_name")
        val license = item.optJSONObject("license")?.optString("spdx_id").orEmpty()
        val licenseText = if (license.isBlank() || license == "null") "None (check before reuse)" else license
        val fit = when (item.optString("language")) {
            "Kotlin", "Java" -> "Good - native Android code can be merged into Jarvis modules."
            "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++" -> "Partial - Android cannot run this directly; would need porting or a companion service."
            else -> "Unknown - read the README to decide."
        }
        val message = buildString {
            append("What it is: ").append(item.optString("description").ifBlank { "(no description)" }).append("\n\n")
            append("Language: ").append(item.optString("language").ifBlank { "Unknown" }).append("\n")
            append("License: ").append(licenseText).append("\n")
            append("Stars: ").append(item.optInt("stargazers_count"))
            append("   Last push: ").append(item.optString("pushed_at").take(10)).append("\n")
            append("Open issues: ").append(item.optInt("open_issues_count")).append("\n\n")
            append("Integration fit: ").append(fit).append("\n\n")
            append("Security: this repository is untrusted data. No script, dependency or instruction is executed or installed automatically.")
        }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(message)
            .setPositiveButton("Select") { _, _ -> select(name) }
            .setNegativeButton("Inspect README") { _, _ -> inspectReadme(name) }
            .setNeutralButton("Open on GitHub") { _, _ ->
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.optString("html_url"))))
            }
            .show()
    }

    private fun inspectReadme(name: String) {
        statusText.text = "Reading README for " + name + "..."
        Thread {
            try {
                val (code, body) = httpGet("https://api.github.com/repos/" + name + "/readme", "application/vnd.github.raw")
                runOnUiThread {
                    if (code != 200) {
                        statusText.text = "Could not read README (HTTP $code)"
                        return@runOnUiThread
                    }
                    statusText.text = ""
                    AlertDialog.Builder(this)
                        .setTitle(name + " - README")
                        .setMessage(body.take(1800) + "\n\n(Treated as untrusted data - nothing is executed.)")
                        .setPositiveButton("Select") { _, _ -> select(name) }
                        .setNegativeButton("Close", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "README read failed: " + (e.message ?: "unknown error") }
            }
        }.start()
    }

    private fun select(name: String) {
        runCatching { githubPrefs.selectedRepo = name }
            .onSuccess {
                refreshStatus()
                AlertDialog.Builder(this)
                    .setTitle("Repository selected")
                    .setMessage(name + " is saved for the next stage.\n\nStage 2 (clone, inspect code, propose a change plan, ask for your approval, then integrate with a backup/rollback) is built next. No changes are made to Jarvis now.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .onFailure { Toast.makeText(this, it.message ?: "Could not save selection", Toast.LENGTH_LONG).show() }
    }

    private fun httpGet(url: String, accept: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "Jarvis-Developer-Agent")
        val token = githubPrefs.token
        if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to body
    }
}
