package dev.ai.elements.core.config

import android.content.Context
import android.content.SharedPreferences
import dev.ai.elements.core.util.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which gateway protocol the app talks to.
 *
 * @property label short human-readable name for the settings UI.
 * @property baseUrl default endpoint root (overridable per-instance).
 * @property path the HTTP path appended to [baseUrl] for a chat request.
 * @property authHeader the header used to carry the credential.
 * @property defaultModel a sensible default model id.
 * @property usesVercelStream whether the response is the Vercel AI Data Stream
 *   Protocol (single-byte event codes) vs raw OpenAI-compatible SSE.
 * @property supportsOAuth whether the provider can be authenticated via OAuth.
 */
enum class GatewayProvider(
    val label: String,
    val baseUrl: String,
    val path: String,
    val authHeader: String,
    val defaultModel: String,
    val usesVercelStream: Boolean,
    val supportsOAuth: Boolean,
) {
    /** The local AI Elements gateway (PydanticAI -> CLIProxyAPI), Data Stream protocol. */
    VERCEL_STREAM(
        label = "AI Elements gateway",
        baseUrl = "http://10.0.2.2:8787",
        path = "/api/chat",
        authHeader = "Authorization",
        defaultModel = "gpt-4o",
        usesVercelStream = true,
        supportsOAuth = false,
    ),

    /** Any OpenAI-compatible endpoint (CLIProxyAPI, LiteLLM, freellmapi, ...). */
    OPENAI_COMPATIBLE(
        label = "OpenAI-compatible",
        baseUrl = "http://10.0.2.2:9090/v1",
        path = "/chat/completions",
        authHeader = "Authorization",
        defaultModel = "gpt-4o",
        usesVercelStream = false,
        supportsOAuth = true,
    ),

    /** In-memory canned reply — no network. */
    MOCK(
        label = "Mock (offline)",
        baseUrl = "",
        path = "",
        authHeader = "",
        defaultModel = "mock",
        usesVercelStream = false,
        supportsOAuth = false,
    ),
}

/**
 * How a provider is authenticated. The *value* of the secret (the raw API key or
 * the OAuth token) is NOT held here — it lives in [SecretStore] (encrypted).
 *
 * @see ApiKeyCredential — the secret is a static key the user typed.
 * @see OAuthCredential — the secret is an OAuth token, refreshed lazily.
 */
sealed interface Credential {
    /** No credential (mock / anonymous). */
    data object None : Credential

    /** A static API key, stored encrypted in [SecretStore]. */
    data class ApiKey(
        val secretRef: String = SecretStore.API_KEY_REF,
    ) : Credential

    /** An OAuth token; [instanceId] keys the [OAuthCredentialStore]. */
    data class OAuth(
        val instanceId: String,
    ) : Credential
}

/**
 * Immutable snapshot of the gateway settings.
 *
 * The demo's settings screen edits this via [GatewayConfigStore]; the
 * [dev.ai.elements.core.ChatController]'s backend is rebuilt from it, so changes
 * apply on the next `send()`. Secrets are referenced by [credential], never stored
 * in this data class.
 */
data class GatewayConfig(
    val provider: GatewayProvider = GatewayProvider.VERCEL_STREAM,
    val baseUrl: String = GatewayProvider.VERCEL_STREAM.baseUrl,
    val credential: Credential = Credential.None,
    val model: String = GatewayProvider.VERCEL_STREAM.defaultModel,
    val temperature: Float = 0.7f,
) {
    val url: String
        get() = baseUrl.trimEnd('/') + provider.path

    /** True when the provider needs a credential and one is configured. */
    val requiresAuth: Boolean
        get() = provider != GatewayProvider.MOCK
}

/**
 * Persists [GatewayConfig] (non-secret fields in plain prefs) and the credential
 * secret (in [EncryptedSharedPreferences]). Exposed as a [StateFlow] so Compose
 * screens can observe + edit it.
 *
 * The secret is keyed by a stable [SecretStore.API_KEY_REF] for API-key
 * credentials; OAuth tokens are keyed by their instance id and managed separately
 * by the OAuth token store.
 */
class GatewayConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secrets: SharedPreferences = EncryptedPrefs.create(context, SECRETS_PREFS)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<GatewayConfig> = _config.asStateFlow()

    fun update(transform: (GatewayConfig) -> GatewayConfig) {
        val next = transform(_config.value)
        _config.update { next }
        save(next)
    }

    /** Read the live secret for the current [credential] (API key or OAuth token). */
    fun secretFor(credential: Credential): String = when (credential) {
        is Credential.ApiKey -> secrets.getString(credential.secretRef, "") ?: ""
        is Credential.OAuth -> "" // OAuth tokens are resolved by the token store at request time.
        Credential.None -> ""
    }

    private fun load(): GatewayConfig {
        val provider = try {
            GatewayProvider.valueOf(prefs.getString(KEY_PROVIDER, GatewayProvider.VERCEL_STREAM.name)!!)
        } catch (_: Exception) {
            GatewayProvider.VERCEL_STREAM
        }
        val credentialType = prefs.getString(KEY_CREDENTIAL, null)
        val credentialInstanceId = prefs.getString(KEY_CREDENTIAL_INSTANCE, null).orEmpty()
        val credential = when (credentialType) {
            "ApiKey" -> Credential.ApiKey()
            "OAuth" -> if (credentialInstanceId.isBlank()) Credential.None
            else Credential.OAuth(credentialInstanceId)
            else -> Credential.None
        }
        return GatewayConfig(
            provider = provider,
            baseUrl = prefs.getString(KEY_BASE_URL, provider.baseUrl) ?: provider.baseUrl,
            credential = credential,
            model = prefs.getString(KEY_MODEL, provider.defaultModel) ?: provider.defaultModel,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
        )
    }

    private fun save(c: GatewayConfig) {
        val instanceId = (c.credential as? Credential.OAuth)?.instanceId.orEmpty()
        prefs.edit()
            .putString(KEY_PROVIDER, c.provider.name)
            .putString(KEY_BASE_URL, c.baseUrl)
            .putString(KEY_CREDENTIAL, c.credential::class.simpleName)
            .putString(KEY_CREDENTIAL_INSTANCE, instanceId)
            .putString(KEY_MODEL, c.model)
            .putFloat(KEY_TEMPERATURE, c.temperature)
            .apply()
        // The raw API key, if present in the in-memory view, is written by the
        // settings screen via [writeApiKey]; here we just keep the ref stable.
    }

    /** Persist (or clear) the raw API key for an API-key credential. */
    fun writeApiKey(plainKey: String) {
        secrets.edit().putString(SecretStore.API_KEY_REF, plainKey).apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_elements_gateway"
        const val SECRETS_PREFS = "ai_elements_gateway_secrets"
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_CREDENTIAL = "credential"
        const val KEY_CREDENTIAL_INSTANCE = "credential_instance"
        const val KEY_MODEL = "model"
        const val KEY_TEMPERATURE = "temperature"
    }
}

/** Stable refs for encrypted secrets. */
object SecretStore {
    const val API_KEY_REF = "api_key"
}
