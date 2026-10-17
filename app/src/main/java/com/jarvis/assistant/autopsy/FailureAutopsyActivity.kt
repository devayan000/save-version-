package com.jarvis.assistant.autopsy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager

/** UI for Failure Autopsy: view, diagnose, export failure records. */
class FailureAutopsyActivity : AppCompatActivity() {

    private lateinit var store: FailureAutopsyStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = FailureAutopsyStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Failure Autopsy"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "Export All Reports"; setOnClickListener { exportReports() } })
        container.addView(Button(this).apply { text = "Clear All"; setOnClickListener {
            MaterialAlertDialogBuilder(this@FailureAutopsyActivity).setTitle("Clear All Records").setMessage("Delete all failure records?")
                .setPositiveButton("Delete") { _, _ -> store.clearAll(); refresh() }.setNegativeButton("Cancel", null).show()
        } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val records = store.getAll()
        if (records.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No failures recorded."; setPadding(0, 16, 0, 0) })
            return
        }
        for (record in records) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = record.operation + (if (record.resolved) " [Resolved]" else ""); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))
            card.addView(TextView(this).apply { text = "Category: " + record.errorCategory + " | " + dateStr; textSize = 13f; setPadding(0, 4, 0, 0) })
            card.addView(TextView(this).apply { text = record.errorMessage.take(100); textSize = 13f; setPadding(0, 2, 0, 0); setTextIsSelectable(true) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "View"; setOnClickListener { showDetail(record.id) } })
            btnRow.addView(Button(this).apply { text = "Copy Report"; setOnClickListener {
                val text = store.formatReport(record)
                val clip = android.content.ClipData.newPlainText("Failure Report", text)
                (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(this@FailureAutopsyActivity, "Report copied.", Toast.LENGTH_SHORT).show()
            } })
            if (!record.resolved) btnRow.addView(Button(this).apply { text = "Resolve"; setOnClickListener { store.markResolved(record.id); refresh() } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.deleteRecord(record.id); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showDetail(id: String) {
        val record = store.getAll().find { it.id == id } ?: return
        val report = store.formatReport(record)
        MaterialAlertDialogBuilder(this@FailureAutopsyActivity).setTitle("Failure Detail")
            .setMessage(report)
            .setPositiveButton("Close", null)
            .setNeutralButton("AI Diagnosis") { _, _ ->
                MaterialAlertDialogBuilder(this@FailureAutopsyActivity).setTitle("AI Diagnosis Consent")
                    .setMessage("This will share the diagnostic report (with sensitive content redacted) with your configured AI provider for analysis. Do you consent?")
                    .setPositiveButton("Consent") { _, _ ->
                        val clip = android.content.ClipData.newPlainText("Failure Report for AI", report)
                        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(this@FailureAutopsyActivity, "Report copied. Paste into your AI provider for diagnosis.", Toast.LENGTH_LONG).show()
                    }.setNegativeButton("Cancel", null).show()
            }.show()
    }

    private fun exportReports() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_failure_reports.json") }
        startActivityForResult(intent, 7001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7001 && resultCode == Activity.RESULT_OK && data?.data != null) {
            contentResolver.openOutputStream(data.data!!)?.use { it.write(store.exportReport().toByteArray()) }
            Toast.makeText(this, "Reports exported.", Toast.LENGTH_SHORT).show()
        }
    }
}
