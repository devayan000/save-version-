package com.jarvis.assistant.developer

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.security.CreatorAuthenticationService
import com.jarvis.assistant.security.CreatorRepoAnalyzer
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DEV STYLE: Creator-only GitHub repository analyzer.
 * Opens only after the creator key unlocks the Creator Profile. Paste a
 * repository link and Jarvis reads its structure, folders, languages and
 * README, then reports a full analysis. Read-only.
 */
class CreatorRepoActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (!CreatorAuthenticationService.isCreator(this)) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        ThemeManager.applyDynamicBackground(root)

        val title = TextView(this).apply {
            text = "REPOSITORY ANALYZER"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val hintText = TextView(this).apply {
            text = "Paste a GitHub repository link. I will read its structure, folders, languages and README, then give you a full analysis. Nothing in the repository is modified."
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "https://github.com/owner/repo"
            inputType = EditorInfo.TYPE_TEXT_VARIATION_URI
            maxLines = 2
        }
        val button = Button(this).apply { text = "Analyze repository" }
        val status = TextView(this).apply { textSize = 13f; setPadding(0, (8 * density).toInt(), 0, 0) }
        val result = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        val resultColumn = LinearLayout(this@CreatorRepoActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(result)
        }
        val scroll = ScrollView(this).apply { addView(resultColumn) }

        button.setOnClickListener {
            val repo = input.text.toString().trim()
            if (repo.isBlank()) {
                Toast.makeText(this@CreatorRepoActivity, "Paste a repository link first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            button.isEnabled = false
            status.text = "Reading and analyzing the repository..."
            scope.launch {
                val report = CreatorRepoAnalyzer.analyze(this@CreatorRepoActivity, repo)
                status.text = "Analysis complete."
                result.text = report
                button.isEnabled = true
            }
        }

        root.addView(title)
        root.addView(hintText)
        root.addView(input)
        root.addView(button)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
