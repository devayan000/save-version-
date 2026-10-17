package com.jarvis.assistant.tools

import android.content.Context
import com.jarvis.assistant.bugs.BugDetectionService
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Search tool. Checks whether an authorized search provider is configured,
 * executes the search, verifies a valid result, and creates a bug record on failure.
 * Never pretends a search occurred when no search was performed.
 */
object GoogleSearchService {

    private const val PREFS = "jarvis_search"
    private const val KEY_ENDPOINT = "search_endpoint"
    private const val KEY_API_KEY = "search_api_key"

    data class SearchResult(val success: Boolean, val results: String, val error: String?)

    fun setEndpoint(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENDPOINT, url.trim()).apply()
    }
    fun getEndpoint(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, "").orEmpty()

    fun setApiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun isConfigured(ctx: Context): Boolean = getEndpoint(ctx).isNotBlank()

    fun search(ctx: Context, query: String): SearchResult {
        val endpoint = getEndpoint(ctx)
        if (endpoint.isBlank()) {
            BugDetectionService.report(
                ctx,
                feature = "Google Search",
                command = "Search: " + query,
                expected = "Perform a web search and return results",
                actual = "Search tool unavailable - no provider configured",
                errorType = BugDetectionService.ErrorType.MISSING_DEPENDENCY,
                errorMessage = "Search integration is not configured",
                rootCause = "Search provider endpoint is not set in Settings",
                suggestedFix = "Configure an authorized search provider and credentials in Settings",
                requiredDependency = "Search API endpoint (e.g. Google Custom Search, SerpAPI, or Bing)",
                severity = BugDetectionService.Severity.HIGH
            )
            return SearchResult(false, "", "Search is not configured. Set a search provider in Settings.")
        }
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = if (endpoint.contains("{query}")) endpoint.replace("{query}", encoded)
                else if (endpoint.contains("?")) endpoint + "&q=" + encoded else endpoint + "?q=" + encoded
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
            }
            val apiKey = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_KEY, "").orEmpty()
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer " + apiKey)
            val code = conn.responseCode
            val body = (conn.inputStream.bufferedReader()?.readText() ?: conn.errorStream?.bufferedReader()?.readText() ?: "")
            conn.disconnect()
            if (code in 200..299 && body.isNotBlank()) {
                SearchResult(true, body, null)
            } else {
                BugDetectionService.report(
                    ctx,
                    feature = "Google Search",
                    command = "Search: " + query,
                    expected = "Perform a web search and return results",
                    actual = "Search API returned error " + code,
                    errorType = BugDetectionService.ErrorType.API_ERROR,
                    errorMessage = "HTTP " + code + ": " + body.take(200),
                    rootCause = "Search provider returned an error response",
                    suggestedFix = "Check the search API key and endpoint configuration",
                    severity = BugDetectionService.Severity.MEDIUM
                )
                SearchResult(false, "", "Search failed (HTTP " + code + "). Check your search provider settings.")
            }
        } catch (e: java.net.SocketTimeoutException) {
            BugDetectionService.report(
                ctx,
                feature = "Google Search",
                command = "Search: " + query,
                expected = "Perform a web search and return results",
                actual = "Search request timed out",
                errorType = BugDetectionService.ErrorType.TIMEOUT,
                errorMessage = e.message,
                rootCause = "The search provider is slow or unreachable",
                suggestedFix = "Check network connectivity or try a different search provider",
                severity = BugDetectionService.Severity.MEDIUM
            )
            SearchResult(false, "", "Search timed out. Check your network and try again.")
        } catch (e: Exception) {
            BugDetectionService.report(
                ctx,
                feature = "Google Search",
                command = "Search: " + query,
                expected = "Perform a web search and return results",
                actual = "Search request failed: " + e.message,
                errorType = BugDetectionService.ErrorType.UNKNOWN,
                errorMessage = e.message,
                severity = BugDetectionService.Severity.LOW
            )
            SearchResult(false, "", "Search failed: " + e.message)
        }
    }

    /**
     * Free fallback search using DuckDuckGo Lite. Works with no API key and no
     * provider configuration, so background web search always has a path.
     */
    fun searchFree(query: String): SearchResult {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://lite.duckduckgo.com/lite/?q=" + encoded)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
            }
            val code = conn.responseCode
            val body = (conn.inputStream.bufferedReader()?.readText() ?: "")
            conn.disconnect()
            if (code in 200..299) {
                val parsed = parseLiteResults(body)
                if (parsed.isNotBlank()) SearchResult(true, parsed, null)
                else SearchResult(false, "", "No search results found.")
            } else SearchResult(false, "", "Free search failed (HTTP " + code + ").")
        } catch (e: java.net.SocketTimeoutException) {
            SearchResult(false, "", "Free search timed out.")
        } catch (e: Exception) {
            SearchResult(false, "", "Free search failed: " + e.message)
        }
    }

    /** Parses DuckDuckGo Lite HTML into a compact list of titles, links and snippets. */
    fun parseLiteResults(html: String): String {
        val snippetRegex = Regex("<td[^>]*class=\"result-snippet\"[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
        val linkRegex = Regex("<a[^>]*rel=\"nofollow\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val snippets = snippetRegex.findAll(html).map { m ->
            m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
        }.toList()
        val sb = StringBuilder()
        var i = 0
        for (m in linkRegex.findAll(html)) {
            val href = m.groupValues[1]
            val title = m.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            if (title.isBlank() || href.startsWith("/")) continue
            if (i >= 6) break
            sb.append(i + 1).append(". ").append(title).append(" - ").append(href)
            if (i < snippets.size && snippets[i].isNotBlank()) sb.append("\n   ").append(snippets[i].take(220))
            sb.append("\n")
            i++
        }
        return sb.toString().trim()
    }

    /**
     * Unified background search: uses the configured provider when available,
     * otherwise falls back to the free DuckDuckGo search automatically.
     */
    fun searchWithFallback(ctx: Context, query: String): SearchResult {
        if (isConfigured(ctx)) {
            val configured = search(ctx, query)
            if (configured.success) return configured
        }
        return searchFree(query)
    }
}
