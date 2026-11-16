package io.aubergine.ambit.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database passphrase: 32 random bytes, stored only in wrapped form. The wrapping key
 * lives in the Android Keystore and never leaves it, so copying the app's files off the
 * phone is not enough to read the database.
 */
class DatabaseKey(private val context: Context) {

    private val file: File get() = File(context.noBackupFilesDir, FILE)

    fun exists(): Boolean = file.exists()

    /** Returns the passphrase, creating and wrapping a new one on first use. */
    fun passphrase(): ByteArray {
        if (file.exists()) return unwrap(file.readBytes())
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.writeBytes(wrap(raw))
        return raw
    }

    /** Forgets the key. Whatever it protected becomes unreadable. */
    fun destroy() {
        file.delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(raw)
    }

    private fun unwrap(stored: ByteArray): ByteArray {
        val ivLength = stored[0].toInt()
        val iv = stored.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(stored, 1 + ivLength, stored.size - 1 - ivLength)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun wrappingKey(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
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
        const val ALIAS = "ambit_db_wrap"
        const val FILE = "db.key"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
