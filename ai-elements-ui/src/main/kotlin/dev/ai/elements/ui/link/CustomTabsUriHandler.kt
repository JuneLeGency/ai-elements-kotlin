package dev.ai.elements.ui.link

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import dev.ai.elements.ui.theme.isDark

/**
 * Opens web links in a [Custom Tab](https://developer.chrome.com/docs/android/custom-tabs): the
 * user's browser inside the app, with the app's colours and a way back, as Android recommends for
 * links in content. Other schemes (`mailto:`, `tel:`, app links) go to the app that handles them.
 *
 * [AiElementsTheme][dev.ai.elements.ui.theme.AiElementsTheme] provides one as `LocalUriHandler`,
 * so citations, sources, Markdown links, A2UI and MCP Apps links all open this way.
 *
 * @param preferNativeApp open a link in an installed app that handles it (e.g. a video in its
 *   app) before falling back to the Custom Tab (Android 11+, `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`).
 */
class CustomTabsUriHandler(
    private val context: Context,
    private val toolbarColor: Int? = null,
    private val darkTheme: Boolean? = null,
    private val preferNativeApp: Boolean = false,
) : UriHandler {
    override fun openUri(uri: String) {
        val parsed = Uri.parse(uri)
        val newTask = context.findActivity() == null
        if (parsed.scheme?.lowercase() !in WEB_SCHEMES) {
            view(parsed, newTask)
            return
        }
        if (preferNativeApp && Build.VERSION.SDK_INT >= 30 && openInNativeApp(parsed, newTask)) return
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .apply {
                darkTheme?.let { setColorScheme(if (it) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT) }
                toolbarColor?.let { setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build()) }
            }
            .build()
        if (newTask) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Without a Custom Tabs browser the intent still opens the default browser.
        try {
            tab.launchUrl(context, parsed)
        } catch (_: ActivityNotFoundException) {
            view(parsed, newTask)
        }
    }

    private fun openInNativeApp(uri: Uri, newTask: Boolean): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or (if (newTask) Intent.FLAG_ACTIVITY_NEW_TASK else 0)),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun view(uri: Uri, newTask: Boolean) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: ActivityNotFoundException) {
            // Nothing handles it: like the platform handler, ignore rather than crash the chat.
        }
    }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}

/** A [CustomTabsUriHandler] with the current theme's surface colour and light / dark scheme. */
@Composable
fun rememberCustomTabsUriHandler(preferNativeApp: Boolean = false): UriHandler {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val dark = MaterialTheme.isDark
    return remember(context, surface, dark, preferNativeApp) { CustomTabsUriHandler(context, surface, dark, preferNativeApp) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
