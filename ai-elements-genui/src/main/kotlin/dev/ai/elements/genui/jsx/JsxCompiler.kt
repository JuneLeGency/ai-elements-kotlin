package dev.ai.elements.genui.jsx

import dev.ai.elements.genui.a2ui.A2ui
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Compiles model-written JSX (the AI Elements `jsx-preview` input) onto A2UI components, so it
 * renders natively through the same catalog as A2UI surfaces.
 *
 * - Catalog tags (`<Card>`, `<Row>`, `<Button>`, `<TextField>`…) keep their name and properties;
 *   common HTML tags map onto them (`div` → Column, `p`/`span`/`h1…h6` → Text, `button` → Button,
 *   `img` → Image, `a` → a link button, `ul`/`li` → a bulleted Column, `hr` → Divider, `input` →
 *   TextField / CheckBox). Tags neither known nor in [knownComponents] render their children.
 * - `{name}` / `{a.b}` read from the bindings (the surface's data model); `{[…]}` / `{{"k": …}}` are
 *   JSON data; nothing is executed.
 * - `<select>` with `<option>`s is a ChoicePicker; `<Tabs>` with `<Tab title="…">`s is Tabs.
 * - `onClick={save}` / `onClick="save"` dispatch the action `save` to the host.
 * - Streaming-tolerant: unclosed tags close at the end, a half-written tag is left out.
 */
internal class JsxCompiler(private val knownComponents: Set<String> = BasicTags) {

    /** The component list for [jsx]: `root` plus its descendants, as A2UI component objects. */
    fun components(jsx: String): List<JsonObject> {
        val roots = JsxParser(jsx).parse()
        val out = mutableListOf<JsonObject>()
        var next = 0
        fun id() = "j${next++}"

        fun text(value: JsonElement, id: String, variant: String? = null) = buildJsonObject {
            put("id", id); put("component", "Text"); put("text", value)
            variant?.let { put("variant", it) }
        }.also(out::add)

        lateinit var emitRef: (JsxNode, String) -> String?

        /** Children: runs of inline content become one Markdown Text; blocks render one by one. */
        fun children(nodes: List<JsxNode>): List<String> {
            val ids = mutableListOf<String>()
            val run = mutableListOf<JsxNode>()
            fun flushRun() {
                if (run.isEmpty()) return
                val value = inline(run.toList())
                run.clear()
                if (value is JsonPrimitive && value.content.isBlank()) return
                val tid = id()
                text(value, tid)
                ids += tid
            }
            nodes.forEach { n ->
                if (n.isInline()) run += n else { flushRun(); emitRef(n, id())?.let(ids::add) }
            }
            flushRun()
            return ids
        }

        fun emit(node: JsxNode, id: String): String? {
            when (node) {
                is JsxNode.Text -> {
                    val value = node.text.trim().replace(Regex("\\s+"), " ")
                    if (value.isEmpty()) return null
                    text(JsonPrimitive(value), id)
                    return id
                }
                is JsxNode.Expression -> {
                    text(node.value, id)
                    return id
                }
                is JsxNode.Element -> {}
            }
            node as JsxNode.Element
            val tag = node.tag
            val props = node.props
            // Inline content of text-like tags becomes one Markdown string.
            if (tag in TextTags) {
                val prefix = when (tag) {
                    "h1" -> "# "; "h2" -> "## "; "h3" -> "### "; "h4" -> "#### "; "h5", "h6" -> "##### "
                    else -> ""
                }
                val value = inline(node.children, prefix)
                text(value, id, if (tag == "small" || props.string("variant") == "caption") "caption" else null)
                return id
            }
            if (tag == "Text") {
                val textProp = props["text"] ?: inline(node.children)
                out += buildJsonObject {
                    put("id", id); put("component", "Text"); put("text", textProp)
                    props.forEach { (k, v) -> if (k != "text") put(k, v) }
                }
                return id
            }
            val childIds = { children(node.children) }
            when {
                tag == "hr" || tag == "Divider" -> out += component(id, "Divider", props.without("className", "style"))
                tag == "br" -> return null
                tag == "img" || tag == "Image" -> out += component(id, "Image", props.renamed("src", "url").renamed("alt", "description").without("className", "style"))
                tag == "button" || tag == "Button" -> {
                    val label = node.children.let { kids ->
                        if (kids.size == 1) emit(kids.single(), id())
                        else if (kids.isEmpty()) props.string("label")?.let { val t = id(); text(JsonPrimitive(it), t); t }
                        else wrap("Row", kids.mapNotNull { emit(it, id()) }, id(), out)
                    }
                    out += buildJsonObject {
                        put("id", id); put("component", "Button")
                        label?.let { put("child", it) }
                        put("variant", props.string("variant") ?: if (props.string("type") == "submit" || tag == "button" && props.string("className")?.contains("primary") == true) "primary" else "default")
                        action(props)?.let { put("action", it) }
                    }
                }
                tag == "a" -> {
                    val label = wrap("Row", childIds(), id(), out)
                    out += buildJsonObject {
                        put("id", id); put("component", "Button"); put("variant", "borderless"); put("child", label)
                        props["href"]?.let { href -> put("action", buildJsonObject { put("functionCall", buildJsonObject { put("call", "openUrl"); put("args", buildJsonObject { put("url", href) }) }) }) }
                    }
                }
                tag == "ul" || tag == "ol" -> {
                    var n = 0
                    val items = node.children.filterIsInstance<JsxNode.Element>().map { li ->
                        n++
                        val bullet = text(JsonPrimitive(if (tag == "ol") "$n." else "•"), id())["id"]!!.let { (it as JsonPrimitive).content }
                        val body = wrap("Column", children(li.children), id(), out)
                        wrap("Row", listOf(bullet, body), id(), out, align = "start")
                    }
                    out += buildJsonObject { put("id", id); put("component", "Column"); put("children", JsonArray(items.map(::JsonPrimitive))) }
                }
                tag == "input" || tag == "textarea" -> {
                    val type = props.string("type")
                    if (type == "checkbox") out += buildJsonObject {
                        put("id", id); put("component", "CheckBox")
                        put("label", props["label"] ?: JsonPrimitive(""))
                        put("value", props["checked"] ?: props["value"] ?: JsonPrimitive(false))
                    } else out += buildJsonObject {
                        put("id", id); put("component", "TextField")
                        (props["label"] ?: props["placeholder"])?.let { put("label", it) }
                        props["placeholder"]?.let { put("placeholder", it) }
                        put("value", props["value"] ?: props["name"]?.let { n -> JsonObject(mapOf("path" to JsonPrimitive("/" + (n as JsonPrimitive).content))) } ?: JsonPrimitive(""))
                        put("variant", when { tag == "textarea" -> "longText"; type == "password" -> "obscured"; type == "number" -> "number"; else -> "shortText" })
                    }
                }
                // `<select>` / `<ChoicePicker>` with `<option>`s, as in HTML: a ChoicePicker.
                tag == "select" || (tag == "ChoicePicker" && node.children.any { it is JsxNode.Element && it.tag == "option" }) -> {
                    val options = node.children.filterIsInstance<JsxNode.Element>().filter { it.tag == "option" }.map { o ->
                        val label = inline(o.children)
                        buildJsonObject { put("label", label); put("value", o.props["value"] ?: label) }
                    }
                    val multiple = (props["multiple"] as? JsonPrimitive)?.let { it.content != "false" } ?: false
                    out += buildJsonObject {
                        put("id", id); put("component", "ChoicePicker")
                        put("options", JsonArray(options))
                        put("variant", props.string("variant") ?: if (multiple) "multipleSelection" else "mutuallyExclusive")
                        (props["value"] ?: props["name"]?.let { n -> JsonObject(mapOf("path" to JsonPrimitive("/" + (n as JsonPrimitive).content))) })?.let { put("value", it) }
                        props.without("className", "style", "value", "name", "multiple", "variant", "options").forEach { (k, v) -> put(k, v) }
                    }
                }
                // `<Tabs>` with `<Tab title="…">` children: A2UI Tabs, one tab per child.
                tag == "Tabs" && node.children.any { it is JsxNode.Element && it.tag == "Tab" } -> {
                    val tabs = node.children.filterIsInstance<JsxNode.Element>().filter { it.tag == "Tab" }.map { t ->
                        buildJsonObject {
                            put("title", t.props["title"] ?: t.props["label"] ?: JsonPrimitive(""))
                            put("child", wrap("Column", children(t.children), id(), out))
                        }
                    }
                    out += buildJsonObject {
                        put("id", id); put("component", "Tabs"); put("tabs", JsonArray(tabs))
                        props.without("className", "style", "tabs").forEach { (k, v) -> put(k, v) }
                    }
                }
                tag == "Card" -> {
                    val kids = childIds()
                    out += buildJsonObject {
                        put("id", id); put("component", "Card")
                        put("child", if (kids.size == 1) kids.single() else wrap("Column", kids, id(), out))
                        props.without("className", "style", "child").forEach { (k, v) -> put(k, v) }
                    }
                }
                tag == "Row" || tag == "Column" || tag == "List" || tag in FlexTags -> {
                    val type = when {
                        tag in FlexTags -> if (props.isRow()) "Row" else "Column"
                        else -> tag
                    }
                    out += buildJsonObject {
                        put("id", id); put("component", type)
                        put("children", JsonArray(childIds().map(::JsonPrimitive)))
                        props.without("className", "style", "children").forEach { (k, v) -> put(k, v) }
                    }
                }
                tag in knownComponents -> out += buildJsonObject {
                    put("id", id); put("component", tag)
                    val kids = childIds()
                    if (kids.isNotEmpty() && "child" !in props && "children" !in props) {
                        if (kids.size == 1) put("child", kids.single()) else put("children", JsonArray(kids.map(::JsonPrimitive)))
                    }
                    props.without("className", "style").forEach { (k, v) -> if (k != "onClick") put(k, v) }
                    action(props)?.let { put("action", it) }
                }
                else -> { // Unknown tags (e.g. <Fragment>, <section>): keep their children.
                    val kids = childIds()
                    if (kids.isEmpty()) return null
                    out += buildJsonObject { put("id", id); put("component", "Column"); put("children", JsonArray(kids.map(::JsonPrimitive))) }
                }
            }
            return id
        }

        emitRef = ::emit
        val top = children(roots)
        when {
            top.isEmpty() -> {}
            top.size == 1 -> {
                // Rename the single top component to `root`.
                val index = out.indexOfFirst { (it["id"] as JsonPrimitive).content == top.single() }
                out[index] = JsonObject(out[index] + ("id" to JsonPrimitive("root")))
            }
            else -> out += buildJsonObject { put("id", "root"); put("component", "Column"); put("children", JsonArray(top.map(::JsonPrimitive))) }
        }
        return out
    }

    /** A2UI messages for a surface showing [jsx] with [bindings] as its data model. */
    fun messages(jsx: String, surfaceId: String, bindings: JsonObject = JsonObject(emptyMap())): List<JsonObject> = listOf(
        buildJsonObject {
            put("version", A2ui.VERSION)
            put("createSurface", buildJsonObject {
                put("surfaceId", surfaceId); put("catalogId", A2ui.BASIC_CATALOG_ID)
                put("dataModel", bindings); put("components", JsonArray(components(jsx)))
            })
        },
    )

    /**
     * Inline nodes as one Text value: Markdown (`<b>` → `**`, `<i>` → `_`, `<code>` → backticks,
     * `<a>` → a link); a lone `{x}` stays a binding, bindings inside text interpolate (`formatString`).
     */
    private fun inline(nodes: List<JsxNode>, prefix: String = ""): JsonElement {
        val only = nodes.singleOrNull()
        if (prefix.isEmpty() && only is JsxNode.Expression && only.value is JsonObject) return only.value
        fun md(n: JsxNode): String = when (n) {
            is JsxNode.Text -> n.text.replace(Regex("\\s+"), " ")
            is JsxNode.Expression -> when (val v = n.value) {
                is JsonObject -> "\${" + ((v["path"] as? JsonPrimitive)?.content ?: "") + "}"
                is JsonPrimitive -> v.content
                else -> v.toString()
            }
            is JsxNode.Element -> {
                val inner = n.children.joinToString("") { md(it) }
                when (n.tag) {
                    "b", "strong" -> "**$inner**"
                    "i", "em" -> "_${inner}_"
                    "code" -> "`$inner`"
                    "br" -> "\n"
                    "a" -> "[$inner](${(n.props["href"] as? JsonPrimitive)?.content.orEmpty()})"
                    else -> inner
                }
            }
        }
        val text = prefix + nodes.joinToString("") { md(it) }.replace(Regex("[ \t]*\n[ \t]*"), " ").trim()
        // Bindings inside mixed text interpolate like A2UI formatString.
        return if ("\${" in text) JsonObject(mapOf("call" to JsonPrimitive("formatString"), "args" to JsonObject(mapOf("value" to JsonPrimitive(text)))))
        else JsonPrimitive(text)
    }

    private fun JsxNode.isInline(): Boolean = when (this) {
        is JsxNode.Text, is JsxNode.Expression -> true
        is JsxNode.Element -> tag in InlineTags
    }

    private fun action(props: Map<String, JsonElement>): JsonObject? {
        val handler = props["onClick"] ?: props["onPress"] ?: props["onSubmit"] ?: return null
        val name = when (handler) {
            is JsonPrimitive -> handler.content
            is JsonObject -> (handler["path"] as? JsonPrimitive)?.content?.trimStart('/') ?: return null
            else -> return null
        }
        return buildJsonObject { put("event", buildJsonObject { put("name", name); put("context", JsonObject(emptyMap())) }) }
    }

    private fun component(id: String, type: String, props: Map<String, JsonElement>) =
        JsonObject(props + mapOf("id" to JsonPrimitive(id), "component" to JsonPrimitive(type)))

    private fun wrap(type: String, children: List<String>, id: String, out: MutableList<JsonObject>, align: String? = null): String {
        if (children.size == 1 && align == null) return children.single()
        out += buildJsonObject {
            put("id", id); put("component", type); put("children", JsonArray(children.map(::JsonPrimitive)))
            align?.let { put("align", it) }
        }
        return id
    }

    private fun Map<String, JsonElement>.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun Map<String, JsonElement>.without(vararg keys: String) = filterKeys { it !in keys }
    private fun Map<String, JsonElement>.renamed(from: String, to: String) = if (from in this) (this - from) + (to to this.getValue(from)) else this

    /** `display: flex` / `flex-row` / `flexDirection: row` (not column) → a Row. */
    private fun Map<String, JsonElement>.isRow(): Boolean {
        val cls = string("className").orEmpty()
        val style = this["style"]?.toString().orEmpty()
        return (Regex("\\bflex\\b").containsMatchIn(cls) && "flex-col" !in cls) || "flex-row" in cls ||
            Regex("flexDirection\\W+row").containsMatchIn(style) || (Regex("display\\W+flex").containsMatchIn(style) && !Regex("flexDirection\\W+column").containsMatchIn(style))
    }

    companion object {
        val BasicTags = setOf(
            "Text", "Image", "Icon", "Video", "AudioPlayer", "Row", "Column", "List", "Card", "Tabs", "Modal",
            "Divider", "Button", "TextField", "CheckBox", "ChoicePicker", "Slider", "DateTimeInput",
        )
        private val InlineTags = setOf("b", "strong", "i", "em", "code", "br", "a")
        private val TextTags = setOf("p", "span", "label", "h1", "h2", "h3", "h4", "h5", "h6", "strong", "b", "em", "i", "small", "code", "blockquote")
        private val FlexTags = setOf("div", "section", "article", "main", "header", "footer", "nav", "form", "aside")
    }
}

/** A parsed JSX node. */
internal sealed interface JsxNode {
    data class Element(val tag: String, val props: Map<String, JsonElement>, val children: List<JsxNode>) : JsxNode
    data class Text(val text: String) : JsxNode

    /** `{…}`: a binding (`{"path": …}`) or a literal. */
    data class Expression(val value: JsonElement) : JsxNode
}

/** A tolerant parser for the JSX subset: elements, attributes, text, `{literal}` and `{binding}`. */
internal class JsxParser(private val src: String) {
    private var i = 0

    fun parse(): List<JsxNode> = children(null)

    private fun children(closing: String?): List<JsxNode> {
        val out = mutableListOf<JsxNode>()
        val text = StringBuilder()
        fun flush() { if (text.isNotEmpty()) { out += JsxNode.Text(text.toString()); text.clear() } }
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("</", i) -> {
                    val end = src.indexOf('>', i)
                    if (end < 0) { i = src.length; break } // half-written closing tag
                    val name = src.substring(i + 2, end).trim()
                    if (closing == null || name == closing || name.isEmpty()) { i = end + 1; flush(); return out }
                    // A stray closing tag for an outer element: let the caller consume it.
                    flush(); return out
                }
                c == '<' && i + 1 < src.length && (src[i + 1].isLetter() || src[i + 1] == '>') -> {
                    flush()
                    val element = element() ?: break
                    out += element
                }
                c == '{' -> {
                    flush()
                    val body = braced() ?: break
                    val trimmed = body.trim()
                    if (trimmed.startsWith("/*")) continue // {/* comment */}
                    expression(trimmed)?.let { out += JsxNode.Expression(it) }
                }
                else -> { text.append(c); i++ }
            }
        }
        flush()
        return out
    }

    /** One element, or null when it is cut off before its tag ends. */
    private fun element(): JsxNode? {
        val start = i
        i++ // <
        if (i < src.length && src[i] == '>') { i++; return JsxNode.Element("Fragment", emptyMap(), children("")) }
        val name = name()
        val props = linkedMapOf<String, JsonElement>()
        while (true) {
            skipSpaces()
            if (i >= src.length) { i = start; i = src.length; return null } // tag still streaming
            when {
                src.startsWith("/>", i) -> { i += 2; return JsxNode.Element(name, props, emptyList()) }
                src[i] == '>' -> { i++; return JsxNode.Element(name, props, if (name in Void) emptyList() else children(name)) }
                src.startsWith("{...", i) -> { braced() ?: return null } // spread: ignored
                else -> {
                    val attr = name()
                    if (attr.isEmpty()) { i++; continue }
                    skipSpaces()
                    if (i < src.length && src[i] == '=') {
                        i++
                        skipSpaces()
                        if (i >= src.length) return null
                        val value = when (src[i]) {
                            '"', '\'' -> quoted()?.let(::JsonPrimitive) ?: return null
                            '{' -> braced()?.let { expression(it.trim()) } ?: return null
                            else -> JsonPrimitive(name())
                        }
                        if (value != null) props[attr] = value
                    } else props[attr] = JsonPrimitive(true)
                }
            }
        }
    }

    /** `{…}` contents (nested braces and strings respected), or null when unterminated. */
    private fun braced(): String? {
        val start = ++i
        var depth = 1
        var quote: Char? = null
        while (i < src.length) {
            val c = src[i]
            when {
                quote != null -> if (c == '\\') i++ else if (c == quote) quote = null
                c == '"' || c == '\'' || c == '`' -> quote = c
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) { i++; return src.substring(start, i - 1) }
            }
            i++
        }
        return null
    }

    private fun quoted(): String? {
        val q = src[i++]
        val sb = StringBuilder()
        while (i < src.length && src[i] != q) sb.append(src[i++])
        if (i >= src.length) return null
        i++
        return sb.toString()
    }

    private fun name(): String {
        val start = i
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] in "-_.:")) i++
        return src.substring(start, i)
    }

    private fun skipSpaces() { while (i < src.length && src[i].isWhitespace()) i++ }

    /** Literals and binding paths only: no code runs. Anything else is dropped. */
    private fun expression(e: String): JsonElement? = when {
        e.isEmpty() -> null
        e == "true" || e == "false" -> JsonPrimitive(e == "true")
        e == "null" || e == "undefined" -> JsonNull
        e.toDoubleOrNull() != null -> JsonPrimitive(e.toDouble().let { if (it % 1.0 == 0.0 && '.' !in e) it.toLong() else it })
        (e.startsWith("\"") && e.endsWith("\"")) || (e.startsWith("'") && e.endsWith("'")) || (e.startsWith("`") && e.endsWith("`") && "\${" !in e) ->
            JsonPrimitive(e.substring(1, e.length - 1))
        // JSON literals (arrays, objects with quoted keys) are data, as in A2UI: options, tabs…
        e.startsWith("[") || e.startsWith("{") -> runCatching { Json.parseToJsonElement(e) }.getOrNull()
        Regex("""^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*|\[\d+])*$""").matches(e) ->
            JsonObject(mapOf("path" to JsonPrimitive("/" + e.replace(Regex("""\[(\d+)]"""), ".$1").replace('.', '/'))))
        else -> null
    }

    private companion object {
        val Void = setOf("img", "br", "hr", "input", "meta", "link")
    }
}
