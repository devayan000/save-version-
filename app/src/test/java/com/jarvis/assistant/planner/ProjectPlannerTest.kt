package com.jarvis.assistant.planner

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectPlannerTest {
    @Test fun taskStatusEnumHasFourStates() {
        assertEquals(4, TaskStatus.values().size)
        assertNotNull(TaskStatus.valueOf("NOT_STARTED"))
    }

    @Test fun taskJsonRoundTrip() {
        val task = Task("t1", "Write tests", "Unit tests", TaskStatus.IN_PROGRESS.name, listOf("dep1"), "All pass", 0)
        val json = task.toJson()
        val restored = Task.fromJson(json)
        assertEquals(task.title, restored.title)
        assertEquals(task.status, restored.status)
        assertEquals(1, restored.dependencies.size)
        assertEquals("dep1", restored.dependencies[0])
    }

    @Test fun milestoneJsonRoundTrip() {
        val ms = Milestone("m1", "Phase 1", listOf(Task("t1", "Setup")))
        val restored = Milestone.fromJson(ms.toJson())
        assertEquals(ms.title, restored.title)
        assertEquals(1, restored.tasks.size)
        assertEquals("Setup", restored.tasks[0].title)
    }

    @Test fun projectPlanJsonRoundTrip() {
        val plan = ProjectPlan("p1", "App", "Ship v1", "Budget", "2026-12-31", "Tests pass",
            listOf(Milestone("m1", "Phase 1", listOf(Task("t1", "Setup")))), "123", "456")
        val restored = ProjectPlan.fromJson(plan.toJson())
        assertEquals(plan.name, restored.name)
        assertEquals(1, restored.milestones.size)
        assertEquals(1, restored.milestones[0].tasks.size)
    }

    @Test fun dependencyAnalysisDetectsBlocked() {
        val task1 = Task("t1", "Design", status = "NOT_STARTED")
        val task2 = Task("t2", "Build", dependencies = listOf("t1"), status = "IN_PROGRESS")
        val task3 = Task("t3", "Test", dependencies = listOf("nonexistent"))
        val store = ProjectPlanStore(org.robolectric.RuntimeEnvironment.getApplication())
        store.createPlan("Test", "Goal", "", "", "")
        val plan = store.getAll().first()
        store.addMilestone(plan.id, "M1")
        val withMs = store.get(plan.id)!!
        store.addTask(plan.id, withMs.milestones[0].id, "Design", "", emptyList(), "")
        val withTask = store.get(plan.id)!!
        val designId = withTask.milestones[0].tasks.first { it.title == "Design" }.id
        // Build depends on the still-unfinished Design task; Test depends on a task that doesn't exist
        store.addTask(plan.id, withTask.milestones[0].id, "Build", "", listOf(designId), "")
        val withBuild = store.get(plan.id)!!
        // Verify the store persists across calls
        assertNotNull(store.get(plan.id))
        store.addTask(plan.id, withBuild.milestones[0].id, "Test", "", listOf("nonexistent"), "")
        val warnings = store.analyzePlan(plan.id)
        assertTrue(warnings.any { it.contains("blocked") || it.contains("unknown") })
    }
}

