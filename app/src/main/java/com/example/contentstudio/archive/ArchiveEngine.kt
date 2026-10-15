package com.example.contentstudio.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.example.contentstudio.media.AtomicMediaSession
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ArchiveSession(val revision: String, val file: String, val name: String,
    val source: String, val bytes: Long, val entries: List<ArchiveEntry>)

/** One immutable input, one atomic metadata writer; no archive paths on the filesystem. */
class ArchiveEngine(private val context: Context) {
    val folder = File(context.noBackupFilesDir, "zip-reader")
    private val storage = AtomicMediaSession(folder, 16384)
    companion object { private val mutex = Mutex() }

    private fun owned(name: String): File {
        require(name.matches(Regex("archive-[a-f0-9-]{36}\\.zip")))
        return File(folder, name).also { require(it.canonicalFile.parentFile == folder.canonicalFile) }
    }
    private fun metadata(): JSONObject? = storage.read()?.let { JSONObject(it).also { j -> require(j.getInt("version") == 1) } }
    private fun resolve(j: JSONObject): File = owned(j.getString("file")).also {
        require(it.isFile && it.length() == j.getLong("bytes") && it.length() <= ZipArchive.MAX_SOURCE)
    }
    private fun session(j: JSONObject, entries: List<ArchiveEntry>) = ArchiveSession(
        j.getString("revision"), j.getString("file"), j.getString("name"), j.getString("source"), j.getLong("bytes"), entries)

    suspend fun read(): ArchiveSession? = withContext(Dispatchers.IO) { mutex.withLock {
        val j = metadata()
        if (j == null) { prune(null); return@withLock null }
        val job = currentCoroutineContext()
        session(j, ZipArchive.inspect(resolve(j)) { job.ensureActive() }).also { prune(it.file) }
    } }

    suspend fun open(uri: Uri): ArchiveSession = withContext(Dispatchers.IO) { mutex.withLock {
        check(folder.isDirectory || folder.mkdirs())
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }?.take(512) ?: "archive.zip"
        val file = owned("archive-${UUID.randomUUID()}.zip")
        var committed = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        if (total > ZipArchive.MAX_SOURCE) throw ArchiveRejected(ArchiveIssue.LIMIT)
                        out.write(buffer, 0, count)
                    }
                    out.fd.sync()
                }
            } ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
            val job = currentCoroutineContext()
            val entries = ZipArchive.inspect(file) { job.ensureActive() }
            val old = metadata()?.getString("file")
            val j = JSONObject().put("version", 1).put("revision", UUID.randomUUID().toString())
                .put("file", file.name).put("name", name).put("source", uri.toString()).put("bytes", file.length())
            currentCoroutineContext().ensureActive()
            // Commit and retire together even if the UI closes during AtomicFile.finishWrite.
            withContext(NonCancellable) {
                storage.write(j.toString()); committed = true
                old?.let { runCatching { owned(it).delete() } }
                prune(file.name)
            }
            session(j, entries)
        } finally { if (!committed) file.delete() }
    } }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        withContext(NonCancellable) { storage.write(null); prune(null) }
    } }

    private fun prune(keep: String?) {
        folder.listFiles()?.filter { it.isFile && (it.name.matches(Regex("archive-[a-f0-9-]{36}\\.zip")) ||
            it.name.matches(Regex("extract-[a-f0-9-]{36}\\.part"))) && it.name != keep }?.forEach { it.delete() }
    }

    /** A CreateDocument destination only. Stage and CRC-check before touching its new document. */
    suspend fun save(revision: String, path: String, destination: Uri) = withContext(Dispatchers.IO) { mutex.withLock {
        val j = metadata() ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
        val source = j.getString("source").toUri()
        require(!sameDocument(source, destination)) // Never delete or open an original on failure either.
        // A stale callback cannot establish that its URI is a new document. Do not delete it.
        require(j.getString("revision") == revision)
        val staged = File(folder, "extract-${UUID.randomUUID()}.part")
        var saved = false
        try {
            val archive = resolve(j); val job = currentCoroutineContext()
            val entries = ZipArchive.index(archive) { job.ensureActive() }
            val entry = entries.single { it.path == path && !it.directory }
            FileOutputStream(staged).use { out ->
                ZipArchive.extract(archive, entry, out) { job.ensureActive() }; out.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                staged.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                }
                out.flush()
            } ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
            saved = true
        } finally {
            staged.delete()
            if (!saved) withContext(NonCancellable) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            }
        }
    } }

    private fun sameDocument(a: Uri, b: Uri): Boolean = a == b ||
        (a.authority == b.authority && runCatching {
            DocumentsContract.getDocumentId(a) == DocumentsContract.getDocumentId(b)
        }.getOrDefault(false))
}
