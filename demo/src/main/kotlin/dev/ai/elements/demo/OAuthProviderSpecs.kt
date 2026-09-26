package dev.ai.elements.demo

import dev.ai.elements.core.auth.OAuthSpec
import dev.ai.elements.core.auth.TokenResponseFormat

/**
 * Ready-made [OAuthSpec]s for the demo. Each is a plain value describing one
 * provider's authorization-code + PKCE flow — add a new provider by adding a new
 * [OAuthSpec] here (no new class per vendor, which is the point of the generic
 * framework).
 *
 * The OpenAI spec below uses OpenAI's public OAuth endpoints (ChatGPT sign-in).
 * For a private deployment, swap [authUrl] / [tokenUrl] / [clientId] for your
 * registered client. The callback port is the loopback port the server listens
 * on before the browser is opened.
 */
object OAuthProviderSpecs {

    private const val OAUTH_INSTANCE = "openai"
    private const val CLIENT_ID = "app_KQ3av5zYhYBcAOL83JtXX0vS"

    /** ChatGPT / OpenAI sign-in via authorization code + PKCE. */
    fun openAi(): OAuthSpec = OAuthSpec(
        instanceId = OAUTH_INSTANCE,
        providerLabel = "OpenAI",
        authUrl = "https://auth.openai.com/oauth/authorize",
        tokenUrl = "https://auth.openai.com/oauth/token",
        clientId = CLIENT_ID,
        clientSecret = "",
        callbackPort = 8465,
        fallbackPorts = listOf(8466, 8467),
        redirectPath = "/oauth/callback",
        scopes = "",
        extraAuthParams = mapOf(
            "codex_cli_sandbox_mode" to "danger-full-access",
            "challenges" to "create",
        ),
        tokenResponseFormat = TokenResponseFormat.JSON,
    )

    fun instanceId(): String = OAUTH_INSTANCE
}
