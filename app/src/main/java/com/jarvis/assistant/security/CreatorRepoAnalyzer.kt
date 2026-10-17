package com.jarvis.assistant.security

import android.content.Context
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEV STYLE: Creator-only repository analyzer.
 * Given any GitHub repository link, it clones nothing - it reads the repo
 * metadata, file tree and README, then produces a full structural analysis
 * so the creator can understand the codebase at a glance.
 * Read-only: nothing in the analyzed repository is modified.
 */
object CreatorRepoAnalyzer {

    data class RepoRef(val owner: String, val repo: String, val branch: String?)

    fun parse(input: String): RepoRef? {
        var s = input.trim().removeSuffix("/").removeSuffix(".git")
        s = s.removePrefix("https://github.com/").removePrefix("http://github.com/").removePrefix("github.com/")
        val parts = s.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val branch = if (parts.size >= 4 && parts[2] == "tree") parts[3] else null
        if (owner.isBlank() || repo.isBlank()) return null
        return RepoRef(owner, repo, branch)
    }

    suspend fun analyze(context: Context, repoInput: String): String = withContext(Dispatchers.IO) {
        val ref = parse(repoInput)
            ?: return@withContext "I could not read that repository link. Paste it like https://github.com/owner/repo"
        val token = runCatching { JarvisApp.instance.githubPreferences.token }.getOrNull().orEmpty()
        try {
            val headers = mutableMapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "Jarvis-Creator-Analyzer"
            )
            if (token.isNotBlank()) headers["Authorization"] = "Bearer " + token

            fun get(url: String): Pair<Int, String> {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return code to body
            }

            val (infoCode, infoBody) = get("https://api.github.com/repos/" + ref.owner + "/" + ref.repo)
            if (infoCode != 200) {
                return@withContext "GitHub answered $infoCode for " + ref.owner + "/" + ref.repo +
                    ". If the repository is private, save your GitHub token in the developer settings first."
            }
            val info = JSONObject(infoBody)
            val branch = ref.branch ?: info.optString("default_branch", "main")
            val sb = StringBuilder()
            sb.append("REPOSITORY ANALYSIS - ").append(info.optString("full_name", ref.owner + "/" + ref.repo)).append("\n")
            sb.append("Branch: ").append(branch)
                .append(" | Main language: ").append(info.optString("language", "unknown"))
                .append(" | Stars: ").append(info.optInt("stargazers_count")).append("\n")
            sb.append("Description: ").append(info.optString("description", "(none)")).append("\n")

            val (treeCode, treeBody) = get("https://api.github.com/repos/" + ref.owner + "/" + ref.repo + "/git/trees/" + branch + "?recursive=1")
            if (treeCode == 200) {
                val tree = JSONObject(treeBody).optJSONArray("tree")
                val files = mutableListOf<String>()
                for (i in 0 until (tree?.length() ?: 0)) {
                    val entry = tree!!.getJSONObject(i)
                    if (entry.optString("type") == "blob") files.add(entry.optString("path"))
                }
                sb.append("Total files: ").append(files.size).append("\n")
                val folders = files.groupBy { it.split("/").first() }
                    .entries.sortedByDescending { it.value.size }.take(10)
                sb.append("Top folders:\n")
                folders.forEach { sb.append("  ").append(it.key).append(" - ").append(it.value.size).append(" files\n") }
                val exts = files.mapNotNull { p ->
                    val e = p.substringAfterLast('.', "").lowercase()
                    if (e.isNotBlank() && e.length <= 5) e else null
                }.groupingBy { it }.eachCount()
                val topExts = exts.entries.sortedByDescending { it.value }.take(8)
                    .joinToString { it.key + " (" + it.value + ")" }
                sb.append("File types: ").append(topExts).append("\n")
                val keyFiles = files.filter {
                    it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") || it == "package.json" ||
                        it == "requirements.txt" || it == "pubspec.yaml" || it == "pom.xml" || it.endsWith("AndroidManifest.xml")
                }
                if (keyFiles.isNotEmpty()) sb.append("Build/manifest files: ").append(keyFiles.joinToString(", ")).append("\n")
                val mainSources = files.filter {
                    it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".py") ||
                        it.endsWith(".js") || it.endsWith(".ts") || it.endsWith(".dart")
                }.take(15)
                if (mainSources.isNotEmpty()) {
                    sb.append("Main source files (sample):\n")
                    mainSources.forEach { sb.append("  ").append(it).append("\n") }
                }
            } else {
                sb.append("Could not read the file tree (GitHub answered ").append(treeCode).append(").\n")
            }

            for (readme in listOf("README.md", "readme.md")) {
                val (rc, rb) = get("https://raw.githubusercontent.com/" + ref.owner + "/" + ref.repo + "/" + branch + "/" + readme)
                if (rc == 200) {
                    sb.append("\nREADME (first 2000 characters):\n").append(rb.take(2000)).append("\n")
                    break
                }
            }
            sb.append("\nSummary: read-only analysis. I changed nothing in the repository.")
            sb.toString()
        } catch (e: Exception) {
            "Analysis failed: " + e.message
        }
    }
}
