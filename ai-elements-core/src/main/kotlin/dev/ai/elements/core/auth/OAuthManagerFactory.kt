package dev.ai.elements.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Builds an [OAuthManager] wired to open the authorization URL in the system
 * browser via `Intent.ACTION_VIEW` (swap for Chrome Custom Tabs by passing a
 * different [openBrowser]).
 *
 * @param openBrowser how to present the authorization URL. Defaults to the system
 *   browser; the activity context is captured from [context].
 */
class OAuthManagerFactory(
    private val context: Context,
    private val tokenStore: OAuthTokenStore,
    private val openBrowser: (Uri) -> Unit = { uri ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, "Cannot open browser: ${it.message}", Toast.LENGTH_LONG).show()
        }
    },
) {
    fun create(spec: OAuthSpec): OAuthManager = OAuthManager(spec, tokenStore, openBrowser)
}
