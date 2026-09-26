package dev.ai.elements.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) + OAuth state helpers. Pure Kotlin (no Android deps) so it is
 * unit-testable on the JVM and KMP-friendly.
 *
 * @property verifier the `code_verifier` (random, base64url, 43–128 chars).
 * @property challenge the `code_challenge` = BASE64URL(SHA256(verifier)).
 * @property state the `state` nonce used to detect CSRF on the callback.
 */
data class Pkce(
    val verifier: String,
    val challenge: String,
    val state: String,
) {
    companion object {
        private val RANDOM = SecureRandom()

        /** Generate a fresh PKCE triple. [verifierBytes] defaults to 32 (64 base64url chars). */
        fun generate(verifierBytes: Int = 32): Pkce {
            val verifier = randomBase64Url(verifierBytes)
            val challenge = base64UrlNoPad(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
            val state = randomBase64Url(24)
            return Pkce(verifier, challenge, state)
        }

        private fun randomBase64Url(bytes: Int): String {
            val buf = ByteArray(bytes)
            RANDOM.nextBytes(buf)
            return base64UrlNoPad(buf)
        }

        private fun base64UrlNoPad(input: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    }
}
