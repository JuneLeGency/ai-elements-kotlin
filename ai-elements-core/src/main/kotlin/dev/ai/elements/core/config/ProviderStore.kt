package dev.ai.elements.core.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.TokenSource
import dev.ai.elements.core.auth.TokenStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * API keys encrypted with an AES-GCM key held in the Android Keystore (the key
 * never leaves secure hardware where available). Replaces the deprecated
 * `androidx.security:security-crypto`.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_elements_secrets", Context.MODE_PRIVATE)

    fun get(id: String): String {
        val stored = prefs.getString(id, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun put(id: String, secret: String) {
        if (secret.isEmpty()) {
            prefs.edit().remove(id).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(id, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ai_elements_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** SecretStore key prefix for a profile's OAuth tokens. */
private const val TOKENS_PREFIX = "oauth-tokens:"

/**
 * Saved provider profiles (SharedPreferences) with their API keys and OAuth tokens kept encrypted
 * in [SecretStore]; exposes the list and the selected profile as flows. [presets] (built in, the
 * first one selected by default) are merged in on load, so new presets appear after an app update.
 */
class ProviderStore(context: Context, val presets: List<ProviderProfile> = ProviderProfile.Presets) {
    private val prefs = context.getSharedPreferences("ai_elements_providers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(ProviderProfile.serializer())
    val secrets = SecretStore(context)

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<ProviderProfile>> = _profiles.asStateFlow()

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null) ?: presets.first().id)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val selected: ProviderProfile
        get() = _profiles.value.firstOrNull { it.id == _selectedId.value } ?: _profiles.value.first()

    fun select(id: String) {
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun upsert(profile: ProviderProfile) {
        _profiles.update { list ->
            if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
            else list + profile
        }
        save()
    }

    fun remove(id: String) {
        _profiles.update { list -> list.filterNot { it.id == id && !it.builtIn } }
        secrets.put(id, "")
        secrets.put(TOKENS_PREFIX + id, "")
        if (_selectedId.value == id) select(presets.first().id)
        save()
    }

    /** Restore a built-in preset to its defaults (keeps its API key). */
    fun reset(id: String) {
        presets.firstOrNull { it.id == id }?.let(::upsert)
    }

    fun apiKey(id: String): String = secrets.get(id)
    fun setApiKey(id: String, key: String) = secrets.put(id, key.trim())

    /** OAuth tokens for a signed-in profile, encrypted like API keys. */
    fun tokenStore(id: String): TokenStore = object : TokenStore {
        override fun load(): OAuthTokens? =
            secrets.get(TOKENS_PREFIX + id).takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString<OAuthTokens>(it) }.getOrNull() }

        override fun save(tokens: OAuthTokens?) {
            secrets.put(TOKENS_PREFIX + id, tokens?.let { json.encodeToString(OAuthTokens.serializer(), it) }.orEmpty())
            _tokenVersion.update { it + 1 }
        }
    }

    /** One per profile, so concurrent turns share a single refresh. */
    private val tokenSources = mutableMapOf<String, TokenSource>()

    fun tokenSource(profile: ProviderProfile): TokenSource = synchronized(tokenSources) {
        tokenSources.getOrPut(profile.id) { TokenSource(requireNotNull(profile.oauth), tokenStore(profile.id)) }
    }

    /** Bumps whenever sign-in state changes, so UI can re-read it. */
    private val _tokenVersion = MutableStateFlow(0)
    val tokenVersion: StateFlow<Int> = _tokenVersion.asStateFlow()

    private fun load(): List<ProviderProfile> {
        val saved = prefs.getString(KEY_PROFILES, null)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            .orEmpty()
        val savedIds = saved.map { it.id }.toSet()
        return saved + presets.filter { it.id !in savedIds }
    }

    private fun save() {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(listSerializer, _profiles.value)).apply()
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_SELECTED = "selected"
    }
}
