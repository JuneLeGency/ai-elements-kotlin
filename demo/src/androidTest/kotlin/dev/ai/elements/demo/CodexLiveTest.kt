package dev.ai.elements.demo

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.jwtClaims
import dev.ai.elements.core.config.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Opt-in, on-device, real end-to-end run through a ChatGPT subscription:
 * chat UI → OAuthBackend → chatgpt.com Codex backend → on-device tool loop →
 * rendered reply.
 *
 * Needs a Codex CLI `auth.json` copied into the app's private files as
 * `live_codex_auth.json` (e.g. `cat auth.json | adb shell run-as <pkg> sh -c 'cat > files/live_codex_auth.json'`).
 * The file is deleted as soon as it is read, and the **refresh token is
 * dropped**: the app can never refresh (ChatGPT rotates refresh tokens, which
 * would sign the CLI out). The profile and its token are removed afterwards.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CodexLiveTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val authFile get() = File(context.filesDir, "live_codex_auth.json")
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun readWithoutRefresh(file: File): OAuthTokens {
        val t = Json.parseToJsonElement(file.readText()).jsonObject["tokens"]!!.jsonObject
        fun str(key: String) = t[key]?.jsonPrimitive?.contentOrNull
        val access = str("access_token")!!
        val id = str("id_token")
        val account = str("account_id")
            ?: ((jwtClaims(id)?.get("https://api.openai.com/auth") as? JsonObject)?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull)
        return OAuthTokens(
            accessToken = access,
            refreshToken = null,
            expiresAtMs = jwtClaims(access)?.get("exp")?.jsonPrimitive?.longOrNull?.let { it * 1000 },
            idToken = id,
            accountId = account,
            email = jwtClaims(id)?.get("email")?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Signs the live ChatGPT profile in with the copied tokens (refresh token dropped) and runs [setup]. */
    private fun signIn(setup: (DemoApplication) -> Unit = {}): DemoApplication {
        assumeTrue("copy a Codex CLI auth.json to files/live_codex_auth.json", authFile.exists())
        val tokens = try {
            readWithoutRefresh(authFile)
        } finally {
            authFile.delete()
        }
        assumeTrue("access token expired — run `codex` once to renew it", !tokens.expiresSoon(slackMs = 120_000))
        val app = resetDemoApp()
        val provider = OAuthProvider.CHATGPT
        val profile = ProviderProfile(
            id = PROFILE_ID, name = "ChatGPT (live)", kind = provider.kind,
            baseUrl = provider.baseUrl, model = provider.defaultModel, oauth = provider,
        )
        app.providers.apply {
            upsert(profile)
            tokenStore(PROFILE_ID).save(tokens)
            select(PROFILE_ID)
        }
        setup(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        return app
    }

    private fun send(prompt: String) {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)
        compose.onNodeWithTag("send-button").performClick()
    }

    @Test
    fun chatgptSubscription_fullChainOnDevice() {
        signIn()
        send("Use the calculate tool to compute 1234 * 5678, then state the result.")
        compose.awaitTurnEnd(180_000)
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-calculate"))
        compose.onNodeWithTag("conversation").performScrollToNode(
            hasText("7,006,652", substring = true) or hasText("7006652", substring = true),
        )
    }

    /**
     * A real model picks an MCP tool that has a view (MCP Apps); the view renders in the sandbox with
     * the tool's result. Needs the reference server (`agentServer`, default the emulator's host).
     */
    @Test
    fun chatgptSubscription_mcpAppOnDevice() {
        val server = InstrumentationRegistry.getArguments().getString("agentServer") ?: "http://10.0.2.2:8788"
        signIn { app ->
            app.agents.update { it.copy(mcpEnabled = true) }
            app.mcpServers.upsert(McpServerConfig("notes", "Notes", "$server/mcp"))
        }
        send("Open my notes board so I can see my notes.")
        compose.waitUntil(180_000) { McpAppProbe(compose) { scenario!! }.show().isSuccess }
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-notes__show_notes_board"))
        val probe = McpAppProbe(compose) { scenario!! }
        compose.waitUntil(60_000) { probe.view("d.getElementById('title').textContent").matches(Regex("\"Notes \\(\\d+\\)\"")) }
    }

    @After
    fun cleanUp() {
        scenario?.close()
        authFile.delete()
        resetDemoApp()
    }

    private companion object {
        const val PROFILE_ID = "live-chatgpt"
    }
}
