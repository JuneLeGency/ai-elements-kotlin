package dev.ai.elements.core.auth

import dev.ai.elements.core.config.ProviderKind
import kotlinx.serialization.Serializable

/**
 * Sign-in methods the app can offer, and what each one produces.
 *
 * [OPENROUTER] is OpenRouter's documented flow for third-party apps and
 * yields an ordinary API key. The others are **subscription sign-ins**: they
 * reuse the public OAuth client of each vendor's own command-line tool to use
 * the user's consumer plan. Vendors may consider that outside their terms,
 * may revoke those clients at any time, and may restrict accounts that use
 * them — which is why the demo keeps them behind an explicit, warned opt-in.
 * (Claude.ai subscriptions are deliberately absent: Anthropic only permits
 * them in its own products.)
 */
@Serializable
enum class OAuthProvider(
    val label: String,
    /** A subscription sign-in through another tool's client (see the class doc). */
    val experimental: Boolean,
    val kind: ProviderKind,
    val baseUrl: String,
    val defaultModel: String,
) {
    OPENROUTER("OpenRouter", experimental = false, ProviderKind.OPENAI, "https://openrouter.ai/api/v1", "openrouter/auto"),
    CHATGPT("ChatGPT (Codex)", experimental = true, ProviderKind.OPENAI_RESPONSES, "https://chatgpt.com/backend-api/codex", "gpt-5.5"),
    XAI("xAI Grok", experimental = true, ProviderKind.OPENAI, "https://api.x.ai/v1", "grok-4.5"),
    KIMI("Kimi Code", experimental = true, ProviderKind.OPENAI, "https://api.kimi.com/coding/v1", "kimi-for-coding"),
    ;

    /** How the user signs in. */
    val flow: SignInFlow
        get() = when (this) {
            OPENROUTER -> OpenRouterFlow
            CHATGPT -> ChatGptFlow
            XAI -> XaiFlow
            KIMI -> KimiFlow
        }

    /** Headers the API needs besides `Authorization: Bearer <access token>`. */
    fun apiHeaders(tokens: OAuthTokens): Map<String, String> = when (this) {
        CHATGPT -> buildMap {
            put("OpenAI-Beta", "responses=experimental")
            put("originator", "codex_cli_rs")
            tokens.accountId?.let { put("chatgpt-account-id", it) }
        }
        else -> emptyMap()
    }

    /** Where to refresh tokens (null: OpenRouter issues a key, nothing to refresh). */
    internal val refreshClient: Triple<String, String, String?>?
        get() = if (this == OPENROUTER) null else when (val f = flow) {
            is AuthorizationCodeFlow -> Triple(f.tokenUrl, f.clientId, f.clientSecret)
            is DeviceCodeFlow -> Triple(f.tokenUrl, f.clientId, null)
        }

    companion object {
        /** OpenRouter: localhost callbacks must use port 3000; exchanges at [OPENROUTER_KEYS_URL]. */
        val OpenRouterFlow = AuthorizationCodeFlow(
            authorizeUrl = "https://openrouter.ai/auth",
            tokenUrl = OPENROUTER_KEYS_URL,
            clientId = "",
            redirect = LoopbackRedirect(port = 3000, path = "/callback"),
            redirectParam = "callback_url",
            usesState = false,
        )

        val ChatGptFlow = AuthorizationCodeFlow(
            authorizeUrl = "https://auth.openai.com/oauth/authorize",
            tokenUrl = "https://auth.openai.com/oauth/token",
            clientId = "app_EMoamEEZ73f0CkXaXp7hrann",
            redirect = LoopbackRedirect(port = 1455, path = "/auth/callback"),
            scopes = "openid profile email offline_access",
            extraAuthorizeParams = mapOf(
                "id_token_add_organizations" to "true",
                "codex_cli_simplified_flow" to "true",
                "originator" to "codex_cli_rs",
            ),
        )

        val XaiFlow = AuthorizationCodeFlow(
            // From https://auth.x.ai/.well-known/openid-configuration (checked by LiveOAuthEndpointsTest).
            authorizeUrl = "https://auth.x.ai/oauth2/authorize",
            tokenUrl = "https://auth.x.ai/oauth2/token",
            clientId = "b1a00492-073a-47ea-816f-4c329264a828",
            // The client's redirect allow-list pins this exact host and port.
            redirect = LoopbackRedirect(host = "127.0.0.1", port = 56121, path = "/callback"),
            scopes = "openid profile email offline_access grok-cli:access api:access",
            extraAuthorizeParams = mapOf("plan" to "generic"),
            nonce = true,
            echoChallengeOnExchange = true,
        )

        /** Kimi rejects any explicit `scope`, and wants form bodies. */
        val KimiFlow = DeviceCodeFlow(
            deviceAuthorizationUrl = "https://auth.kimi.com/api/oauth/device_authorization",
            tokenUrl = "https://auth.kimi.com/api/oauth/token",
            clientId = "17e5f671-d194-4dfb-9706-5516cb48c098",
        )

        const val OPENROUTER_KEYS_URL = "https://openrouter.ai/api/v1/auth/keys"
    }
}
