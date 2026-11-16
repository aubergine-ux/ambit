package io.aubergine.ambit.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes export payloads to app-private storage and hands out share URIs.
 *
 * Files land in `cacheDir/exports`, which is private to the app until the user chooses to
 * share one. Nothing is uploaded; the share sheet is the only way anything leaves, and the
 * user drives it.
 */
@Singleton
class ExportManager @Inject constructor(
    private val context: Context,
) {

    data class Artifact(val file: File, val mimeType: String, val bytes: Long)

    suspend fun write(
        content: String,
        kind: Kind,
        at: Long = System.currentTimeMillis(),
    ): Artifact = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        prune(dir)
        val file = File(dir, "ambit_${kind.slug}_${stamp(at)}.${kind.extension}")
        file.writeText(content)
        Artifact(file, kind.mimeType, file.length())
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

    fun shareIntent(artifact: Artifact, subject: String): Intent {
        val uri = uriFor(artifact.file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = artifact.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            subject,
        )
    }

    /** Keeps the cache from growing without bound across many sessions. */
    private fun prune(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_RETAINED).forEach { runCatching { it.delete() } }
    }

    private fun stamp(at: Long): String = STAMP.format(Date(at))

    enum class Kind(val slug: String, val extension: String, val mimeType: String) {
        DEVICES_CSV("devices", "csv", "text/csv"),
        OBSERVATIONS_CSV("log", "csv", "text/csv"),
        JSON("export", "json", "application/json"),
        DEBRIEF("debrief", "txt", "text/plain"),
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val MAX_RETAINED = 20
        val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
