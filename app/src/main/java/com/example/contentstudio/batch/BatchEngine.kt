package com.example.contentstudio.batch

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.contentstudio.media.*
import com.example.contentstudio.processing.ProcessingKind
import com.example.contentstudio.processing.ProcessingOutput
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded media item at a time; single-tool cache sweepers never enter this folder. */
class BatchEngine(private val context: Context, val kind: BatchKind = BatchKind.IMAGE) {
    val folder = File(context.noBackupFilesDir, kind.processing.folder)
    private val assets = File(folder, "assets").apply { check(isDirectory || mkdirs()) }
    private val disk = AtomicMediaSession(folder)
    private val converters = BatchConverters(context, assets, kind)
    private val writer get() = writers.getValue(kind)
    private fun source(item: BatchItem) = BatchPolicy.owned(assets, item.source, "source-")
        .also { if (!it.isFile || it.length() != item.bytes) throw MediaImportException(ImportIssue.FILE_ACCESS) }
    private fun output(item: BatchItem) = BatchPolicy.owned(assets, checkNotNull(item.output), "output-")
        .also { require(it.isFile && it.length() == item.outputBytes) }
    private fun load(): BatchQueue = (disk.read()?.let(BatchPolicy::decode) ?: BatchQueue(options = BatchOptions(kind = kind)))
        .also { require(it.options.kind == kind) }
    suspend fun read(): BatchQueue = writer.withLock {
        val saved = load()
        val checked = saved.items.map { item ->
            if (runCatching { source(item) }.isFailure || (item.output != null && runCatching { output(item) }.isFailure))
                item.copy(output = null, outputBytes = 0, issue = ImportIssue.FILE_ACCESS) else item
        }
        val next = if (checked == saved.items) saved else saved.revised(items = checked).also { disk.write(BatchPolicy.encode(it)) }
        val retained = next.items.flatMap { listOfNotNull(it.source, it.output) }.toSet()
        // These files belong exclusively to this queue; no project or other tool refers to them.
        assets.listFiles()?.filter { it.isFile && it.name !in retained &&
            (it.name.startsWith("source-") || it.name.startsWith("output-")) }?.forEach { it.delete() }
        next
    }
    suspend fun pruneOutputs(keep: String?) = writer.withLock {
        folder.listFiles()?.filter { it.isFile && it.name.startsWith("output-") && it.name != keep }?.forEach { it.delete() }
    }
    suspend fun add(uris: List<Uri>): BatchQueue = writer.withLock {
        val old = load()
        require(uris.isNotEmpty() && old.items.size + uris.size <= BatchPolicy.MAX_FILES)
        val added = mutableListOf<BatchItem>()
        var committed = false
        try {
            var bytes = old.items.sumOf { it.bytes }
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val imported = converters.import(uri)
                try {
                    bytes += imported.bytes
                    require(bytes <= BatchPolicy.MAX_BYTES)
                    added += imported
                } finally {
                    if (added.none { it.source == imported.source }) BatchPolicy.owned(assets, imported.source, "source-").delete()
                }
            }
            currentCoroutineContext().ensureActive()
            val next = old.revised(items = old.items + added)
            // Validate untrusted display names/URI lengths before creating a durable reference.
            BatchPolicy.decode(BatchPolicy.encode(next))
            withContext(NonCancellable) { disk.write(BatchPolicy.encode(next)); committed = true }
            next
        } finally { if (!committed) added.forEach { source(it).delete() } }
    }
    suspend fun options(options: BatchOptions): BatchQueue = writer.withLock {
        require(options.kind == kind)
        val old = load()
        if (old.options == options) return@withLock old
        val next = old.revised(options = options,
            items = old.items.map { it.copy(output = null, outputBytes = 0, issue = null, videoIssue = null, audioIssue = null) })
        withContext(NonCancellable) {
            disk.write(BatchPolicy.encode(next))
            old.items.filter { it.output != null }.forEach { runCatching { output(it).delete() } }
        }
        next
    }
    suspend fun include(id: String, include: Boolean): BatchQueue = writer.withLock {
        load().let { old -> old.revised(items = old.items.map { if (it.id == id) it.copy(included = include) else it }) }
            .also { withContext(NonCancellable) { disk.write(BatchPolicy.encode(it)) } }
    }
    suspend fun remove(id: String): BatchQueue = writer.withLock {
        val old = load(); val removed = old.items.filter { it.id == id }
        val next = old.revised(items = old.items.filterNot { it.id == id })
        withContext(NonCancellable) {
            disk.write(BatchPolicy.encode(next))
            removed.forEach { BatchPolicy.owned(assets, it.source, "source-").delete(); if (it.output != null) runCatching { output(it).delete() } }
        }
        next
    }
    suspend fun clear(): BatchQueue = writer.withLock {
        // Commit the tombstone first. Never touch project assets, other tools or original URIs.
        withContext(NonCancellable) {
            disk.write(null)
            assets.listFiles()?.filter { it.isFile && (it.name.startsWith("source-") || it.name.startsWith("output-")) }
                ?.forEach { it.delete() }
            folder.listFiles()?.filter { it.isFile && it.name.startsWith("output-") }?.forEach { it.delete() }
        }
        BatchQueue(options = BatchOptions(kind = kind))
    }
    suspend fun convert(revision: String, progress: (Int?) -> Unit = {},
        changed: suspend (BatchQueue, Int) -> Unit): ProcessingOutput = writer.withLock {
        var batch = load(); require(batch.revision == revision && batch.items.isNotEmpty())
        val count = batch.items.size
        batch.items.indices.forEach { index ->
            currentCoroutineContext().ensureActive()
            val item = batch.items[index]
            if (item.output == null) {
                var result: File? = null
                var committed = false
                try {
                    source(item)
                    result = converters.convert(item, batch.options) { percent ->
                        progress(percent?.let { (index * 100 + it.coerceIn(0, 100)) / count })
                    }
                    if (batch.items.sumOf { it.outputBytes } + result.length() > BatchPolicy.MAX_BYTES)
                        throw MediaImportException(if (kind == BatchKind.IMAGE) ImportIssue.IMAGE_LIMIT else ImportIssue.VIDEO_LIMIT)
                    currentCoroutineContext().ensureActive()
                    val updated = item.copy(output = result.name, outputBytes = result.length(), issue = null, videoIssue = null, audioIssue = null)
                    val next = batch.copy(items = batch.items.toMutableList().also { it[index] = updated })
                    withContext(NonCancellable) { disk.write(BatchPolicy.encode(next)); batch = next; committed = true }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    val updated = item.copy(issue = importIssue(failure) ?: ImportIssue.INVALID_MEDIA,
                        videoIssue = (failure as? BatchOutputFailure)?.video, audioIssue = (failure as? BatchOutputFailure)?.audio)
                    batch = batch.copy(items = batch.items.toMutableList().also { it[index] = updated })
                    withContext(NonCancellable) { disk.write(BatchPolicy.encode(batch)) }
                } finally {
                    if (!committed) result?.delete()
                }
            }
            changed(batch, (index + 1) * 100 / count)
        }
        // ProcessingJobs owns only this receipt; cancelled jobs cannot delete the checkpoint or completed images.
        val receipt = File(folder, "output-${UUID.randomUUID()}.json")
        try {
            FileOutputStream(receipt).use { it.write(BatchPolicy.encode(batch).toByteArray()); it.fd.sync() }
            ProcessingOutput(receipt.name, receipt.length())
        } catch (failure: Throwable) { receipt.delete(); throw failure }
    }
    suspend fun zip(revision: String, approved: Set<String>, progress: (Int) -> Unit = {}): ProcessingOutput = writer.withLock {
        val batch = load(); require(batch.revision == revision)
        val selected = BatchPolicy.requireExportable(batch, approved)
        val ready = File(folder, "output-${UUID.randomUUID()}.zip")
        val partial = File(folder, ready.name + ".part")
        var committed = false
        try {
            FileOutputStream(partial).use { file ->
                val zip = ZipOutputStream(file)
                try {
                // Media is already encoded; packaging does not promise further compression.
                zip.setLevel(0)
                val buffer = ByteArray(64 * 1024)
                val total = selected.sumOf { it.outputBytes }
                var copied = 0L
                var reported = -1
                selected.forEachIndexed { index, item ->
                    source(item)
                    zip.putNextEntry(ZipEntry(BatchPolicy.entryName(index, item.name, batch.options.extension)))
                    output(item).inputStream().use { input ->
                        var bytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            bytes += count; require(bytes <= item.outputBytes)
                            zip.write(buffer, 0, count)
                            copied += count
                            val percent = (copied * 100 / total).toInt()
                            if (reported != percent) { progress(percent); reported = percent }
                        }
                        require(bytes == item.outputBytes)
                    }
                    zip.closeEntry()
                }
                zip.finish(); zip.flush(); file.fd.sync()
                } finally { zip.close() }
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { check(partial.renameTo(ready)); committed = true }
            ProcessingOutput(ready.name, ready.length())
        } finally { partial.delete(); if (!committed) ready.delete() }
    }
    suspend fun export(result: ProcessingOutput, revision: String, approved: Set<String>, uri: Uri) = writer.withLock {
        val batch = load(); require(batch.revision == revision)
        BatchPolicy.requireExportable(batch, approved)
        require(batch.items.none { Uri.parse(it.uri) == uri }) { "Cannot overwrite original" }
        require(result.name.endsWith(".zip"))
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                result.resolve(folder).inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.flush(); currentCoroutineContext().ensureActive()
            } ?: error("Destination unavailable")
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }; throw failure
        }
    }
    companion object { private val writers = BatchKind.entries.associateWith { Mutex() } }
}
