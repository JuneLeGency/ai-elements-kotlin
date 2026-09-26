package dev.ai.elements.demo.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dev.ai.elements.core.auth.AuthorizationCodeFlow
import dev.ai.elements.core.auth.DeviceAuthorization
import dev.ai.elements.core.auth.DeviceCodeFlow
import dev.ai.elements.core.auth.LoopbackReceiver
import dev.ai.elements.core.auth.OAuthClient
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.config.ProviderStore
import dev.ai.elements.core.auth.LoopbackRedirect
import dev.ai.elements.core.mcp.McpAuthRequiredException
import dev.ai.elements.core.mcp.McpOAuth
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.demo.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a sign-in for one provider profile at a time.
 *
 * Browser sign-in: a loopback listener opens on the provider's registered
 * port, the authorize page opens in a Custom Tab, and when the provider
 * redirects to `http://localhost:<port>/…` the app exchanges the code and
 * brings itself back to the front (closing the tab). Device sign-in shows a
 * code to approve on the provider's page while the app polls.
 */
class SignInController(
    private val app: Context,
    private val providers: ProviderStore,
    private val scope: CoroutineScope,
) {

    sealed interface State {
        data object Idle : State
        data class InBrowser(val profileId: String) : State
        data class Device(val profileId: String, val authorization: DeviceAuthorization) : State
        data class Failed(val profileId: String, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private val client = OAuthClient()

    fun signIn(activity: Activity, profile: ProviderProfile) {
        val provider = profile.oauth ?: return
        job?.cancel()
        job = scope.launch {
            try {
                when (val flow = provider.flow) {
                    is AuthorizationCodeFlow -> browser(activity, profile, provider, flow)
                    is DeviceCodeFlow -> device(profile, flow)
                }
                _state.value = State.Idle
            } catch (e: CancellationException) {
                _state.value = State.Idle
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(profile.id, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * MCP authorization for a remote server (MCP spec: RFC 9728 / 8414 discovery, dynamic
     * client registration, PKCE with the RFC 8707 resource). The registration is kept, so later
     * sign-ins reuse the same client and loopback port.
     */
    fun signInMcp(activity: Activity, store: McpServerStore, server: McpServerConfig, challenge: McpAuthRequiredException?, onDone: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            try {
                val registration = store.registration(server.id) ?: McpOAuth().discover(
                    serverUrl = server.url,
                    resourceMetadataUrl = challenge?.resourceMetadataUrl,
                    scope = challenge?.scope,
                    redirect = LoopbackRedirect("127.0.0.1", freePort()),
                    clientName = "AI Elements",
                ).also { store.setRegistration(server.id, it) }
                val flow = registration.flow()
                withContext(Dispatchers.IO) { LoopbackReceiver.open(flow.redirect) }.use { receiver ->
                    val attempt = client.begin(flow)
                    _state.value = State.InBrowser(server.id)
                    open(activity, attempt.url)
                    store.tokenStore(server.id).save(client.exchange(flow, attempt, receiver.awaitCode(attempt.state)))
                }
                store.upsert(server) // drops the cached client so the new tokens are used
                returnToApp()
                _state.value = State.Idle
                onDone()
            } catch (e: CancellationException) {
                _state.value = State.Idle
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(server.id, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    fun cancel() {
        job?.cancel()
        _state.value = State.Idle
    }

    fun dismissError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }

    fun signOut(profile: ProviderProfile) {
        if (profile.usesTokens) providers.tokenStore(profile.id).save(null) else providers.setApiKey(profile.id, "")
    }

    private suspend fun browser(activity: Activity, profile: ProviderProfile, provider: OAuthProvider, flow: AuthorizationCodeFlow) {
        val receiver = withContext(Dispatchers.IO) { LoopbackReceiver.open(flow.redirect) }
        receiver.use {
            val attempt = client.begin(flow)
            _state.value = State.InBrowser(profile.id)
            open(activity, attempt.url) // the only use of the Activity: a sign-in outlives rotations
            val code = receiver.awaitCode(attempt.state)
            if (provider == OAuthProvider.OPENROUTER) {
                providers.setApiKey(profile.id, client.openRouterKey(OAuthProvider.OPENROUTER_KEYS_URL, code, attempt))
            } else {
                providers.tokenStore(profile.id).save(client.exchange(flow, attempt, code))
            }
        }
        returnToApp()
    }

    private suspend fun device(profile: ProviderProfile, flow: DeviceCodeFlow) {
        val authorization = client.startDevice(flow)
        _state.value = State.Device(profile.id, authorization)
        providers.tokenStore(profile.id).save(client.pollDevice(flow, authorization))
        returnToApp()
    }

    /** Opens the provider's page in a Custom Tab (falls back to the browser). */
    fun open(activity: Activity, url: String) {
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(url))
        }.onFailure {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /** Pops the Custom Tab (it sits on top of our task) and shows the app again. */
    private fun returnToApp() {
        app.startActivity(
            Intent(app, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }
}
