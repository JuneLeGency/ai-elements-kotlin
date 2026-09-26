package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatBackendException
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.TokenSource
import dev.ai.elements.core.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [ChatBackend] for signed-in providers: takes a fresh access token each
 * turn and builds the protocol backend with it. If the provider rejects the
 * token (401) before anything was streamed, it refreshes once and retries.
 */
class OAuthBackend(
    private val tokens: TokenSource,
    private val backendFor: (OAuthTokens) -> ChatBackend,
) : ChatBackend {
    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        var emitted = false
        try {
            backendFor(tokens.fresh()).stream(history).collect { emitted = true; emit(it) }
        } catch (e: ChatBackendException) {
            if (emitted || e.statusCode != 401) throw e
            backendFor(tokens.fresh(forceRefresh = true)).stream(history).collect { emit(it) }
        }
    }
}
