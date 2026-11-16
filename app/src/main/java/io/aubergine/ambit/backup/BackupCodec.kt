package io.aubergine.ambit.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backup container.
 *
 * Layout: magic "AMBITBK1", PBKDF2 iteration count (int), 16-byte salt, 12-byte nonce, then
 * AES-256-GCM ciphertext with the 16-byte tag at the end. The header is authenticated as
 * associated data, so changing the iteration count or salt also fails the tag check.
 *
 * The whole payload is held in memory. Backups are a few tens of megabytes at most, and
 * GCM only proves integrity once the last byte is read, so streaming would hand back
 * plaintext that might still turn out to be forged.
 */
object BackupCodec {

    private val MAGIC = "AMBITBK1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    class WrongPasswordOrDamaged : Exception("wrong password, or the file is damaged")
    class NotABackup : Exception("not an Ambit backup")

    fun encrypt(plain: ByteArray, password: CharArray, out: OutputStream, iterations: Int = ITERATIONS) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = header(iterations, salt, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        out.write(header)
        out.write(cipher.doFinal(plain))
    }

    fun decrypt(input: InputStream, password: CharArray): ByteArray {
        val all = input.readBytes()
        val data = DataInputStream(all.inputStream())
        val magic = ByteArray(MAGIC.size)
        if (all.size < MAGIC.size + 4 + SALT_BYTES + NONCE_BYTES) throw NotABackup()
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw NotABackup()
        val iterations = data.readInt()
        if (iterations !in 10_000..10_000_000) throw NotABackup()
        val salt = ByteArray(SALT_BYTES).also(data::readFully)
        val nonce = ByteArray(NONCE_BYTES).also(data::readFully)
        val headerLength = MAGIC.size + 4 + SALT_BYTES + NONCE_BYTES
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(all, 0, headerLength)
        return try {
            cipher.doFinal(all, headerLength, all.size - headerLength)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordOrDamaged()
        }
    }

    private fun header(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.write(MAGIC)
            it.writeInt(iterations)
            it.write(salt)
            it.write(nonce)
        }
        return buffer.toByteArray()
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, iterations, 256))
            .encoded
        return SecretKeySpec(bytes, "AES")
    }
}
