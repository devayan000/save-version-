package com.jarvis.assistant.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local JSON store for project plans. Survives app restarts. */
class ProjectPlanStore(private val context: Context) {
    private fun file() = File(context.filesDir, "jarvis_project_plans.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun getAll(): List<ProjectPlan> {
        val arr = load()
        return (0 until arr.length()).map { ProjectPlan.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): ProjectPlan? = getAll().find { it.id == id }

    fun save(plan: ProjectPlan): ProjectPlan {
        val plans = getAll().toMutableList()
        val idx = plans.indexOfFirst { it.id == plan.id }
        val now = System.currentTimeMillis().toString()
        val updated = plan.copy(updatedDate = now, createdDate = if (plan.createdDate.isBlank()) now else plan.createdDate)
        if (idx >= 0) plans[idx] = updated else plans.add(updated)
        save(JSONArray(plans.map { it.toJson() }))
        return updated
    }

    fun delete(id: String) {
        val plans = getAll().filter { it.id != id }
        save(JSONArray(plans.map { it.toJson() }))
    }

    fun createPlan(name: String, goal: String, constraints: String, deadline: String, successCriteria: String): ProjectPlan {
        val plan = ProjectPlan(
            id = UUID.randomUUID().toString(), name = name, goal = goal,
            constraints = constraints, deadline = deadline, successCriteria = successCriteria,
            createdDate = System.currentTimeMillis().toString()
        )
        return save(plan)
    }

    fun addMilestone(planId: String, title: String): ProjectPlan? {
        val plan = get(planId) ?: return null
        val milestone = Milestone(UUID.randomUUID().toString(), title)
        return save(plan.copy(milestones = plan.milestones + milestone))
    }

    fun addTask(planId: String, milestoneId: String, title: String, description: String, dependencies: List<String>, acceptanceCriteria: String): ProjectPlan? {
        val plan = get(planId) ?: return null
        val milestones = plan.milestones.map { ms ->
            if (ms.id == milestoneId) {
                val task = Task(UUID.randomUUID().toString(), title, description, TaskStatus.NOT_STARTED.name, dependencies, acceptanceCriteria, ms.tasks.size)
                ms.copy(tasks = ms.tasks + task)
            } else ms
        }
        return save(plan.copy(milestones = milestones))
    }

    fun updateTaskStatus(planId: String, milestoneId: String, taskId: String, status: String): ProjectPlan? {
        val plan = get(planId) ?: return null
        val milestones = plan.milestones.map { ms ->
            if (ms.id == milestoneId) ms.copy(tasks = ms.tasks.map { if (it.id == taskId) it.copy(status = status) else it })
            else ms
        }
        return save(plan.copy(milestones = milestones))
    }

    fun deleteTask(planId: String, milestoneId: String, taskId: String): ProjectPlan? {
        val plan = get(planId) ?: return null
        val milestones = plan.milestones.map { ms ->
            if (ms.id == milestoneId) ms.copy(tasks = ms.tasks.filter { it.id != taskId })
            else ms
        }
        return save(plan.copy(milestones = milestones))
    }

    /** Identify tasks blocked by incomplete dependencies or risky assumptions. */
    fun analyzePlan(planId: String): List<String> {
        val plan = get(planId) ?: return emptyList()
        val warnings = mutableListOf<String>()
        val allTasks = plan.milestones.flatMap { it.tasks }
        val taskMap = allTasks.associateBy { it.id }
        for (task in allTasks) {
            for (depId in task.dependencies) {
                val dep = taskMap[depId]
                if (dep == null) warnings.add("Task '" + task.title + "' depends on unknown task.")
                else if (dep.status != TaskStatus.COMPLETED.name) warnings.add("Task '" + task.title + "' is blocked by incomplete '" + dep.title + "'.")
            }
            if (task.acceptanceCriteria.isBlank()) warnings.add("Task '" + task.title + "' has no acceptance criteria.")
        }
        return warnings
    }

    fun exportJson(): String = load().toString()

    fun importJson(json: String): Boolean {
        return try {
            val arr = JSONArray(json)
            save(arr)
            true
        } catch (e: Exception) { false }
    }
}
