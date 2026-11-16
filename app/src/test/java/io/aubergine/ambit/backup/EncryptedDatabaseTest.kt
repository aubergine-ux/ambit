package io.aubergine.ambit.backup

import io.aubergine.ambit.data.db.EncryptedDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EncryptedDatabaseTest {

    @Test
    fun `the raw key literal is the form sqlcipher treats as key bytes`() {
        assertEquals("x'00ff10'", EncryptedDatabase.rawKeyLiteral(byteArrayOf(0, -1, 16)))
    }

    @Test
    fun `plaintext sqlite files are told apart from encrypted ones`() {
        val plain = File.createTempFile("plain", ".db").apply { writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100)) }
        val encrypted = File.createTempFile("enc", ".db").apply { writeBytes(ByteArray(116) { (it * 37).toByte() }) }
        assertTrue(EncryptedDatabase.isPlaintext(plain))
        assertFalse(EncryptedDatabase.isPlaintext(encrypted))
        assertFalse(EncryptedDatabase.isPlaintext(File("/nonexistent/x.db")))
        plain.delete()
        encrypted.delete()
    }
}
