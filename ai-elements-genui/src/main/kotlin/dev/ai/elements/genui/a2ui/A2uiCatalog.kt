package dev.ai.elements.genui.a2ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Renders one component type of a catalog. */
fun interface A2uiComponent {
    @Composable
    fun Render(scope: ComponentScope, modifier: Modifier)
}

/**
 * A component catalog (A2UI §"The component catalog"): the component types and functions a surface
 * may use, rendered natively. [Basic] is the A2UI Basic Catalog; [extend] adds or replaces
 * components and functions — your design system's components, under your own catalog id.
 */
@Immutable
class A2uiCatalog(
    val id: String,
    val components: Map<String, A2uiComponent>,
    val functions: Map<String, A2uiFunction>,
    /** The A2UI version its definitions follow; catalogs mixed on one surface must share it. */
    val protocolVersion: String = A2ui.VERSION,
) {
    fun extend(
        id: String = this.id,
        components: Map<String, A2uiComponent> = emptyMap(),
        functions: Map<String, A2uiFunction> = emptyMap(),
    ) = A2uiCatalog(id, this.components + components, this.functions + functions, protocolVersion)

    companion object {
        val Basic: A2uiCatalog by lazy { A2uiCatalog(A2ui.BASIC_CATALOG_ID, BasicComponents.all, BasicFunctions.all) }
    }
}

/** A child to place: its component and the data scope it renders in. */
@Immutable
class ChildRef internal constructor(val id: String, val component: JsonObject, internal val context: DataContext) {
    /** This child's `weight` (flex-grow in a Row / Column), if any. */
    val weight: Float? get() = (component["weight"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
}

/**
 * What a component renderer sees: its properties resolved in its data scope, its children, two-way
 * binding writes and action dispatch.
 */
@Stable
class ComponentScope internal constructor(
    val component: JsonObject,
    val context: DataContext,
    private val host: SurfaceHost,
) {
    val id: String get() = component.str("id").orEmpty()
    val type: String get() = component.str("component").orEmpty()

    fun raw(prop: String): JsonElement? = component[prop]
    fun resolve(prop: String): JsonElement? = context.resolve(component[prop])
    fun string(prop: String): String? = safely { context.string(component[prop]) }
    fun boolean(prop: String): Boolean? = safely { context.boolean(component[prop]) }
    fun number(prop: String): Double? = safely { context.number(component[prop]) }
    fun stringList(prop: String): List<String> = safely { context.stringList(component[prop]) }.orEmpty()

    /** Two-way binding: store [value] where [prop] is bound (literal properties are read-only). */
    fun write(prop: String, value: JsonElement?) {
        val bound = component[prop] as? JsonObject ?: return
        if (with(DataContext) { bound.isBinding() }) context.surface.write(context.absolute(bound.str("path")!!), value)
    }

    /** Messages of the failing [checks][prop] (A2UI `Checkable`); empty when valid. */
    fun failedChecks(prop: String = "checks"): List<String> = (component[prop] as? JsonArray).orEmpty().mapNotNull { rule ->
        val obj = rule as? JsonObject ?: return@mapNotNull null
        // v1.0 `{condition, message}`; earlier revisions put the call on the rule itself.
        val condition = obj["condition"] ?: obj
        val result = safely { context.resolve(condition) }
        val valid = result?.let(DataContext::truthy) ?: true
        if (valid) null else (result as? JsonObject)?.str("message") ?: obj.str("message") ?: "Invalid value"
    }

    /** Run the [prop] action: an agent `event` (sent with resolved context) or a local `functionCall`. */
    fun dispatch(prop: String = "action") {
        val action = component[prop] as? JsonObject ?: return
        val active = context.activated()
        (action["event"] as? JsonObject)?.let { event ->
            val resolved = JsonObject((event["context"] as? JsonObject).orEmpty().mapValues { (_, v) -> safely { active.resolve(v) } ?: kotlinx.serialization.json.JsonNull })
            host.onAction(
                A2uiAction(
                    surfaceId = context.surface.id,
                    name = event.str("name").orEmpty(),
                    sourceComponentId = id,
                    context = resolved,
                    userMessage = safely { active.string(event["userMessage"]) },
                    dataModel = context.surface.dataModel.takeIf { context.surface.sendDataModel },
                ),
            )
        }
        (action["functionCall"] as? JsonObject)?.let { safely { active.call(it) } }
    }

    /** The children in [prop]: a static id list or a template repeated over a data list. */
    fun children(prop: String = "children"): List<ChildRef> = when (val list = component[prop]) {
        is JsonArray -> list.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::ref) }
        is JsonObject -> {
            val template = list.str("componentId")
            val path = list.str("path")
            val items = path?.let { context.surface.read(context.absolute(it)) }
            val count = (items as? JsonArray)?.size ?: (items as? JsonObject)?.size ?: 0
            if (template == null || path == null) emptyList()
            else (0 until count).mapNotNull { i -> ref(template, context.item(path, i)) }
        }
        else -> emptyList()
    }

    /** The single child whose id is in [prop] (e.g. a Card's or Button's `child`). */
    fun child(prop: String = "child"): ChildRef? = (component[prop] as? JsonPrimitive)?.contentOrNull?.let(::ref)

    /** A child referenced by id somewhere else in the properties (e.g. a tab's `child`). */
    fun ref(id: String, scope: DataContext = context): ChildRef? =
        context.surface.component(id)?.let { ChildRef(id, it, scope) }

    @Composable
    fun Render(child: ChildRef?, modifier: Modifier = Modifier) {
        if (child != null) RenderComponent(child.component, child.context, host, modifier)
    }

    private inline fun <T> safely(block: () -> T): T? = runCatching(block).getOrNull()
}

internal class SurfaceHost(
    val catalog: A2uiCatalog,
    val onAction: (A2uiAction) -> Unit,
    /** Other catalogs by id (A2UI mixable catalogs): components naming one render from it. */
    val catalogs: Map<String, A2uiCatalog> = emptyMap(),
) {
    /** A component's renderer: its own `catalogId`'s, else the surface's catalog, else the main one. */
    fun renderer(component: JsonObject, surfaceCatalog: String?): A2uiComponent? {
        val type = component.str("component") ?: return null
        val catalogId = component.str("catalogId") ?: surfaceCatalog
        return catalogId?.let { catalogs[it]?.components?.get(type) } ?: catalog.components[type]
    }
}

@Composable
internal fun RenderComponent(component: JsonObject, context: DataContext, host: SurfaceHost, modifier: Modifier = Modifier) {
    val renderer = host.renderer(component, context.surface.catalogId) ?: return // unknown types are skipped (progressive rendering)
    val scope = ComponentScope(component, context, host)
    val accessibility = component["accessibility"] as? JsonObject
    if (accessibility != null && context.boolean(accessibility["hidden"]) == true) return
    val label = accessibility?.let { runCatching { context.string(it["label"]) }.getOrNull() }
    renderer.Render(
        scope,
        modifier.testTag("a2ui-${scope.id}").then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}

/**
 * Renders an A2UI surface natively with [catalog] (default: the Basic Catalog); nothing until its
 * `root` component arrives. [onAction] receives the user's actions as renderer-to-agent messages
 * ([A2uiAction.toMessage]) for the transport to send back; links open through `LocalUriHandler`.
 */
@Composable
fun A2uiSurfaceView(
    surface: A2uiSurface,
    modifier: Modifier = Modifier,
    catalog: A2uiCatalog = A2uiCatalog.Basic,
    onAction: (A2uiAction) -> Unit = {},
    /** More catalogs components may name with `catalogId` (mixable catalogs). */
    catalogs: List<A2uiCatalog> = emptyList(),
) {
    val uri = LocalUriHandler.current
    val root = surface.component("root") ?: return
    val host = SurfaceHost(catalog, onAction, catalogs.associateBy { it.id })
    val context = DataContext(surface, catalog.functions, effects = A2uiEffects { uri.openUri(it) })
    RenderComponent(root, context, host, modifier.testTag("a2ui-surface-${surface.id}"))
}
