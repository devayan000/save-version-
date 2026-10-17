package com.jarvis.assistant.debate

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager

/** UI for AI Debate Chamber. Shows consent before sending to AI provider. */
class DebateActivity : AppCompatActivity() {

    private lateinit var engine: DebateEngine
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        engine = DebateEngine(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "AI Debate Chamber"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "New Debate"; setOnClickListener { showNewDebateDialog() } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val sessions = engine.getAll()
        if (sessions.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No debates yet."; setPadding(0, 16, 0, 0) })
            return
        }
        for (session in sessions) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = session.topic; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            val status = when {
                session.proposerResponse.isBlank() -> "Not started"
                session.summary.isBlank() -> "In progress"
                else -> "Complete"
            }
            card.addView(TextView(this).apply { text = "Status: " + status; textSize = 13f; setPadding(0, 4, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "View"; setOnClickListener { showDebateDetail(session.id) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { engine.delete(session.id); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showNewDebateDialog() {
        val ctx = this
        val et = EditText(ctx).apply { hint = "Topic, question, or code to debate"; minLines = 3 }
        MaterialAlertDialogBuilder(ctx).setTitle("New Debate").setView(et)
            .setPositiveButton("Start") { _, _ ->
                if (et.text.isNotBlank()) {
                    val session = engine.createSession(et.text.toString().trim())
                    showConsentDialog(session.id)
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showConsentDialog(sessionId: String) {
        val ctx = this
        MaterialAlertDialogBuilder(ctx).setTitle("AI Provider Consent")
            .setMessage("This will send your topic to the configured AI provider to generate three perspectives (Proposer, Critic, Reviewer). If only one provider is configured, the same model will be used for all roles — this is NOT independent verification.\n\nDo you consent to sending this content?")
            .setPositiveButton("Consent & Generate") { _, _ -> showGenerateDialog(sessionId) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showGenerateDialog(sessionId: String) {
        val ctx = this
        val session = engine.get(sessionId) ?: return
        val (proposerPrompt, criticPrompt, reviewerPrompt) = engine.buildPrompts(session.topic)
        MaterialAlertDialogBuilder(ctx).setTitle("Debate Prompts Ready")
            .setMessage("Three role-based prompts have been generated. Copy them to use with your configured AI provider:\n\n" +
                "1. PROPOSER prompt (" + proposerPrompt.length + " chars)\n" +
                "2. CRITIC prompt (" + criticPrompt.length + " chars)\n" +
                "3. REVIEWER prompt (" + reviewerPrompt.length + " chars)\n\n" +
                "Tap a role to copy its prompt to clipboard.")
            .setItems(arrayOf("Copy Proposer Prompt", "Copy Critic Prompt", "Copy Reviewer Prompt")) { _, which ->
                val prompt = when (which) { 0 -> proposerPrompt; 1 -> criticPrompt; else -> reviewerPrompt }
                val clip = android.content.ClipData.newPlainText("Debate Prompt", prompt)
                (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(ctx, "Prompt copied. Paste into your AI provider.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Close", null).show()
    }

    private fun showDebateDetail(sessionId: String) {
        val session = engine.get(sessionId) ?: return
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val proposer = EditText(ctx).apply { hint = "Proposer response"; setText(session.proposerResponse); minLines = 2; container.addView(this) }
        val critic = EditText(ctx).apply { hint = "Critic response"; setText(session.criticResponse); minLines = 2; container.addView(this) }
        val reviewer = EditText(ctx).apply { hint = "Reviewer response"; setText(session.reviewerResponse); minLines = 2; container.addView(this) }
        val summary = EditText(ctx).apply { hint = "Summary"; setText(session.summary); minLines = 2; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Debate: " + session.topic).setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                engine.updateSession(session.copy(
                    proposerResponse = proposer.text.toString(), criticResponse = critic.text.toString(),
                    reviewerResponse = reviewer.text.toString(), summary = summary.text.toString()
                ))
                refresh()
            }.setNegativeButton("Cancel", null)
            .setNeutralButton("Copy Full Debate") { _, _ ->
                val text = engine.formatSession(engine.get(sessionId)!!)
                val clip = android.content.ClipData.newPlainText("Debate", text)
                (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(ctx, "Debate copied.", Toast.LENGTH_SHORT).show()
            }.show()
    }
}
