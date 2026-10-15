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
import com.example.contentstudio.storage.*
import com.example.contentstudio.archive.ZipArchive
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded media item at a time; single-tool cache sweepers never enter this folder. */
class BatchEngine(private val context: Context, val kind: BatchKind = BatchKind.IMAGE,
    private val capacity: (File) -> StreamingStorage = ::deviceStorage) {
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
            (it.name.startsWith("source-") || it.name.startsWith("output-") ||
                (kind == BatchKind.DOCUMENT && (it.name.startsWith("result-") || it.name.startsWith("office-")))) }?.forEach { it.delete() }
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
                val imported = converters.import(uri, old.options)
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
            items = old.items.map { it.copy(output = null, outputBytes = 0, issue = null, videoIssue = null, audioIssue = null, documentIssue = null) })
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
    suspend fun document(id: String, sheet: Int, mode: com.example.contentstudio.documents.OfficeCellMode): BatchQueue = writer.withLock {
        require(kind == BatchKind.DOCUMENT)
        val old = load(); val item = old.items.single { it.id == id }; val meta = checkNotNull(item.document)
        require(meta.office == com.example.contentstudio.documents.OfficeFormat.XLSX && sheet in meta.sheets.indices)
        val nextMeta = meta.copy(sheet = sheet, mode = mode)
        if (meta == nextMeta) return@withLock old
        val next = old.revised(items = old.items.map { if (it.id == id) it.copy(document = nextMeta,
            output = null, outputBytes = 0, issue = null, documentIssue = null) else it })
        withContext(NonCancellable) {
            disk.write(BatchPolicy.encode(next))
            item.output?.let { runCatching { output(item).delete() } }
        }
        next
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
            assets.listFiles()?.filter { it.isFile && (it.name.startsWith("source-") || it.name.startsWith("output-") ||
                (kind == BatchKind.DOCUMENT && (it.name.startsWith("result-") || it.name.startsWith("office-")))) }
                ?.forEach { it.delete() }
            folder.listFiles()?.filter { it.isFile && it.name.startsWith("output-") }?.forEach { it.delete() }
        }
        BatchQueue(options = BatchOptions(kind = kind))
    }
    suspend fun convert(revision: String, progress: (Int?) -> Unit = {},
        documentApproved: Set<String> = emptySet(),
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
                    result = converters.convert(item, batch.options, documentApproved) { percent ->
                        progress(percent?.let { (index * 100 + it.coerceIn(0, 100)) / count })
                    }
                    if (batch.items.sumOf { it.outputBytes } + result.length() > BatchPolicy.MAX_BYTES) {
                        if (kind == BatchKind.DOCUMENT) throw BatchDocumentFailure(BatchDocumentIssue.OUTPUT_LIMIT)
                        throw MediaImportException(if (kind == BatchKind.IMAGE) ImportIssue.IMAGE_LIMIT else ImportIssue.VIDEO_LIMIT)
                    }
                    currentCoroutineContext().ensureActive()
                    val updated = item.copy(output = result.name, outputBytes = result.length(), issue = null, videoIssue = null, audioIssue = null, documentIssue = null)
                    val next = batch.copy(items = batch.items.toMutableList().also { it[index] = updated })
                    withContext(NonCancellable) { disk.write(BatchPolicy.encode(next)); batch = next; committed = true }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    val updated = item.copy(issue = importIssue(failure) ?: ImportIssue.INVALID_MEDIA,
                        videoIssue = (failure as? BatchOutputFailure)?.video, audioIssue = (failure as? BatchOutputFailure)?.audio,
                        documentIssue = (failure as? BatchDocumentFailure)?.issue)
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
    suspend fun zip(revision: String, approved: Set<String>, documentApproved: Set<String> = emptySet(), progress: (Int) -> Unit = {}): ProcessingOutput = writer.withLock {
        val batch = load(); require(batch.revision == revision)
        val selected = BatchPolicy.requireExportable(batch, approved, documentApproved)
        val storage = capacity(folder)
        storage.requireRoom(StreamingLimits.zipWorkingBytes(selected.sumOf { it.outputBytes }, selected.size))
        val ready = File(folder, "output-${UUID.randomUUID()}.zip")
        val partial = File(folder, ready.name + ".part")
        var committed = false
        try {
            FileOutputStream(partial).use { file ->
                val zip = ZipOutputStream(java.io.BufferedOutputStream(storage.guard(file), 65536))
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
            // Our own package must satisfy the reader's structural and content-size contract.
            val job = currentCoroutineContext()
            val entries = ZipArchive.index(partial) { job.ensureActive() }
            require(entries.size == selected.size && entries.sumOf { it.bytes } == selected.sumOf { it.outputBytes })
            withContext(NonCancellable) { check(partial.renameTo(ready)); committed = true }
            ProcessingOutput(ready.name, ready.length())
        } finally { partial.delete(); if (!committed) ready.delete() }
    }
    suspend fun export(result: ProcessingOutput, revision: String, approved: Set<String>, uri: Uri, documentApproved: Set<String> = emptySet()) = writer.withLock {
        val batch = load(); require(batch.revision == revision)
        BatchPolicy.requireExportable(batch, approved, documentApproved)
        require(batch.items.none { Uri.parse(it.uri) == uri }) { "Cannot overwrite original" }
        require(result.name.endsWith(".zip"))
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                val guarded = capacity(folder).guard(output)
                result.resolve(folder).inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        guarded.write(buffer, 0, count)
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
