package com.jarvis.assistant.personality

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

/** "What Jarvis has learned about me" — view, edit, export, delete memories. */
class MemoryManagementActivity : AppCompatActivity() {

    private lateinit var store: ConversationMemoryStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = ConversationMemoryStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Memory Management"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "Export Memories"; setOnClickListener { exportMemories() } })
        container.addView(Button(this).apply { text = "Import Memories"; setOnClickListener { openImportFile() } })
        container.addView(Button(this).apply { text = "Clear All Memories"; setOnClickListener {
            MaterialAlertDialogBuilder(this@MemoryManagementActivity).setTitle("Clear All").setMessage("Delete all saved memories? This cannot be undone.")
                .setPositiveButton("Delete") { _, _ -> store.clearAllMemories(); refresh() }.setNegativeButton("Cancel", null).show()
        } })
        container.addView(Button(this).apply { text = "Clear Summaries"; setOnClickListener { store.clearSummaries(); Toast.makeText(this@MemoryManagementActivity, "Summaries cleared.", Toast.LENGTH_SHORT).show() } })
        container.addView(Button(this).apply { text = "Reset Emotional State"; setOnClickListener { PersonalityEngine.resetState(); Toast.makeText(this@MemoryManagementActivity, "Emotional state reset.", Toast.LENGTH_SHORT).show() } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val memories = store.getAllMemories()
        if (memories.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No memories saved yet."; setPadding(0, 16, 0, 0) })
            return
        }
        for (mem in memories) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = "[" + mem.type + "]" + (if (!mem.confirmed) " (unconfirmed)" else ""); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            card.addView(TextView(this).apply { text = mem.content; textSize = 13f; setPadding(0, 4, 0, 0); setTextIsSelectable(true) })
            if (mem.provenance.isNotBlank()) card.addView(TextView(this).apply { text = "Source: " + mem.provenance; textSize = 11f; setPadding(0, 2, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "Edit"; setOnClickListener { showEditDialog(mem.id, mem.content) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.deleteMemory(mem.id); refresh() } })
            if (!mem.confirmed) btnRow.addView(Button(this).apply { text = "Confirm"; setOnClickListener { store.updateMemory(mem.id, mem.content); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showEditDialog(id: String, current: String) {
        val ctx = this
        val et = EditText(ctx).apply { setText(current); minLines = 2 }
        MaterialAlertDialogBuilder(ctx).setTitle("Edit Memory").setView(et)
            .setPositiveButton("Save") { _, _ -> store.updateMemory(id, et.text.toString().trim()); refresh() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun exportMemories() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_memories.json") }
        startActivityForResult(intent, 3001)
    }

    private fun openImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 3002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            3001 -> { contentResolver.openOutputStream(uri)?.use { it.write(store.exportMemories().toByteArray()) }; Toast.makeText(this, "Exported.", Toast.LENGTH_SHORT).show() }
            3002 -> { val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return; if (store.importMemories(json)) { refresh(); Toast.makeText(this, "Imported.", Toast.LENGTH_SHORT).show() } else Toast.makeText(this, "Import failed.", Toast.LENGTH_SHORT).show() }
        }
    }
}
