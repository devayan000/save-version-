package com.jarvis.assistant.browser

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager
import org.json.JSONObject

/** Browser agent with page understanding, login guidance, and authorized actions.
 *  Treats all web content as untrusted. Never logs passwords or session tokens. */
class BrowserAgentActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var statusText: TextView
    private lateinit var pageContent: TextView
    private var pageText: String = ""

    inner class JarvisWebInterface {
        @JavascriptInterface
        fun onPageParsed(title: String, text: String, linkCount: Int, hasLoginForm: Boolean) {
            runOnUiThread {
                statusText.text = "Page: $title | Links: $linkCount" + (if (hasLoginForm) " | Login form detected" else "")
                pageText = text
                pageContent.text = text.take(2000) + if (text.length > 2000) "\n...(truncated)" else ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        TextView(this).apply { text = "AI Browser Agent"; textSize = 18f; setPadding(0, 0, 0, 8); container.addView(this) }

        // URL bar + search
        val urlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlBar = EditText(this).apply { hint = "Search or enter URL"; setSingleLine(); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        urlRow.addView(urlBar)
        urlRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Go"; setOnClickListener { handleUrlInput(urlBar.text.toString().trim()) } })
        container.addView(urlRow)

        statusText = TextView(this).apply { text = "Enter a URL or search query"; textSize = 13f; setPadding(0, 8, 0, 8) }
        container.addView(statusText)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(JarvisWebInterface(), "JarvisWeb")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    urlBar.setText(url ?: "")
                    // Inject JS to extract page info — treats content as untrusted
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                var title = document.title || '';
                                var text = document.body ? document.body.innerText.substring(0, 5000) : '';
                                var links = document.querySelectorAll('a').length;
                                var hasLogin = !!document.querySelector('input[type="password"]');
                                JarvisWeb.onPageParsed(title, text, links, hasLogin);
                            } catch(e) { JarvisWeb.onPageParsed('Error', '', 0, false); }
                        })();
                    """, null)
                }
            }
        }
        container.addView(webView)

        pageContent = TextView(this).apply { textSize = 12f; setTextIsSelectable(true); setPadding(0, 8, 0, 8); maxLines = 6 }
        container.addView(pageContent)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Login Help"; setOnClickListener { showLoginHelp() } })
        btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Fill Form"; setOnClickListener { showFillFormDialog() } })
        btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Clear Data"; setOnClickListener { clearBrowsingData() } })
        btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Close"; setOnClickListener { finish() } })
        container.addView(btnRow)

        setContentView(container)
    }

    private fun handleUrlInput(input: String) {
        if (input.isBlank()) return
        val url = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else if (input.contains(".") && !input.contains(" ")) {
            "https://" + input
        } else {
            // Treat as search query — use Google search
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8")
        }
        urlBar.setText(url)
        webView.loadUrl(url)
    }

    private fun showLoginHelp() {
        val msg = "Login Guidance:\n\n" +
            "1. If a login form is detected, Jarvis will NOT fill passwords automatically.\n" +
            "2. Use Android Credential Manager or your password manager to autofill credentials.\n" +
            "3. Never paste passwords into AI prompts.\n" +
            "4. For 2FA/CAPTCHA, complete them manually — Jarvis cannot bypass these.\n" +
            "5. Sessions are kept in this WebView only. Clear data when done."
        MaterialAlertDialogBuilder(this).setTitle("Login Help").setMessage(msg).setPositiveButton("Got It", null).show()
    }

    private fun showFillFormDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val fieldName = EditText(ctx).apply { hint = "Field name or selector (e.g. search, #email)"; container.addView(this) }
        val value = EditText(ctx).apply { hint = "Value to fill"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Fill Form Field").setView(container)
            .setPositiveButton("Fill") { _, _ ->
                val field = fieldName.text.toString().trim()
                val v = value.text.toString()
                if (field.isNotBlank()) {
                    // Show confirmation before any form submission
                    MaterialAlertDialogBuilder(ctx).setTitle("Confirm Action")
                        .setMessage("Fill '$field' with '$v'? This will interact with the page but will NOT submit.")
                        .setPositiveButton("Fill") { _, _ ->
                            val js = """
                                (function() {
                                    var el = document.querySelector('[name="$field"], #$field') || document.getElementById('$field');
                                    if (el) { el.value = '$v'; el.dispatchEvent(new Event('input', {bubbles: true})); }
                                })();
                            """
                            webView.evaluateJavascript(js, null)
                            Toast.makeText(ctx, "Field filled. Review before submitting.", Toast.LENGTH_SHORT).show()
                        }.setNegativeButton("Cancel", null).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun clearBrowsingData() {
        MaterialAlertDialogBuilder(this).setTitle("Clear Browsing Data").setMessage("Clear cookies, cache, and WebView storage?")
            .setPositiveButton("Clear") { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
                Toast.makeText(this, "Browsing data cleared.", Toast.LENGTH_SHORT).show()
                webView.loadUrl("about:blank")
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
