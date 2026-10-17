package com.jarvis.assistant.decision

import android.app.Activity
import android.content.Intent
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

/** UI for Decision DNA: record, search, edit, export decisions. */
class DecisionDnaActivity : AppCompatActivity() {

    private lateinit var store: DecisionStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = DecisionStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Decision DNA"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "Record Decision"; setOnClickListener { showCreateDialog() } })
        val searchBox = EditText(this).apply { hint = "Search decisions..."; setSingleLine() }
        container.addView(searchBox)
        container.addView(Button(this).apply { text = "Search"; setOnClickListener { refresh(searchBox.text.toString()) } })
        container.addView(Button(this).apply { text = "Export"; setOnClickListener { exportDecisions() } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh(searchQuery: String = "") {
        listContainer.removeAllViews()
        val records = if (searchQuery.isBlank()) store.getAll() else store.search(searchQuery)
        if (records.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No decisions recorded yet."; setPadding(0, 16, 0, 0) })
            return
        }
        for (record in records) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = record.title; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            if (record.context.isNotBlank()) card.addView(TextView(this).apply { text = "Context: " + record.context; textSize = 13f; setPadding(0, 4, 0, 0) })
            if (record.options.isNotEmpty()) card.addView(TextView(this).apply { text = "Options: " + record.options.joinToString(", "); textSize = 13f; setPadding(0, 2, 0, 0) })
            if (record.chosenOption.isNotBlank()) card.addView(TextView(this).apply { text = "Chose: " + record.chosenOption; textSize = 13f; setPadding(0, 2, 0, 0) })
            if (record.reasoning.isNotBlank()) card.addView(TextView(this).apply { text = "Reasoning: " + record.reasoning; textSize = 13f; setPadding(0, 2, 0, 0) })
            if (record.tags.isNotEmpty()) card.addView(TextView(this).apply { text = "Tags: " + record.tags.joinToString(", "); textSize = 12f; setPadding(0, 2, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "Edit"; setOnClickListener { showEditDialog(record.id) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.delete(record.id); refresh(searchQuery) } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showCreateDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val title = EditText(ctx).apply { hint = "Decision title"; container.addView(this) }
        val contextInput = EditText(ctx).apply { hint = "Context / situation"; container.addView(this) }
        val options = EditText(ctx).apply { hint = "Options considered (comma-separated)"; container.addView(this) }
        val chosen = EditText(ctx).apply { hint = "Chosen option"; container.addView(this) }
        val reasoning = EditText(ctx).apply { hint = "Reasoning"; container.addView(this) }
        val tags = EditText(ctx).apply { hint = "Tags (comma-separated)"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Record Decision").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                if (title.text.isBlank()) { Toast.makeText(ctx, "Title required.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                store.create(
                    title.text.toString().trim(), contextInput.text.toString().trim(),
                    options.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() },
                    chosen.text.toString().trim(), reasoning.text.toString().trim(),
                    tags.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showEditDialog(id: String) {
        val record = store.get(id) ?: return
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val title = EditText(ctx).apply { hint = "Decision title"; setText(record.title); container.addView(this) }
        val contextInput = EditText(ctx).apply { hint = "Context"; setText(record.context); container.addView(this) }
        val options = EditText(ctx).apply { hint = "Options (comma-separated)"; setText(record.options.joinToString(",")); container.addView(this) }
        val chosen = EditText(ctx).apply { hint = "Chosen option"; setText(record.chosenOption); container.addView(this) }
        val reasoning = EditText(ctx).apply { hint = "Reasoning"; setText(record.reasoning); container.addView(this) }
        val tags = EditText(ctx).apply { hint = "Tags (comma-separated)"; setText(record.tags.joinToString(",")); container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Edit Decision").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                store.update(id, title.text.toString().trim(), contextInput.text.toString().trim(),
                    options.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() },
                    chosen.text.toString().trim(), reasoning.text.toString().trim(),
                    tags.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() })
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun exportDecisions() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_decisions.json") }
        startActivityForResult(intent, 5001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5001 && resultCode == Activity.RESULT_OK && data?.data != null) {
            contentResolver.openOutputStream(data.data!!)?.use { it.write(store.exportJson().toByteArray()) }
            Toast.makeText(this, "Exported.", Toast.LENGTH_SHORT).show()
        }
    }
}
