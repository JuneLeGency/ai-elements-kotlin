package dev.ai.elements.genui

import dev.ai.elements.genui.a2ui.A2uiCatalog
import dev.ai.elements.genui.a2ui.A2uiParseError
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.ExpressionParser
import dev.ai.elements.genui.a2ui.testDataModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.math.BigDecimal

/**
 * The official A2UI conformance suites (the YAML files of `conformance/core`) run against this renderer, as
 * the suite README asks of every SDK. Cases are skipped only for behaviour this renderer does not
 * own, listed in [skipped] with the reason.
 */
class A2uiConformanceTest {
    private val dir = File("src/test/resources/a2ui/conformance/core")

    @Suppress("UNCHECKED_CAST")
    private fun cases(file: String): List<Map<String, Any?>> = Yaml(org.yaml.snakeyaml.LoaderOptions().apply { nestingDepthLimit = 1000 }).load<List<Map<String, Any?>>>(File(dir, file).readText())

    /** Behaviour outside a renderer core, or modelled differently here. */
    private val skipped = mapOf(
        "expect_notified" to "per-path observer notification: reactivity here is Compose snapshot state",
        "validate" to "strict schema / catalog validation is the agent-side validator's job",
        "strictMode" to "strict-mode errors come from the schema validator (see above)",
    )

    @Test
    fun expressions() {
        var ran = 0
        cases("expressions.yaml").filter { it["action"] == "parse_expression_template" }.forEach { case ->
            val name = case["name"]
            val input = case["input"] as String
            val error = case["expect_error"] as? Map<*, *>
            if (error != null) {
                val thrown = runCatching { ExpressionParser.parse(input) }.exceptionOrNull()
                assertTrue("$name: expected ${error["message"]}, got $thrown", thrown is A2uiParseError && thrown.message!!.contains(error["message"] as String, ignoreCase = true))
            } else {
                assertJson("$name", json(case["expect"]), JsonArray(ExpressionParser.parse(input)))
            }
            ran++
        }
        assertEquals(39, ran)
    }

    @Test
    fun dataModel() {
        var ran = 0
        cases("data_model.yaml").forEach { case ->
            val name = case["name"]
            val model = testDataModel(json(case["initial"]) as? JsonObject ?: JsonObject(emptyMap()))
            @Suppress("UNCHECKED_CAST")
            val watch = (case["watch"] as? List<String>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            (case["steps"] as List<Map<String, Any?>>).forEach { step ->
                val path = step["path"] as? String ?: "/"
                val outcome = runCatching {
                    when (step["op"]) {
                        "get" -> {
                            val value = model.get(path)
                            if (step["expect_absent"] == true) assertTrue("$name: $path should be absent, was $value", value == null || value == JsonNull)
                            if (step.containsKey("expect")) assertJson("$name get $path", json(step["expect"]), value ?: JsonNull)
                            when (step["expect_type"]) {
                                "list" -> assertTrue("$name: $path is not a list", value is JsonArray)
                                "object" -> assertTrue("$name: $path is not an object", value is JsonObject)
                            }
                        }
                        "set" -> model.set(path, json(step["value"]))
                        "delete" -> model.delete(path)
                        "dispose" -> Unit
                    }
                }
                if (step.containsKey("expect_error")) assertTrue("$name: ${step["op"]} $path should fail", outcome.isFailure)
                else outcome.getOrThrow()
                @Suppress("UNCHECKED_CAST")
                (step["expect_values"] as? Map<String, Any?>)?.forEach { (p, v) -> assertJson("$name value $p", json(v), model.get(p) ?: JsonNull) }
            }
            if (watch.isNotEmpty()) Unit // notifications: see [skipped]
            ran++
        }
        assertEquals(41, ran)
    }

    @Test
    fun messageProcessor() {
        var ran = 0
        var skippedStrict = 0
        (cases("message_processor_v1_0.yaml") + cases("message_processor.yaml")).filter { it["action"] == "process_messages" }.forEach { case ->
            val name = case["name"]
            if (case["strictMode"] == true) { skippedStrict++; return@forEach }
            // The case's catalogs: ids and protocol versions (their components do not matter here).
            @Suppress("UNCHECKED_CAST")
            val catalogs = (case["catalogs"] as? List<Map<String, Any?>>).orEmpty().map {
                A2uiCatalog(it["catalogId"] as String, emptyMap(), emptyMap(), "v" + (it["protocolVersion"] as String).removePrefix("v"))
            }
            val state = A2uiState(catalogs = catalogs)
            // A case is one batch with its expectations, or `steps` of them.
            @Suppress("UNCHECKED_CAST")
            val steps = (case["steps"] as? List<Map<String, Any?>>) ?: listOf(case)
            steps.forEachIndexed { n, step ->
                @Suppress("UNCHECKED_CAST")
                val replies = when (val messages = step["messages"]) {
                    is List<*> -> messages.flatMap { state.process(json(it)) }
                    else -> state.process(json(messages)) // a list wrapper object
                }
                val where = "$name step $n"
                if (step.containsKey("expectError")) {
                    assertTrue("$where: expected an error, got none", replies.any { "error" in it })
                    return@forEachIndexed
                }
                assertTrue("$where: unexpected errors $replies", replies.none { "error" in it })
                @Suppress("UNCHECKED_CAST")
                ((step["expect"] as? Map<String, Any?>)?.get("surfaces") as? Map<String, Map<String, Any?>>)?.forEach { (id, expect) ->
                    val surface = state.surface(id)
                    assertEquals("$where: surface $id exists", expect["exists"] ?: true, surface != null)
                    if (surface == null) return@forEach
                    expect["sendDataModel"]?.let { assertEquals("$where sendDataModel", it, surface.sendDataModel) }
                    expect["dataModel"]?.let { assertJson("$where dataModel", json(it), surface.dataModel) }
                    @Suppress("UNCHECKED_CAST")
                    (expect["components"] as? List<Map<String, Any?>>)?.forEach { c ->
                        val actual = surface.component(c["id"] as String) ?: fail("$where: missing component ${c["id"]}") as Nothing
                        c.forEach { (k, v) -> assertJson("$where component ${c["id"]}.$k", json(v), actual[k] ?: JsonNull) }
                    }
                }
            }
            ran++
        }
        assertTrue("ran $ran", ran >= 15)
        println("message processor: $ran cases run, $skippedStrict strict-mode cases skipped (${skipped["strictMode"]})")
    }

    // --- YAML → JSON, and number-tolerant comparison ------------------------------------------------

    private fun json(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to json(v) })
        is List<*> -> JsonArray(value.map(::json))
        else -> JsonPrimitive(value.toString())
    }

    private fun assertJson(what: String, expected: JsonElement, actual: JsonElement) {
        assertTrue("$what: expected $expected but was $actual", same(expected, actual))
    }

    private fun same(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString && a != JsonNull && b != JsonNull ->
            runCatching { BigDecimal(a.content).compareTo(BigDecimal(b.content)) == 0 }.getOrDefault(a.content == b.content)
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.all { (k, v) -> same(v, b.getValue(k)) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        else -> a == b
    }
}
