package com.jarvis.assistant.planner

import org.json.JSONArray
import org.json.JSONObject

/** A project plan with milestones, tasks, dependencies, and acceptance criteria. */
data class ProjectPlan(
    val id: String,
    val name: String,
    val goal: String,
    val constraints: String = "",
    val deadline: String = "",
    val successCriteria: String = "",
    val milestones: List<Milestone> = emptyList(),
    val createdDate: String = "",
    val updatedDate: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("goal", goal)
        put("constraints", constraints); put("deadline", deadline)
        put("successCriteria", successCriteria)
        put("createdDate", createdDate); put("updatedDate", updatedDate)
        put("milestones", JSONArray(milestones.map { it.toJson() }))
    }
    companion object {
        fun fromJson(o: JSONObject) = ProjectPlan(
            o.getString("id"), o.getString("name"), o.getString("goal"),
            o.optString("constraints", ""), o.optString("deadline", ""),
            o.optString("successCriteria", ""),
            (0 until o.getJSONArray("milestones").length()).map { Milestone.fromJson(o.getJSONArray("milestones").getJSONObject(it)) },
            o.optString("createdDate", ""), o.optString("updatedDate", "")
        )
    }
}

data class Milestone(
    val id: String,
    val title: String,
    val tasks: List<Task> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title)
        put("tasks", JSONArray(tasks.map { it.toJson() }))
    }
    companion object {
        fun fromJson(o: JSONObject) = Milestone(
            o.getString("id"), o.getString("title"),
            (0 until o.getJSONArray("tasks").length()).map { Task.fromJson(o.getJSONArray("tasks").getJSONObject(it)) }
        )
    }
}

enum class TaskStatus { NOT_STARTED, IN_PROGRESS, BLOCKED, COMPLETED }

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String = TaskStatus.NOT_STARTED.name,
    val dependencies: List<String> = emptyList(),
    val acceptanceCriteria: String = "",
    val order: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("description", description)
        put("status", status)
        put("dependencies", JSONArray(dependencies))
        put("acceptanceCriteria", acceptanceCriteria)
        put("order", order)
    }
    companion object {
        private fun stringList(o: JSONObject, key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it, "") }
        }
        fun fromJson(o: JSONObject) = Task(
            o.getString("id"), o.getString("title"), o.optString("description", ""),
            o.optString("status", TaskStatus.NOT_STARTED.name),
            stringList(o, "dependencies"),
            o.optString("acceptanceCriteria", ""),
            o.optInt("order", 0)
        )
    }
}
