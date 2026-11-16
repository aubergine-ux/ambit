package io.aubergine.ambit.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupCodecTest {

    private val payload = "observations".repeat(1000).toByteArray()

    private fun sealed(password: String = "correct horse"): ByteArray =
        ByteArrayOutputStream().also { BackupCodec.encrypt(payload, password.toCharArray(), it, iterations = 10_000) }.toByteArray()

    @Test
    fun `a backup opens with its password`() {
        assertArrayEquals(payload, BackupCodec.decrypt(sealed().inputStream(), "correct horse".toCharArray()))
    }

    @Test(expected = BackupCodec.WrongPasswordOrDamaged::class)
    fun `the wrong password is refused`() {
        BackupCodec.decrypt(sealed().inputStream(), "battery staple".toCharArray())
    }

    @Test(expected = BackupCodec.WrongPasswordOrDamaged::class)
    fun `a single flipped byte in the data is detected`() {
        val bytes = sealed()
        bytes[bytes.size - 40] = (bytes[bytes.size - 40].toInt() xor 1).toByte()
        BackupCodec.decrypt(bytes.inputStream(), "correct horse".toCharArray())
    }

    @Test(expected = BackupCodec.WrongPasswordOrDamaged::class)
    fun `a tampered header is detected too`() {
        val bytes = sealed()
        // Last byte of the salt, which is covered by the tag as associated data.
        bytes[8 + 4 + 15] = (bytes[8 + 4 + 15].toInt() xor 1).toByte()
        BackupCodec.decrypt(bytes.inputStream(), "correct horse".toCharArray())
    }

    @Test(expected = BackupCodec.NotABackup::class)
    fun `other files are recognised as not a backup`() {
        BackupCodec.decrypt("PK\u0003\u0004 just a zip file, not ours at all".byteInputStream(), "x".toCharArray())
    }

    @Test
    fun `each backup uses a fresh salt and nonce`() {
        val a = sealed()
        val b = sealed()
        assertEquals(a.size, b.size)
        assert(!a.contentEquals(b))
    }

    @Test
    fun `create statements are moved into the attached schema`() {
        assertEquals(
            "CREATE TABLE plain.`observations` (`id` INTEGER)",
            BackupManager.qualify("CREATE TABLE `observations` (`id` INTEGER)", "TABLE"),
        )
        assertEquals(
            "CREATE INDEX plain.`index_observations_address` ON `observations` (`address`)",
            BackupManager.qualify("CREATE INDEX `index_observations_address` ON `observations` (`address`)", "INDEX"),
        )
        assertEquals(
            "CREATE TABLE IF NOT EXISTS plain.`room_master_table` (id INTEGER)",
            BackupManager.qualify("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER)", "TABLE"),
        )
    }
}
