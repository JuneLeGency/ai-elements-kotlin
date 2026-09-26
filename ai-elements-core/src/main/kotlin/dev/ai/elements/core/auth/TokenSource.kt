package dev.ai.elements.core.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where signed-in tokens live; implement it over encrypted storage. */
interface TokenStore {
    fun load(): OAuthTokens?
    fun save(tokens: OAuthTokens?)
}

/**
 * Hands out a usable access token, refreshing it when it's about to expire.
 * Refreshes are single-flight: concurrent callers wait for one refresh
 * instead of racing (providers that rotate refresh tokens would otherwise
 * invalidate each other's).
 */
class TokenSource(
    private val provider: OAuthProvider,
    private val store: TokenStore,
    private val client: OAuthClient = OAuthClient(),
    private val now: () -> Long = System::currentTimeMillis,
    /** Tests: refresh against a local server. */
    internal val refreshUrlOverride: String? = null,
) {
    private val mutex = Mutex()

    val signedIn: Boolean get() = store.load() != null

    /** A token valid now; [forceRefresh] after the API rejected the current one. */
    suspend fun fresh(forceRefresh: Boolean = false): OAuthTokens = mutex.withLock {
        val current = store.load() ?: throw OAuthException("Not signed in to ${provider.label}.")
        if (!forceRefresh && !current.expiresSoon(now())) return current
        val (tokenUrl, clientId, secret) = provider.refreshClient ?: return current
        client.refresh(refreshUrlOverride ?: tokenUrl, clientId, secret, current).also(store::save)
    }
}
