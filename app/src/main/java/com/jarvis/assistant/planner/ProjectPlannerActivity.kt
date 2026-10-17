package com.jarvis.assistant.planner

import android.app.Activity
import android.content.Intent
import android.net.Uri
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

/** UI for creating and managing project plans with milestones and tasks. */
class ProjectPlannerActivity : AppCompatActivity() {

    private lateinit var store: ProjectPlanStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = ProjectPlanStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "AI Project Planner"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { text = "New Project"; setOnClickListener { showCreateDialog() } })
        container.addView(Button(this).apply { text = "Export Plans"; setOnClickListener { exportPlans() } })
        container.addView(Button(this).apply { text = "Import Plans"; setOnClickListener { openImportFile() } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val plans = store.getAll()
        if (plans.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No projects yet. Tap 'New Project' to start."; setPadding(0, 16, 0, 0) })
            return
        }
        for (plan in plans) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundResource(android.R.color.white); setPadding(24, 24, 24, 24) }
            val titleView = TextView(this).apply { text = plan.name; textSize = 16f; setOnClickListener { showPlanDetail(plan.id) } }
            card.addView(titleView)
            val goalView = TextView(this).apply { text = "Goal: " + plan.goal; textSize = 13f; setPadding(0, 4, 0, 0) }
            card.addView(goalView)
            val taskCount = plan.milestones.flatMap { it.tasks }.size
            val completed = plan.milestones.flatMap { it.tasks }.count { it.status == "COMPLETED" }
            card.addView(TextView(this).apply { text = "Tasks: $completed/$taskCount completed"; textSize = 13f; setPadding(0, 4, 0, 0) })
            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { text = "Open"; setOnClickListener { showPlanDetail(plan.id) } })
            btnRow.addView(Button(this).apply { text = "Delete"; setOnClickListener { store.delete(plan.id); refresh() } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showCreateDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val name = EditText(ctx).apply { hint = "Project name"; container.addView(this) }
        val goal = EditText(ctx).apply { hint = "Goal"; container.addView(this) }
        val constraints = EditText(ctx).apply { hint = "Constraints (optional)"; container.addView(this) }
        val deadline = EditText(ctx).apply { hint = "Deadline (optional, e.g. 2026-12-31)"; inputType = InputType.TYPE_CLASS_DATETIME; container.addView(this) }
        val criteria = EditText(ctx).apply { hint = "Success criteria"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("New Project").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Create") { _, _ ->
                if (name.text.isBlank() || goal.text.isBlank()) { Toast.makeText(ctx, "Name and goal required.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                store.createPlan(name.text.toString().trim(), goal.text.toString().trim(), constraints.text.toString().trim(), deadline.text.toString().trim(), criteria.text.toString().trim())
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showPlanDetail(planId: String) {
        val plan = store.get(planId) ?: return
        val ctx = this
        val items = plan.milestones.map { "Milestone: " + it.title + " (" + it.tasks.size + " tasks)" }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(plan.name).setItems(items) { _, which ->
            showMilestoneDetail(planId, plan.milestones[which].id)
        }.setNeutralButton("Add Milestone") { _, _ -> showAddMilestone(planId) }
            .setPositiveButton("Analyze") { _, _ -> showAnalysis(planId) }
            .setNegativeButton("Close", null).create()
        dialog.show()
    }

    private fun showAddMilestone(planId: String) {
        val ctx = this
        val et = EditText(ctx).apply { hint = "Milestone title" }
        MaterialAlertDialogBuilder(ctx).setTitle("Add Milestone").setView(et)
            .setPositiveButton("Add") { _, _ -> if (et.text.isNotBlank()) { store.addMilestone(planId, et.text.toString().trim()); showPlanDetail(planId) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showMilestoneDetail(planId: String, milestoneId: String) {
        val plan = store.get(planId) ?: return
        val milestone = plan.milestones.find { it.id == milestoneId } ?: return
        val ctx = this
        val items = milestone.tasks.map { it.title + " [" + it.status + "]" }.toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(milestone.title).setItems(items) { _, which ->
            showTaskActions(planId, milestoneId, milestone.tasks[which].id)
        }.setNeutralButton("Add Task") { _, _ -> showAddTask(planId, milestoneId) }
            .setPositiveButton("Back") { _, _ -> showPlanDetail(planId) }
            .setNegativeButton("Close", null).show()
    }

    private fun showAddTask(planId: String, milestoneId: String) {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val title = EditText(ctx).apply { hint = "Task title"; container.addView(this) }
        val desc = EditText(ctx).apply { hint = "Description"; container.addView(this) }
        val deps = EditText(ctx).apply { hint = "Dependency IDs (comma-separated, optional)"; container.addView(this) }
        val criteria = EditText(ctx).apply { hint = "Acceptance criteria"; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Add Task").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Add") { _, _ ->
                if (title.text.isNotBlank()) {
                    val depList = deps.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    store.addTask(planId, milestoneId, title.text.toString().trim(), desc.text.toString().trim(), depList, criteria.text.toString().trim())
                    showMilestoneDetail(planId, milestoneId)
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showTaskActions(planId: String, milestoneId: String, taskId: String) {
        val ctx = this
        val statuses = arrayOf("NOT_STARTED", "IN_PROGRESS", "BLOCKED", "COMPLETED")
        MaterialAlertDialogBuilder(ctx).setTitle("Task Action").setItems(arrayOf("Set Status", "Delete Task", "Cancel")) { _, which ->
            when (which) {
                0 -> MaterialAlertDialogBuilder(ctx).setTitle("Set Status").setItems(statuses) { _, s -> store.updateTaskStatus(planId, milestoneId, taskId, statuses[s]); showMilestoneDetail(planId, milestoneId) }.show()
                1 -> { store.deleteTask(planId, milestoneId, taskId); showMilestoneDetail(planId, milestoneId) }
            }
        }.show()
    }

    private fun showAnalysis(planId: String) {
        val warnings = store.analyzePlan(planId)
        val msg = if (warnings.isEmpty()) "No issues found. Plan looks good." else warnings.joinToString("\n")
        MaterialAlertDialogBuilder(this).setTitle("Plan Analysis").setMessage(msg).setPositiveButton("Close", null).show()
    }

    private fun exportPlans() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; putExtra(Intent.EXTRA_TITLE, "jarvis_plans.json") }
        startActivityForResult(intent, 1001)
    }

    private fun openImportFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 1002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            1001 -> { contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }; Toast.makeText(this, "Exported.", Toast.LENGTH_SHORT).show() }
            1002 -> { val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return; if (store.importJson(json)) { refresh(); Toast.makeText(this, "Imported.", Toast.LENGTH_SHORT).show() } else Toast.makeText(this, "Import failed.", Toast.LENGTH_SHORT).show() }
        }
    }
}
