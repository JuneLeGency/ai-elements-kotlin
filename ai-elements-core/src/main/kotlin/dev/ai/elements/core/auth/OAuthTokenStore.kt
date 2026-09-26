package dev.ai.elements.core.auth

import android.content.Context
import dev.ai.elements.core.util.EncryptedPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An OAuth token set for one provider instance.
 *
 * @property accessToken the bearer token used in requests.
 * @property refreshToken used to obtain a fresh [accessToken] (may be null for
 *   providers that issue non-refreshable tokens).
 * @property expiresAtMillis absolute expiry in epoch millis (0 = unknown/never).
 */
@Serializable
data class OAuthToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMillis: Long = 0L,
    val accountEmail: String? = null,
) {
    fun isExpired(bufferMs: Long = 0L): Boolean =
        expiresAtMillis > 0L && System.currentTimeMillis() >= expiresAtMillis - bufferMs
}

/**
 * EncryptedSharedPreferences-backed store of [OAuthToken]s, keyed by instance id.
 *
 * Refresh is **pull-based** (no timer): [validAccessToken] returns the stored
 * token, refreshing it first when it is within [refreshBufferMs] of expiry.
 * Concurrent callers for the same instance are coalesced via a per-instance
 * [Mutex] so the token endpoint is hit once.
 *
 * @param refresh the function that exchanges [OAuthToken.refreshToken] for a new
 *   [OAuthToken]. Injected by the [OAuthManager] so this store stays transport-
 *   agnostic (and unit-testable).
 */
class OAuthTokenStore(
    context: Context,
    private val refresh: suspend (instanceId: String, token: OAuthToken) -> OAuthToken = { _, _ ->
        throw IllegalStateException("no refresh handler configured")
    },
    private val refreshBufferMs: Long = 5 * 60 * 1000L,
) {
    private val prefs = EncryptedPrefs.create(context, PREFS_NAME)
    private val mutexes = HashMap<String, Mutex>()

    private fun mutexFor(id: String): Mutex = synchronized(mutexes) {
        mutexes.getOrPut(id) { Mutex() }
    }

    fun save(instanceId: String, token: OAuthToken) {
        prefs.edit().putString(tokenKey(instanceId), JSON.encodeToString(token)).apply()
    }

    fun load(instanceId: String): OAuthToken? =
        prefs.getString(tokenKey(instanceId), null)?.let {
            runCatching { JSON.decodeFromString(OAuthToken.serializer(), it) }.getOrNull()
        }

    fun isAuthenticated(instanceId: String): Boolean = load(instanceId)?.accessToken?.isNotBlank() == true

    fun logout(instanceId: String) {
        prefs.edit().remove(tokenKey(instanceId)).apply()
    }

    /**
     * Return a usable access token for [instanceId], refreshing if it is about to
     * expire. Returns null when no token is stored or refresh fails on an expired
     * token.
     */
    suspend fun validAccessToken(instanceId: String): String? = mutexFor(instanceId).withLock {
        val current = load(instanceId) ?: return@withLock null
        if (!current.isExpired(refreshBufferMs)) return@withLock current.accessToken
        // Token is (near) expired — try to refresh.
        return@withLock try {
            val fresh = refresh(instanceId, current)
            save(instanceId, fresh)
            fresh.accessToken
        } catch (e: Exception) {
            if (current.isExpired(0)) {
                logout(instanceId) // genuinely expired + refresh failed -> clear
                null
            } else {
                current.accessToken // transient failure; still usable
            }
        }
    }

    private fun tokenKey(instanceId: String) = "oauth_tokens_$instanceId"

    companion object {
        private const val PREFS_NAME = "ai_elements_oauth_tokens"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
