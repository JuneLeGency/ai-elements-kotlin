package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.MockChatBackend
import dev.ai.elements.core.auth.OAuthTokenStore
import dev.ai.elements.core.config.Credential
import dev.ai.elements.core.config.GatewayConfig
import dev.ai.elements.core.config.GatewayConfigStore
import dev.ai.elements.core.config.GatewayProvider

/**
 * Build the right [ChatBackend] for the current gateway [config].
 *
 * @param config the live gateway settings (re-read per request via [configRef]).
 * @param configRef a supplier that always returns the latest [GatewayConfig]
 *   (so model/temperature edits apply without rebuilding the backend).
 * @param store used to resolve the live secret for the configured credential
 *   (API key from the encrypted prefs).
 * @param oauthTokenStore used to resolve a refreshed OAuth access token when the
 *   credential is [Credential.OAuth]. Pass null when OAuth is not in use.
 *
 * This is the single seam the app uses to swap gateways at runtime.
 */
fun backendFor(
    config: GatewayConfig,
    configRef: () -> GatewayConfig = { config },
    store: GatewayConfigStore? = null,
    oauthTokenStore: OAuthTokenStore? = null,
): ChatBackend {
    // Resolve the live auth value at request time (suspend, so OAuth tokens can
    // be refreshed lazily). For API keys this reads the encrypted store; for
    // OAuth it pulls a (possibly refreshed) token from [oauthTokenStore].
    val authProvider: suspend () -> String = {
        val live = configRef()
        when (val cred = live.credential) {
            is Credential.OAuth -> oauthTokenStore?.validAccessToken(cred.instanceId).orEmpty()
            is Credential.ApiKey -> store?.secretFor(cred).orEmpty()
            Credential.None -> ""
        }
    }

    return when (config.provider) {
        GatewayProvider.VERCEL_STREAM -> VercelDataStreamBackend(configRef, authProvider)
        GatewayProvider.OPENAI_COMPATIBLE -> OpenAiBackend(configRef, authProvider)
        GatewayProvider.MOCK -> MockChatBackend()
    }
}
