package io.aubergine.ambit.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Opens the Room database through SQLCipher, converting an existing plaintext database the
 * first time.
 *
 * The conversion runs when Room first asks for the database (always off the main thread),
 * not when the factory is built. It writes an encrypted copy beside the original, checks the
 * row counts match, and only then swaps the files. If anything goes wrong the plaintext
 * database stays in place and [EncryptionStatus] says so, because losing months of
 * observations to a failed migration is worse than leaving them unencrypted.
 */
object EncryptedDatabase {

    @Volatile var status: EncryptionStatus = EncryptionStatus.UNKNOWN
        private set

    fun factory(context: Context, key: DatabaseKey): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        val rawKey = rawKeyLiteral(key.passphrase())
        val delegate = SupportOpenHelperFactory(rawKey.toByteArray(Charsets.US_ASCII))
        return SupportSQLiteOpenHelper.Factory { config ->
            val inner = delegate.create(config)
            object : SupportSQLiteOpenHelper by inner {
                @Volatile private var prepared = false

                private fun prepare() {
                    if (prepared) return
                    synchronized(this) {
                        if (prepared) return
                        config.name?.let { encryptInPlace(context.getDatabasePath(it), rawKey) }
                        prepared = true
                    }
                }

                override val writableDatabase: SupportSQLiteDatabase
                    get() {
                        prepare()
                        return inner.writableDatabase
                    }

                override val readableDatabase: SupportSQLiteDatabase
                    get() {
                        prepare()
                        return inner.readableDatabase
                    }
            }
        }
    }

    /** Writes a plaintext database into a new encrypted file at [target]. Used by restore. */
    fun importPlaintext(plain: File, target: File, rawKey: String) {
        System.loadLibrary("sqlcipher")
        SQLiteDatabase.openDatabase(plain.path, "", null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
            val version = db.version
            db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(target.path, rawKey))
            db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            db.execSQL("PRAGMA encrypted.user_version = $version")
            db.execSQL("DETACH DATABASE encrypted")
        }
    }

    /** SQLite's file header. An encrypted file starts with random bytes instead. */
    fun isPlaintext(file: File): Boolean =
        file.exists() && file.length() >= 16 &&
            file.inputStream().use { input -> String(ByteArray(16).also { input.read(it) }, Charsets.US_ASCII) } == "SQLite format 3\u0000"

    private fun encryptInPlace(file: File, rawKey: String) {
        if (!isPlaintext(file)) {
            status = EncryptionStatus.ENCRYPTED
            return
        }
        val target = File(file.path + ".encrypting")
        runCatching {
            target.delete()
            val plain = SQLiteDatabase.openDatabase(file.path, "", null, SQLiteDatabase.OPEN_READWRITE, null, null)
            plain.use { db ->
                // Fold the WAL into the main file so nothing is left behind in it.
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                val version = db.version
                db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(target.path, rawKey))
                db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA encrypted.user_version = $version")
                check(countsMatch(db)) { "row counts differ after export" }
                db.execSQL("DETACH DATABASE encrypted")
            }
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            check(target.renameTo(file)) { "could not move the encrypted database into place" }
            status = EncryptionStatus.ENCRYPTED
        }.onFailure {
            target.delete()
            status = EncryptionStatus.FAILED
        }
    }

    private fun countsMatch(db: SQLiteDatabase): Boolean {
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.all { t ->
            val a = db.rawQuery("SELECT COUNT(*) FROM main.`$t`", null).use { it.moveToFirst(); it.getLong(0) }
            val b = db.rawQuery("SELECT COUNT(*) FROM encrypted.`$t`", null).use { it.moveToFirst(); it.getLong(0) }
            a == b
        }
    }

    /**
     * SQLCipher treats a key written as x'hex' as raw key bytes rather than a passphrase to
     * stretch. The same literal has to be used everywhere the database is keyed.
     */
    fun rawKeyLiteral(bytes: ByteArray): String = "x'" + bytes.joinToString("") { "%02x".format(it) } + "'"
}

enum class EncryptionStatus { UNKNOWN, ENCRYPTED, FAILED }
