package com.jarvis.assistant.agent.planning

import org.junit.Assert.*
import org.junit.Test

class TaskPlannerTest {
    @Test fun singleStepIsNotMultiStep() { assertFalse(TaskPlanner.isMultiStep("set a timer")) }
    @Test fun thenIndicatesMultiStep() { assertTrue(TaskPlanner.isMultiStep("search for flights then book the cheapest")) }
    @Test fun planReturnsStepsForMultiStep() {
        val plan = TaskPlanner.plan("find the weather then suggest clothing")
        assertNotNull(plan); assertTrue(plan!!.steps.size >= 2)
    }
    @Test fun planReturnsNullForSingleStep() { assertNull(TaskPlanner.plan("what time is it")) }
    @Test fun planBlockIsEmptyForNullPlan() { assertEquals("", TaskPlanner.planBlock(null)) }
}
