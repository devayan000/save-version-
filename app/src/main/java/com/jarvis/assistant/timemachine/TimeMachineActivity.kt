package com.jarvis.assistant.timemachine

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
import com.jarvis.assistant.decision.DecisionStore
import com.jarvis.assistant.personality.ConversationMemoryStore
import com.jarvis.assistant.planner.ProjectPlanStore
import com.jarvis.assistant.promptlab.PromptLabStore
import com.jarvis.assistant.util.ThemeManager

/** UI for Memory Time Machine: snapshot, browse, compare, restore Jarvis-owned data. */
class TimeMachineActivity : AppCompatActivity() {

    private lateinit var store: SnapshotStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = SnapshotStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Memory Time Machine"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }

        container.addView(Button(this).apply { text = "Snapshot Prompts"; setOnClickListener { takeSnapshot(SnapshotStore.DataType.PROMPTS) } })
        container.addView(Button(this).apply { text = "Snapshot Plans"; setOnClickListener { takeSnapshot(SnapshotStore.DataType.PLANS) } })
        container.addView(Button(this).apply { text = "Snapshot Decisions"; setOnClickListener { takeSnapshot(SnapshotStore.DataType.DECISIONS) } })
        container.addView(Button(this).apply { text = "Snapshot Memories"; setOnClickListener { takeSnapshot(SnapshotStore.DataType.MEMORIES) } })
        container.addView(Button(this).apply { text = "Export Backup"; setOnClickListener { exportBackup() } })
        container.addView(Button(this).apply { text = "Import Backup"; setOnClickListener { openImportFile() } })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun takeSnapshot(type: SnapshotStore.DataType) {
        val data = when (type) {
            SnapshotStore.DataType.PROMPTS -> PromptLabStore(this).exportJson()
            SnapshotStore.DataType.PLANS -> ProjectPlanStore(this).exportJson()
            SnapshotStore.DataType.DECISIONS -> DecisionStore(this).exportJson()
            SnapshotStore.DataType.MEMORIES -> ConversationMemoryStore(this).exportMemories()
            SnapshotStore.DataType.SETTINGS -> "{}"
        }
        val label = type.name.lowercase() + " snapshot"
        store.takeSnapshot(type, label, data)
        Toast.makeText(this, "Snapshot saved.", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val snapshots = store.getSnapshots()
        if (snapshots.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No snapshots yet."; setPadding(0, 16, 0, 0) })
            return
        }
        for (snapshot in snapshots) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            card.addView(TextView(this).apply { text = snapshot.label; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snapshot.timestamp))
            card.addView(TextView(this).apply { text = "Type: " + snapshot.dataType + " | " + dateStr; textSize = 13f; setPadding(0, 4, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "Preview"; setOnClickListener { showPreview(snapshot.id) } })
            btnRow.addView(Button(this).apply { text = "Restore"; setOnClickListener { showRestoreDialog(snapshot.id) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.deleteSnapshot(snapshot.id); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showPreview(id: String) {
        val snapshot = store.getSnapshot(id) ?: return
        MaterialAlertDialogBuilder(this).setTitle("Preview: " + snapshot.label)
            .setMessage(snapshot.data.take(2000) + if (snapshot.data.length > 2000) "\n...(truncated)" else "")
            .setPositiveButton("Close", null).show()
    }

    private fun showRestoreDialog(id: String) {
        val snapshot = store.getSnapshot(id) ?: return
        MaterialAlertDialogBuilder(this).setTitle("Restore Snapshot")
            .setMessage("This will REPLACE current " + snapshot.dataType + " data with this snapshot. This cannot be undone.\n\nSnapshot: " + snapshot.label + "\n\nContinue?")
            .setPositiveButton("Restore") { _, _ ->
                val data = store.getRestoreData(id) ?: return@setPositiveButton
                when (snapshot.dataType) {
                    SnapshotStore.DataType.PROMPTS.name -> PromptLabStore(this).importJson(data)
                    SnapshotStore.DataType.PLANS.name -> ProjectPlanStore(this).importJson(data)
                    SnapshotStore.DataType.DECISIONS.name -> DecisionStore(this).importJson(data)
                    SnapshotStore.DataType.MEMORIES.name -> ConversationMemoryStore(this).importMemories(data)
                }
                Toast.makeText(this, "Restored.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_backup.json") }
        startActivityForResult(intent, 6001)
    }

    private fun openImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 6002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            6001 -> { contentResolver.openOutputStream(uri)?.use { it.write(store.exportBackup().toByteArray()) }; Toast.makeText(this, "Backup exported.", Toast.LENGTH_SHORT).show() }
            6002 -> {
                val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                if (store.importBackup(json)) { refresh(); Toast.makeText(this, "Backup imported.", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Import failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
