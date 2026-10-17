package com.jarvis.assistant.promptlab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager

/** UI for the Prompt Evolution Lab: create, version, compare, rate prompts. */
class PromptLabActivity : AppCompatActivity() {

    private lateinit var store: PromptLabStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = PromptLabStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Prompt Evolution Lab"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "New Prompt"; setOnClickListener { showCreateDialog() } })
        container.addView(Button(this).apply { text = "Export Collection"; setOnClickListener { exportCollection() } })
        container.addView(Button(this).apply { text = "Import Collection"; setOnClickListener { openImportFile() } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val prompts = store.getAll()
        if (prompts.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No prompts yet."; setPadding(0, 16, 0, 0) })
            return
        }
        for (entry in prompts) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = entry.name + " (" + entry.versions.size + " versions)"; textSize = 16f })
            if (entry.tags.isNotEmpty()) card.addView(TextView(this).apply { text = "Tags: " + entry.tags.joinToString(", "); textSize = 13f; setPadding(0, 4, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "Open"; setOnClickListener { showPromptDetail(entry.id) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.deletePrompt(entry.id); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showCreateDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val name = EditText(ctx).apply { hint = "Prompt name"; container.addView(this) }
        val content = EditText(ctx).apply { hint = "Prompt content"; minLines = 4; container.addView(this) }
        val tags = EditText(ctx).apply { hint = "Tags (comma-separated)"; container.addView(this) }
        val taskType = EditText(ctx).apply { hint = "Task type (optional)"; container.addView(this) }
        val provider = EditText(ctx).apply { hint = "Provider/model (optional)"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("New Prompt").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Create") { _, _ ->
                if (name.text.isBlank() || content.text.isBlank()) { Toast.makeText(ctx, "Name and content required.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val tagList = tags.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                store.createPrompt(name.text.toString().trim(), content.text.toString(), tagList, taskType.text.toString().trim(), provider.text.toString().trim())
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showPromptDetail(promptId: String) {
        val entry = store.get(promptId) ?: return
        val ctx = this
        val items = entry.versions.map { it.versionLabel + " [rating: " + it.rating + "]" + (if (it.notes.isNotBlank()) " — " + it.notes else "") }.toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(entry.name).setItems(items) { _, which -> showVersionDetail(promptId, entry.versions[which].id) }
            .setNeutralButton("Add Version") { _, _ -> showAddVersion(promptId) }
            .setPositiveButton("Compare") { _, _ -> showCompare(promptId) }
            .setNegativeButton("Close", null).show()
    }

    private fun showAddVersion(promptId: String) {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val content = EditText(ctx).apply { hint = "New version content"; minLines = 4; container.addView(this) }
        val notes = EditText(ctx).apply { hint = "Notes (optional)"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Add Version").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Add") { _, _ -> if (content.text.isNotBlank()) { store.addVersion(promptId, content.text.toString(), notes.text.toString().trim()); showPromptDetail(promptId) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showVersionDetail(promptId: String, versionId: String) {
        val entry = store.get(promptId) ?: return
        val version = entry.versions.find { it.id == versionId } ?: return
        val ctx = this
        MaterialAlertDialogBuilder(ctx).setTitle(version.versionLabel).setMessage(version.content + "\n\nNotes: " + version.notes + "\nOutcome: " + version.outcome)
            .setNeutralButton("Duplicate") { _, _ -> store.duplicateVersion(promptId, versionId); showPromptDetail(promptId) }
            .setPositiveButton("Rate") { _, _ -> showRateDialog(promptId, versionId) }
            .setNegativeButton("Close", null).show()
    }

    private fun showRateDialog(promptId: String, versionId: String) {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val rating = EditText(ctx).apply { hint = "Rating (1-5)"; inputType = InputType.TYPE_CLASS_NUMBER; container.addView(this) }
        val outcome = EditText(ctx).apply { hint = "Outcome / evaluation"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Rate Version").setView(container)
            .setPositiveButton("Save") { _, _ ->
                val r = rating.text.toString().toIntOrNull() ?: 0
                if (r in 1..5) { store.rateVersion(promptId, versionId, r, outcome.text.toString().trim()); Toast.makeText(ctx, "Rated.", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(ctx, "Rating must be 1-5.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showCompare(promptId: String) {
        val entry = store.get(promptId) ?: return
        if (entry.versions.size < 2) { Toast.makeText(this, "Need at least 2 versions to compare.", Toast.LENGTH_SHORT).show(); return }
        val ctx = this
        val labels = entry.versions.map { it.versionLabel }.toTypedArray()
        var first = 0
        MaterialAlertDialogBuilder(ctx).setTitle("Select first version").setItems(labels) { _, f -> first = f
            MaterialAlertDialogBuilder(ctx).setTitle("Select second version").setItems(labels) { _, s ->
                val diff = PromptDiff.diff(entry.versions[first].content, entry.versions[s].content)
                val sb = StringBuilder()
                for (line in diff) {
                    val prefix = when (line.type) { PromptDiff.DiffType.ADDED -> "+ "; PromptDiff.DiffType.REMOVED -> "- "; else -> "  " }
                    sb.append(prefix).append(line.text).append("\n")
                }
                MaterialAlertDialogBuilder(ctx).setTitle("Diff: " + entry.versions[first].versionLabel + " vs " + entry.versions[s].versionLabel)
                    .setMessage(sb.toString()).setPositiveButton("Close", null).show()
            }.show()
        }.show()
    }

    private fun exportCollection() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_prompts.json") }
        startActivityForResult(intent, 2001)
    }

    private fun openImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 2002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            2001 -> { contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }; Toast.makeText(this, "Exported.", Toast.LENGTH_SHORT).show() }
            2002 -> { val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return; if (store.importJson(json)) { refresh(); Toast.makeText(this, "Imported.", Toast.LENGTH_SHORT).show() } else Toast.makeText(this, "Import failed.", Toast.LENGTH_SHORT).show() }
        }
    }
}
