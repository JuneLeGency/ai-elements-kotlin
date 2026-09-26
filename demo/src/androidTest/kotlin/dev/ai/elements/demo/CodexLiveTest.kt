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
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.jwtClaims
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
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

    @Test
    fun chatgptSubscription_fullChainOnDevice() {
        assumeTrue("copy a Codex CLI auth.json to files/live_codex_auth.json", authFile.exists())
        val tokens = try {
            readWithoutRefresh(authFile)
        } finally {
            authFile.delete()
        }
        assumeTrue("access token expired — run `codex` once to renew it", !tokens.expiresSoon(slackMs = 120_000))

        context.getSharedPreferences("ai_elements_providers", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "conversations.json").delete()
        val provider = OAuthProvider.CHATGPT
        val profile = ProviderProfile(
            id = PROFILE_ID, name = "ChatGPT (live)", kind = provider.kind,
            baseUrl = provider.baseUrl, model = provider.defaultModel, oauth = provider,
        )
        ProviderStore(context).apply {
            upsert(profile)
            tokenStore(PROFILE_ID).save(tokens)
            select(PROFILE_ID)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        compose.onNodeWithTag("prompt-input").performClick().performTextInput("Use the calculate tool to compute 1234 * 5678, then state the result.")
        compose.onNodeWithTag("send-button").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("regenerate"), 180_000)
        compose.onNodeWithTag("chat-error").assertDoesNotExist()
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-calculate"))
        compose.onNodeWithTag("conversation").performScrollToNode(
            hasText("7,006,652", substring = true) or hasText("7006652", substring = true),
        )
    }

    @After
    fun cleanUp() {
        scenario?.close()
        authFile.delete()
        ProviderStore(context).remove(PROFILE_ID)
    }

    private companion object {
        const val PROFILE_ID = "live-chatgpt"
    }
}
