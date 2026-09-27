package dev.ai.elements.genui

import dev.ai.elements.genui.a2ui.A2ui
import dev.ai.elements.genui.a2ui.A2uiCatalog
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.ComponentScope
import dev.ai.elements.genui.a2ui.testScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A2UI v1.0 renderer logic, against the spec's semantics and the official Basic Catalog examples. */
class A2uiTest {
    private fun state(jsonl: String) = A2uiState().also { assertEquals(emptyList<JsonObject>(), it.processText(jsonl.trimIndent())) }

    @Test
    fun officialExamples_allProcessRenderableAndEvaluable() {
        val examples = File("src/test/resources/a2ui/examples").listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }
        assertTrue("expected the official examples, found ${examples.size}", examples.size >= 40)
        val catalog = A2uiCatalog.Basic
        examples.forEach { file ->
            val example = Json.parseToJsonElement(file.readText()).jsonObject
            val state = A2uiState()
            val replies = (example["messages"] as JsonArray).flatMap { state.process(it) }
            assertEquals("${file.name}: $replies", emptyList<JsonObject>(), replies)
            state.surfaces.forEach { surface ->
                assertTrue("${file.name}: no root", surface.hasRoot)
                // Walk the whole tree from root the way the renderer does, evaluating every property.
                fun walk(scope: ComponentScope, depth: Int) {
                    assertTrue("${file.name}: ${scope.type} is not in the Basic Catalog", scope.type in catalog.components)
                    scope.component.keys.filter { it !in setOf("id", "component", "children", "child", "action", "checks", "tabs", "options", "trigger", "content") }
                        .forEach { prop -> scope.resolve(prop) } // throws on unknown functions / bad expressions
                    scope.failedChecks()
                    val kids = scope.children() + listOfNotNull(scope.child(), scope.child("trigger"), scope.child("content")) +
                        (scope.raw("tabs") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("child")?.jsonPrimitive?.content?.let(scope::ref) }
                    if (depth < 40) kids.forEach { walk(testScope(it), depth + 1) }
                }
                walk(testScope(surface), 0)
            }
        }
    }

    @Test
    fun surfaceLifecycle_andProgressiveComponents() {
        val state = A2uiState()
        state.processText("""{"version":"v1.0","createSurface":{"surfaceId":"s","catalogId":"${A2ui.BASIC_CATALOG_ID}"}}""")
        val s = state.surface("s")!!
        assertFalse(s.hasRoot)
        state.processText("""{"version":"v1.0","updateComponents":{"surfaceId":"s","components":[{"id":"t","component":"Text","text":"hi"}]}}""")
        assertFalse(s.hasRoot) // buffered until root arrives
        state.processText("""{"version":"v1.0","updateComponents":{"surfaceId":"s","components":[{"id":"root","component":"Column","children":["t","missing"]}]}}""")
        assertTrue(s.hasRoot)
        assertEquals(listOf("t"), testScope(s).children().map { it.id }) // unknown references are skipped
        // Errors: duplicate surface, unknown surface.
        assertEquals("VALIDATION_FAILED", state.processText("""{"version":"v1.0","createSurface":{"surfaceId":"s"}}""").single()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(1, state.processText("""{"version":"v1.0","updateDataModel":{"surfaceId":"nope","value":1}}""").size)
        state.processText("""{"version":"v1.0","deleteSurface":{"surfaceId":"s"}}""")
        assertNull(state.surface("s"))
    }

    @Test
    fun dataModel_upsertReplaceAndDelete() {
        val state = state("""{"version":"v1.0","createSurface":{"surfaceId":"s","dataModel":{"user":{"first":"A","tmp":1}}}}""")
        val s = state.surface("s")!!
        state.processText("""{"version":"v1.0","updateDataModel":{"surfaceId":"s","path":"/user/last","value":"B"}}""")
        state.processText("""{"version":"v1.0","updateDataModel":{"surfaceId":"s","path":"/user/tmp","value":null}}""")
        state.processText("""{"version":"v1.0","updateDataModel":{"surfaceId":"s","path":"/list/0","value":"x"}}""")
        assertEquals(Json.parseToJsonElement("""{"user":{"first":"A","last":"B"},"list":["x"]}"""), s.dataModel) // numeric token → list
        state.processText("""{"version":"v1.0","updateDataModel":{"surfaceId":"s","value":{"only":true}}}""")
        assertEquals(Json.parseToJsonElement("""{"only":true}"""), s.dataModel)
    }

    @Test
    fun templates_relativeAbsolutePathsAndIndex() {
        val state = state(
            """
            {"version":"v1.0","createSurface":{"surfaceId":"s","dataModel":{"company":"Acme","employees":[{"name":"Alice"},{"name":"Bob"}]},"components":[
              {"id":"root","component":"List","children":{"path":"/employees","componentId":"row"}},
              {"id":"row","component":"Text","text":{"call":"formatString","args":{"value":"${'$'}{@index(offset: 1)}. ${'$'}{name} @ ${'$'}{/company}"}}}]}}
            """,
        )
        val items = testScope(state.surface("s")!!).children()
        assertEquals(listOf("1. Alice @ Acme", "2. Bob @ Acme"), items.map { testScope(it).string("text") })
    }

    @Test
    fun formatString_literalsCallsAndEscapes() {
        val state = state("""{"version":"v1.0","createSurface":{"surfaceId":"s","dataModel":{"n":3,"price":4.5,"obj":{"a":1},"none":null}}}""")
        val scope = testScope(state.surface("s")!!, """{"id":"x","component":"Text"}""")
        fun fmt(t: String) = scope.context.interpolate(t)
        assertEquals("3 items", fmt("\${/n} \${pluralize(value: /n, one: 'item', other: 'items')}"))
        assertEquals("literal \${x}", fmt("literal \\\${x}"))
        assertEquals("{\"a\":1}|", fmt("\${/obj}|\${/none}"))
        assertEquals("true", fmt("\${not(value: false)}"))
        assertEquals("4.50", fmt("\${formatNumber(value: /price, decimals: 2, grouping: false)}"))
        // Nested blocks as argument values (official example 32).
        state.surface("s")!!.write("/now", JsonPrimitive("2025-12-15T12:00:00Z"))
        assertEquals("Hello! 2025", fmt("Hello! \${formatDate(value: \${/now}, format: 'yyyy')}"))
    }

    @Test(timeout = 5_000)
    fun formatString_malformedInputTerminates() {
        val state = state("""{"version":"v1.0","createSurface":{"surfaceId":"s","dataModel":{"a":1}}}""")
        val scope = testScope(state.surface("s")!!, """{"id":"x","component":"Text"}""")
        listOf("\${", "\${(", "\${f(x: {)}", "\${f(:,:)}", "\${ !! }", "\${f(a: 'x'", "\${/a").forEach { scope.context.interpolate(it) }
    }

    @Test
    fun checks_validationFunctionsAndLogic() {
        val state = state(
            """
            {"version":"v1.0","createSurface":{"surfaceId":"s","dataModel":{"email":"bad","code":"12"},"components":[
              {"id":"root","component":"TextField","label":"Email","value":{"path":"/email"},"checks":[
                {"condition":{"call":"required","args":{"value":{"path":"/email"}}},"message":"Required"},
                {"condition":{"call":"email","args":{"value":{"path":"/email"}}},"message":"Not an email"}]},
              {"id":"code","component":"TextField","value":{"path":"/code"},"checks":[
                {"condition":{"call":"and","args":{"values":[{"call":"regex","args":{"value":{"path":"/code"},"pattern":"^[0-9]+$"}},{"call":"length","args":{"value":{"path":"/code"},"min":4}}]}},"message":"4+ digits"}]}]}}
            """,
        )
        val s = state.surface("s")!!
        val email = testScope(s)
        assertEquals(listOf("Not an email"), email.failedChecks())
        email.write("value", JsonPrimitive("a@b.co")) // two-way binding writes through to the model
        assertEquals("a@b.co", s.read("/email")!!.jsonPrimitive.content)
        assertEquals(emptyList<String>(), email.failedChecks())
        val code = testScope(s, s.component("code").toString())
        assertEquals(listOf("4+ digits"), code.failedChecks())
        s.write("/code", JsonPrimitive("1234"))
        assertEquals(emptyList<String>(), code.failedChecks())
    }

    @Test
    fun action_resolvesContextAndCarriesTheDataModel() {
        val state = state(
            """
            {"version":"v1.0","createSurface":{"surfaceId":"s","sendDataModel":true,"dataModel":{"form":{"email":"j@x.io"}},"components":[
              {"id":"root","component":"Button","child":"l","action":{"event":{"name":"submit","context":{"email":{"path":"/form/email"},"id":"123"}}}},
              {"id":"l","component":"Text","text":"Send"}]}}
            """,
        )
        val actions = mutableListOf<dev.ai.elements.genui.a2ui.A2uiAction>()
        testScope(state.surface("s")!!, onAction = { actions += it }).dispatch()
        val action = actions.single()
        assertEquals("submit", action.name)
        assertEquals("root", action.sourceComponentId)
        assertEquals(Json.parseToJsonElement("""{"email":"j@x.io","id":"123"}"""), action.context)
        assertEquals(Json.parseToJsonElement("""{"form":{"email":"j@x.io"}}"""), action.dataModel)
        val message = action.toMessage()
        assertEquals("v1.0", message["version"]!!.jsonPrimitive.content)
        assertEquals(setOf("name", "surfaceId", "sourceComponentId", "timestamp", "context"), message["action"]!!.jsonObject.keys)
    }

    @Test
    fun openUrl_needsUserActivationAndHttp() {
        val opened = mutableListOf<String>()
        val state = state(
            """
            {"version":"v1.0","createSurface":{"surfaceId":"s","components":[
              {"id":"root","component":"Button","child":"l","action":{"functionCall":{"call":"openUrl","args":{"url":"https://a2ui.org"}}}},
              {"id":"bad","component":"Button","child":"l","action":{"functionCall":{"call":"openUrl","args":{"url":"javascript:alert(1)"}}}},
              {"id":"l","component":"Text","text":"Go"}]}}
            """,
        )
        val s = state.surface("s")!!
        testScope(s, openUrl = { opened += it }).dispatch()
        testScope(s, s.component("bad").toString(), openUrl = { opened += it }).dispatch()
        assertEquals(listOf("https://a2ui.org"), opened)
        // Not from an action (e.g. a binding): rejected.
        val passive = runCatching { testScope(s, openUrl = { opened += it }).context.call(Json.parseToJsonElement("""{"call":"openUrl","args":{"url":"https://x.org"}}""").jsonObject) }
        assertTrue(passive.isFailure)
    }

    @Test
    fun callRendererFunction_onlyAgentCallableFunctions() {
        val state = A2uiState()
        val reply = state.processText("""{"version":"v1.0","callRendererFunction":{"functionCallId":"f1","callFunction":{"call":"email","catalogId":"${A2ui.BASIC_CATALOG_ID}","args":{"value":"a@b.co"}}}}""").single()
        val error = reply["error"]!!.jsonObject
        assertEquals("INVALID_FUNCTION_CALL", error["code"]!!.jsonPrimitive.content)
        assertEquals("f1", error["functionCallId"]!!.jsonPrimitive.content)
    }

    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}
