package com.jarvis.assistant.developer

import com.jarvis.assistant.JarvisApp
import java.io.File

/**
 * DEV STYLE: one-command website builder — generates a clean white/lime landing
 * page from the brief, saves it on the device and reports where it is.
 */
object WebsiteBuilder {

    fun createWebsiteAndReport(brief: String): String {
        val title = brief.take(60).trim().ifBlank { "DEV STYLE Site" }
        val dir: File = JarvisApp.instance.getExternalFilesDir("websites")
            ?: JarvisApp.instance.filesDir
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "site" }
        val file = File(dir, safeName + ".html")
        return runCatching {
            file.writeText(buildHtml(title, brief))
            "Website ready, boss! '" + title + "' saved at " + file.absolutePath
        }.getOrElse { "Boss, website banate parlam na: " + (it.message ?: "error") }
    }

    private fun buildHtml(title: String, brief: String): String {
        val paragraphs = brief.split(Regex("[.;\\n]+")).map { it.trim() }.filter { it.length > 2 }
        val body = paragraphs.joinToString("") { p -> "      <p>" + p + "</p>\n" }
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>$title</title>
<style>
  body { margin: 0; font-family: system-ui, sans-serif; background: #ffffff; color: #1A2E05; }
  header { background: #84CC16; padding: 48px 24px; text-align: center; }
  header h1 { margin: 0; font-size: 32px; color: #1A2E05; }
  main { max-width: 720px; margin: 0 auto; padding: 32px 24px; }
  footer { text-align: center; padding: 24px; font-size: 12px; color: #6B7280; }
</style>
</head>
<body>
  <header><h1>$title</h1></header>
  <main>
$body  </main>
  <footer>Built by DEV STYLE (Jarvis)</footer>
</body>
</html>"""
    }
}
