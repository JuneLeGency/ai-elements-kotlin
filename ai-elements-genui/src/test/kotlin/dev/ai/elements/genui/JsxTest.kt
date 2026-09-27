package dev.ai.elements.genui

import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.ChildRef
import dev.ai.elements.genui.a2ui.ComponentScope
import dev.ai.elements.genui.a2ui.testScope
import dev.ai.elements.genui.jsx.JsxCompiler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsxTest {
    private val compiler = JsxCompiler()

    private val weather = """
        <Card>
          <h2>Weather in {city}</h2>
          <p>It is <b>{temp}°C</b> and sunny.</p>
          <div className="flex gap-2">
            <Button variant="primary" onClick={refresh}>Refresh</Button>
            <a href="https://example.com/forecast">Forecast</a>
          </div>
          <input name="note" placeholder="Add a note" />
          <ul><li>Wind 5 km/h</li><li>Humidity {humidity}%</li></ul>
          {alert("pwned")}
        </Card>
    """.trimIndent()

    private val bindings = Json.parseToJsonElement("""{"city":"Paris","temp":21,"humidity":40,"note":""}""").jsonObject

    private fun render(jsx: String, b: JsonObject = bindings): Pair<A2uiState, ComponentScope> {
        val state = A2uiState()
        compiler.messages(jsx, "s", b).forEach { assertEquals(emptyList<JsonObject>(), state.process(it)) }
        val surface = state.surface("s")!!
        return state to testScope(surface)
    }

    /** Every Text shown in the tree, bindings resolved, in order. */
    private fun texts(scope: ComponentScope): List<String> {
        val own = if (scope.type == "Text") listOfNotNull(scope.string("text")) else emptyList()
        val kids: List<ChildRef> = scope.children() + listOfNotNull(scope.child())
        return own + kids.flatMap { texts(testScope(it)) }
    }

    @Test
    fun modelJsx_compilesOntoTheCatalog() {
        val (_, root) = render(weather)
        assertEquals("Card", root.type)
        assertEquals(
            listOf("## Weather in Paris", "It is **21°C** and sunny.", "Refresh", "[Forecast](https://example.com/forecast)", "•", "Wind 5 km/h", "•", "Humidity 40%"),
            texts(root),
        )
        val column = testScope(root.child()!!)
        val row = column.children().map(::testScope).first { it.type == "Row" } // className="flex" → Row
        val (refresh, link) = row.children().map(::testScope)
        assertEquals("primary", refresh.string("variant"))
        assertEquals("Text", link.type) // inline link: Markdown, opens through LocalUriHandler
        assertTrue(column.children().map(::testScope).any { it.type == "TextField" && it.string("placeholder") == "Add a note" })
    }

    @Test
    fun handlersBecomeActions_inputsBindToTheData() {
        val (state, root) = render(weather)
        val column = testScope(root.child()!!)
        val input = column.children().map(::testScope).single { it.type == "TextField" }
        input.write("value", JsonPrimitive("bring an umbrella"))
        assertEquals("bring an umbrella", state.surface("s")!!.read("/note")!!.jsonPrimitive.content)
        val actions = mutableListOf<A2uiAction>()
        val row = column.children().map(::testScope).first { it.type == "Row" }
        testScope(state.surface("s")!!, row.children().first().component.toString(), onAction = { actions += it }).dispatch()
        assertEquals("refresh", actions.single().name)
    }

    @Test
    fun noCodeRuns_unsupportedExpressionsAreDropped() {
        val components = compiler.components(weather)
        assertTrue(components.none { it.toString().contains("pwned") || it.toString().contains("alert") })
        val weird = compiler.components("<div>{items.map(i => <p>{i}</p>)}{ 1 + 2 }{`t`}{fn()}</div>")
        assertTrue(weird.any { it["id"]!!.jsonPrimitive.content == "root" })
        assertTrue(weird.none { it.toString().contains("map(") || it.toString().contains("fn(") })
    }

    @Test
    fun streaming_everyPrefixCompiles() {
        for (end in 0..weather.length) {
            val prefix = weather.substring(0, end)
            val components = compiler.components(prefix) // never throws
            if (components.isNotEmpty()) assertTrue("prefix $end has no root", components.any { it["id"]!!.jsonPrimitive.content == "root" })
        }
        // A half-written tag is left out; completed siblings render.
        val partial = compiler.components("<Column><p>Done</p><Button variant=\"prim")
        assertTrue(partial.any { (it["text"] as? JsonPrimitive)?.content == "Done" })
        assertTrue(partial.none { it["component"]!!.jsonPrimitive.content == "Button" })
    }

    @Test
    fun fragmentsCatalogTagsAndUnknownTags() {
        val c = compiler.components("<><Text variant=\"caption\">{label}</Text><Slider label=\"Volume\" value={volume} min={0} max={10} /><Custom><p>kept</p></Custom></>")
        val byType = c.groupBy { it["component"]!!.jsonPrimitive.content }
        assertEquals("caption", byType["Text"]!!.first()["variant"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"path":"/label"}"""), byType["Text"]!!.first()["text"])
        assertEquals(Json.parseToJsonElement("""{"path":"/volume"}"""), byType["Slider"]!!.single()["value"])
        assertEquals(10L, byType["Slider"]!!.single()["max"]!!.jsonPrimitive.content.toLong())
        assertTrue(c.any { (it["text"] as? JsonPrimitive)?.content == "kept" })
        assertEquals("Column", c.single { it["id"]!!.jsonPrimitive.content == "root" }["component"]!!.jsonPrimitive.content)
    }
}
