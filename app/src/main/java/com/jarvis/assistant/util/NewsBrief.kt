package com.jarvis.assistant.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEV STYLE — Breaking news brief.
 * Fetches the top Bangladeshi headlines from public RSS feeds and returns a
 * short spoken-friendly brief. Used when the mic opens so JARVIS greets the
 * user with the latest breaking story.
 */
object NewsBrief {

    private val FEEDS = listOf(
        "https://www.thedailystar.net/frontpage/rss.xml",
        "https://www.prothomalo.com/feed/"
    )

    suspend fun fetchBrief(): String? = withContext(Dispatchers.IO) {
        for (feed in FEEDS) {
            val result = runCatching { fetchTopHeadlines(feed, 3) }.getOrNull()
            if (!result.isNullOrBlank()) return@withContext result
        }
        null
    }

    private fun fetchTopHeadlines(feedUrl: String, count: Int): String? {
        val conn = URL(feedUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) JARVIS/1.8")
        try {
            if (conn.responseCode != 200) return null
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(conn.inputStream, null)
            val titles = mutableListOf<String>()
            var insideItem = false
            var insideTitle = false
            val sb = StringBuilder()
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT && titles.size < count) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item", "entry" -> { insideItem = true; sb.setLength(0) }
                        "title" -> if (insideItem) { insideTitle = true; sb.setLength(0) }
                    }
                    XmlPullParser.TEXT -> if (insideTitle) sb.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "title" -> if (insideTitle) {
                            insideTitle = false
                            val t = sb.toString().trim()
                            if (t.isNotBlank()) titles.add(t)
                        }
                        "item", "entry" -> insideItem = false
                    }
                }
                eventType = parser.next()
            }
            if (titles.isEmpty()) return null
            return titles.joinToString(". And also: ")
        } finally {
            conn.disconnect()
        }
    }
}
