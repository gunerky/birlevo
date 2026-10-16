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
import org.json.JSONArray
import com.example.contentstudio.storage.*

data class ArchiveSession(val revision: String, val file: String, val name: String,
    val source: String, val bytes: Long, val entries: List<ArchiveEntry>)

/** One immutable input, one atomic metadata writer; no archive paths on the filesystem. */
class ArchiveEngine(private val context: Context,
    private val capacity: (File) -> StreamingStorage = ::deviceStorage) {
    val folder = File(context.noBackupFilesDir, "zip-reader")
    private val storage = AtomicMediaSession(folder, 16384)
    private val selection = AtomicMediaSession(File(folder, "selection"), 4 * 1024 * 1024)
    private val extraction = AtomicMediaSession(File(folder, "extraction"), 16384)
    companion object { private val mutex = Mutex() }
    fun inputLimit(): Long = capacity(context.noBackupFilesDir).limit(ZipArchive.MAX_SOURCE)
    private fun space() = capacity(context.noBackupFilesDir)

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
                    val guarded = space().guard(out)
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        if (total > ZipArchive.MAX_SOURCE) throw ArchiveRejected(ArchiveIssue.LIMIT)
                        guarded.write(buffer, 0, count)
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
                selection.write(null)
                extraction.write(null)
                old?.let { runCatching { owned(it).delete() } }
                prune(file.name)
            }
            session(j, entries)
        } finally { if (!committed) file.delete() }
    } }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        withContext(NonCancellable) { storage.write(null); selection.write(null); extraction.write(null); prune(null) }
    } }

    private fun prune(keep: String?) {
        folder.listFiles()?.filter { it.isFile && (it.name.matches(Regex("archive-[a-f0-9-]{36}\\.zip")) ||
            it.name.matches(Regex("extract-[a-f0-9-]{36}\\.part"))) && it.name != keep }?.forEach { it.delete() }
    }

    /** Informational recovery only: never resume writing/deleting a previous destination implicitly. */
    suspend fun lastExtraction(): Triple<Uri, Int, Boolean>? = withContext(Dispatchers.IO) { mutex.withLock {
        val j = metadata() ?: return@withLock null
        extraction.read()?.let(::JSONObject)?.takeIf { it.getString("revision") == j.getString("revision") }?.let {
            Triple(it.getString("folder").toUri(), it.optInt("count"), it.optBoolean("complete"))
        }
    } }

    /** Private, bounded picker request. Only the token enters saved Android instance state. */
    suspend fun prepareSelection(revision: String, paths: Set<String>): String = withContext(Dispatchers.IO) { mutex.withLock {
        val j = metadata() ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
        require(j.getString("revision") == revision && paths.isNotEmpty() && paths.size <= ZipArchive.MAX_ENTRIES)
        val job = currentCoroutineContext()
        val entries = ZipArchive.index(resolve(j)) { job.ensureActive() }.filterNot { it.directory }.map { it.path }.toSet()
        require(entries.containsAll(paths))
        val token = UUID.randomUUID().toString()
        selection.write(JSONObject().put("token", token).put("revision", revision).put("paths", JSONArray(paths.sorted())).toString())
        token
    } }

    suspend fun extractSelection(token: String, tree: Uri, progress: (Int, Int) -> Unit = { _, _ -> },
        remaining: (Uri) -> Unit = {}): Pair<Uri, Int> = withContext(Dispatchers.IO) { mutex.withLock {
        val j = metadata() ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
        val plan = selection.read()?.let(::JSONObject) ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
        require(plan.getString("token") == token && plan.getString("revision") == j.getString("revision"))
        val job = currentCoroutineContext()
        val archive = resolve(j)
        val requested = plan.getJSONArray("paths").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val entries = ZipArchive.index(archive) { job.ensureActive() }.filter { !it.directory && it.path in requested }
        require(entries.size == requested.size && entries.isNotEmpty())
        space().requireRoom(entries.sumOf { it.bytes })
        val stages = mutableListOf<Pair<ArchiveEntry, File>>()
        val targets = ArchiveFolders(context.contentResolver, tree, j.getString("source").toUri())
        var success = false
        try {
            // Verify every selected entry before creating anything in the user's folder.
            progress(0, entries.size)
            ZipArchive.extractMany(archive, entries, { job.ensureActive() }) { entry, copy ->
                job.ensureActive()
                val stage = File(folder, "extract-${UUID.randomUUID()}.part")
                stages.add(entry to stage)
                try { FileOutputStream(stage).use { copy(space().guard(it)); it.fd.sync() } }
                catch (failure: StorageCapacityException) { throw failure }
                catch (failure: java.io.IOException) { throw ArchiveRejected(ArchiveIssue.ACCESS).apply { initCause(failure) } }
            }
            job.ensureActive()
            val root = targets.start()
            withContext(NonCancellable) {
                extraction.write(JSONObject().put("revision", j.getString("revision")).put("folder", root.toString()).toString())
            }
            for ((index, pair) in stages.withIndex()) {
                job.ensureActive()
                val destination = targets.file(pair.first.path)
                job.ensureActive()
                context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                    val guarded = space().guard(out)
                    pair.second.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            // The provider may be on this volume, removable storage, or remote.
                            // Only local workspace is measurable; provider failures remain authoritative.
                            guarded.write(buffer, 0, count)
                        }
                    }
                    out.flush()
                } ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
                progress(index + 1, entries.size)
            }
            job.ensureActive()
            withContext(NonCancellable) {
                extraction.write(JSONObject().put("revision", j.getString("revision")).put("folder", root.toString())
                    .put("count", entries.size).put("complete", true).toString())
            }
            success = true
            root to entries.size
        } finally {
            withContext(NonCancellable) {
                stages.forEach { it.second.delete() }
                if (!success) {
                    val partial = targets.cleanup()
                    // The UI must still learn the remaining folder if local receipt storage also fails.
                    partial?.let(remaining)
                    // Preserve an honest interrupted-work notice across process recreation.
                    extraction.write(partial?.let { JSONObject().put("revision", j.getString("revision")).put("folder", it.toString()).toString() })
                }
                selection.write(null)
            }
        }
    } }

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
            space().requireRoom(entry.bytes)
            FileOutputStream(staged).use { out ->
                ZipArchive.extract(archive, entry, space().guard(out)) { job.ensureActive() }; out.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                val guarded = space().guard(out)
                staged.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        guarded.write(buffer, 0, count)
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
