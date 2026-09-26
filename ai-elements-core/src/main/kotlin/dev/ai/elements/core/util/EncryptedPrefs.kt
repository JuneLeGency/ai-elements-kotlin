package dev.ai.elements.core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Self-healing wrapper around [EncryptedSharedPreferences].
 *
 * Mirrors OpenMinis' `EncryptedPrefsFactory`: on a crypto failure (e.g. a corrupt
 * Tink keyset on some OEM/Android versions) it wipes the backing store and retries
 * once, then falls back to a plain-text prefs file so the app keeps working. The
 * fallback is only used for secrets when the hardware keystore is genuinely
 * unavailable; on a normal device the encrypted path is taken.
 */
object EncryptedPrefs {

    /** Returns a [SharedPreferences] for [name], encrypted when possible. */
    fun create(context: Context, name: String): SharedPreferences {
        val appContext = context.applicationContext
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Retry once after wiping the corrupt store.
            try {
                appContext.deleteSharedPreferences(name)
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                // Last resort: plain prefs (secrets stored in cleartext).
                appContext.getSharedPreferences("${name}_plain_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
