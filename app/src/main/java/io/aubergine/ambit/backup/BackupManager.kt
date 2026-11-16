package io.aubergine.ambit.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import io.aubergine.ambit.BuildConfig
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.DatabaseKey
import io.aubergine.ambit.data.db.EncryptedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whole-database backup to a password-encrypted file the user keeps wherever they like.
 *
 * The database on the phone is keyed to this phone's Keystore, so a raw copy would be
 * useless anywhere else. The backup is a plaintext SQLite copy made inside the running
 * database, zipped with a small manifest and encrypted with the user's password.
 * App settings (including the lock PIN) are deliberately not included.
 */
@Singleton
class BackupManager @Inject constructor(
    private val context: Context,
    private val db: AppDatabase,
) {

    @Serializable
    data class Manifest(val format: Int, val schema: Int, val app: String, val created: Long)

    class TooNew : Exception("backup is from a newer version of the app")

    suspend fun export(password: CharArray, target: Uri) = withContext(Dispatchers.IO) {
        val plain = File(context.cacheDir, "backup-plain.db").apply { delete() }
        try {
            val schema = copyToPlaintext(db.openHelper.writableDatabase, plain)
            val zipped = ByteArrayOutputStream()
            ZipOutputStream(zipped).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json.encodeToString(Manifest.serializer(), Manifest(FORMAT, schema, BuildConfig.VERSION_NAME, System.currentTimeMillis())).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATABASE))
                plain.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                BackupCodec.encrypt(zipped.toByteArray(), password, out)
            } ?: error("cannot write to the chosen file")
        } finally {
            plain.delete()
            password.fill(' ')
        }
    }

    /**
     * Replaces the database with the backup's contents. The app must restart afterwards;
     * the caller does that.
     */
    suspend fun restore(password: CharArray, source: Uri) = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(source)?.use { BackupCodec.decrypt(it, password) }
                ?: error("cannot read the chosen file")
        } finally {
            password.fill(' ')
        }
        val plain = File(context.cacheDir, "restore-plain.db").apply { delete() }
        var manifest: Manifest? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    MANIFEST -> manifest = json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                    DATABASE -> plain.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        val m = manifest ?: throw BackupCodec.NotABackup()
        if (m.format > FORMAT || m.schema > CURRENT_SCHEMA) throw TooNew()
        if (!EncryptedDatabase.isPlaintext(plain)) throw BackupCodec.NotABackup()

        val live = context.getDatabasePath(AppDatabase.NAME)
        db.close()
        listOf("", "-wal", "-shm", "-journal").forEach { File(live.path + it).delete() }
        if (DatabaseKey(context).exists()) {
            EncryptedDatabase.importPlaintext(plain, live, EncryptedDatabase.rawKeyLiteral(DatabaseKey(context).passphrase()))
        } else {
            plain.copyTo(live, overwrite = true)
        }
        plain.delete()
    }

    /** Copies every table into a fresh plaintext file. Returns the schema version. */
    private fun copyToPlaintext(source: SupportSQLiteDatabase, target: File): Int {
        source.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val version = source.version
        // Under SQLCipher an attached database inherits the main key unless told otherwise.
        val cipher = source.javaClass.name.startsWith("net.zetetic")
        val keyClause = if (cipher) " KEY ''" else ""
        source.execSQL("ATTACH DATABASE '${target.path.replace("'", "''")}' AS plain$keyClause")
        try {
            val schema = source.query(
                "SELECT type, name, sql FROM main.sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type DESC",
            ).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) } }
            schema.filter { it.first == "table" }.forEach { (_, name, sql) ->
                source.execSQL(qualify(sql, "TABLE"))
                source.execSQL("INSERT INTO plain.`$name` SELECT * FROM main.`$name`")
            }
            schema.filter { it.first == "index" }.forEach { (_, _, sql) -> source.execSQL(qualify(sql, "INDEX")) }
            source.execSQL("PRAGMA plain.user_version = $version")
        } finally {
            source.execSQL("DETACH DATABASE plain")
        }
        return version
    }

    companion object {
        private const val FORMAT = 1
        private const val MANIFEST = "manifest.json"
        private const val DATABASE = "ambit.db"
        private val CURRENT_SCHEMA = AppDatabase.SCHEMA_VERSION
        private val json = Json { ignoreUnknownKeys = true }

        /** Rewrites `CREATE TABLE x` / `CREATE INDEX x` to create the object in the `plain` schema. */
        fun qualify(sql: String, kind: String): String {
            val text = sql.trim()
            val match = Regex("^(CREATE\\s+(?:UNIQUE\\s+)?$kind\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)([`\"]?)([^`\"\\s(]+)\\2", RegexOption.IGNORE_CASE)
                .find(text) ?: return text
            return text.replaceRange(match.range, "${match.groupValues[1]}plain.`${match.groupValues[3]}`")
        }
    }
}
