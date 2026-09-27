package dev.ai.elements.genui.a2ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** A component scope as the renderer builds it, for logic tests without Compose. */
fun testScope(
    surface: A2uiSurface,
    component: String? = null,
    onAction: (A2uiAction) -> Unit = {},
    openUrl: (String) -> Unit = {},
    catalog: A2uiCatalog = A2uiCatalog.Basic,
): ComponentScope {
    val json = component?.let { Json.parseToJsonElement(it).jsonObject } ?: surface.component("root")!!
    return ComponentScope(json, DataContext(surface, catalog.functions, effects = A2uiEffects(openUrl)), SurfaceHost(catalog, onAction))
}

/** The scope a child renders in (template items keep their own data scope). */
fun testScope(child: ChildRef, catalog: A2uiCatalog = A2uiCatalog.Basic): ComponentScope =
    ComponentScope(child.component, child.context, SurfaceHost(catalog) {})
