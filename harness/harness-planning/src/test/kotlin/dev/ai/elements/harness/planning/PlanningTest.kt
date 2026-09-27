package dev.ai.elements.harness.planning

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {
    private val planning = Planning()
    private suspend fun call(name: String, args: JsonObject = JsonObject(emptyMap())) = planning.tools().single { it.name == name }.execute(args)
    private fun step(content: String, status: String, id: String? = null) = buildJsonObject { id?.let { put("id", it) }; put("content", content); put("status", status) }

    @Test
    fun writeReadAndUpdate_withHarnessTexts() = runBlocking<Unit> {
        assertNull(planning.context())
        val written = call("write_plan", buildJsonObject { put("items", JsonArray(listOf(step("Read", "completed", "a"), step("Search", "in_progress", "b"), step("Write", "pending", "c")))) })
        assertEquals("Plan updated: 3 step(s).\n\n1. [x] Read\n2. [~] Search\n3. [ ] Write\n(1/3 completed)", written)
        assertEquals("Current plan:\n1. [x] [a] Read\n2. [~] [b] Search\n3. [ ] [c] Write\n\nSummary: 1 completed, 1 in progress, 1 pending", call("read_plan"))
        assertEquals("<plan-reminder>\nYour current plan (keep it updated with the planning tools):\n\n1. [x] Read\n2. [~] Search\n3. [ ] Write\n(1/3 completed)\n</plan-reminder>", planning.context())

        // A batch is all-or-nothing.
        val bad = call("update_task_statuses", buildJsonObject { put("updates", JsonArray(listOf(buildJsonObject { put("task_id", "b"); put("status", "completed") }, buildJsonObject { put("task_id", "zz"); put("status", "completed") }))) })
        assertEquals("No changes applied. Errors:\n- Step with id 'zz' not found.", bad)
        val ok = call("update_task_statuses", buildJsonObject { put("updates", JsonArray(listOf(buildJsonObject { put("task_id", "b"); put("status", "completed") }, buildJsonObject { put("task_id", "c"); put("status", "in_progress") }))) })
        assertEquals("Updated 2 step(s):\n- [b] Search -> completed\n- [c] Write -> in_progress", ok)
        call("update_task_status", buildJsonObject { put("task_id", "c"); put("status", "completed") })
        assertTrue(call("read_plan").endsWith("All steps are completed. Do NOT call read_plan again -- respond to the user with a summary instead."))
        assertTrue(call("add_task", buildJsonObject { put("content", "Ship") }).startsWith("Added step 'Ship' with id: "))
        assertEquals("Step with id 'nope' not found.", call("remove_task", buildJsonObject { put("task_id", "nope") }))
    }

    @Test
    fun planData_matchesTheAiElementsPlanShape() {
        val data = planning.planData(listOf(PlanItem("a", "Read", TaskStatus.COMPLETED), PlanItem("b", "Search", TaskStatus.IN_PROGRESS, "Searching")))
        assertEquals("1/2 steps done", data["description"]!!.jsonPrimitive.content)
        assertEquals(listOf("Read" to "complete", "Searching" to "active"), data["steps"]!!.jsonArray.map { it.jsonObject["label"]!!.jsonPrimitive.content to it.jsonObject["status"]!!.jsonPrimitive.content })
    }

    @Test
    fun duplicateIds_areRefused() = runBlocking<Unit> {
        val refused = call("write_plan", buildJsonObject { put("items", JsonArray(listOf(step("A", "pending", "x"), step("B", "pending", "x")))) })
        assertEquals("Plan not updated: Duplicate step ids: x. Every step needs a unique id.", refused)
        assertEquals("No plan yet. Use write_plan to create one.", call("read_plan"))
    }
}
