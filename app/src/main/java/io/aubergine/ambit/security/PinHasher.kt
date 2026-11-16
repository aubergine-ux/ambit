package io.aubergine.ambit.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 for the app-lock PIN. A short PIN is weak against a determined offline attack
 * whatever the hashing; the point is that the stored value is not the PIN itself and
 * guessing it on the device is slow.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class Stored(val hash: String, val salt: String)

    fun hash(pin: String, salt: ByteArray = newSalt()): Stored = Stored(
        hash = encode(derive(pin, salt)),
        salt = encode(salt),
    )

    fun verify(pin: String, stored: Stored): Boolean {
        val salt = runCatching { Base64.getDecoder().decode(stored.salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(stored.hash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    fun isAcceptable(pin: String): Boolean = pin.length in 4..12 && pin.all { it.isDigit() }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
