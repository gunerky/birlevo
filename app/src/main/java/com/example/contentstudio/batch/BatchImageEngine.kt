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

/** One bounded image at a time. This isolated folder is never swept by the single-image cache. */
class BatchImageEngine(private val context: Context) {
    val folder = File(context.noBackupFilesDir, ProcessingKind.BATCH_IMAGE.folder)
    private val assets = File(folder, "assets").apply { check(isDirectory || mkdirs()) }
    private val disk = AtomicMediaSession(folder)
    private val images = ImageCompressor(context, assets)
    private fun source(item: BatchImage) = BatchImagePolicy.owned(assets, item.source, "source-")
        .also { if (!it.isFile || it.length() != item.bytes) throw MediaImportException(ImportIssue.FILE_ACCESS) }
    private fun output(item: BatchImage) = BatchImagePolicy.owned(assets, checkNotNull(item.output), "output-")
        .also { require(it.isFile && it.length() == item.outputBytes) }
    private fun load(): BatchImages = disk.read()?.let(BatchImagePolicy::decode) ?: BatchImages()
    suspend fun read(): BatchImages = writer.withLock {
        val saved = load()
        val checked = saved.items.map { item ->
            if (runCatching { source(item) }.isFailure || (item.output != null && runCatching { output(item) }.isFailure))
                item.copy(output = null, outputBytes = 0, issue = ImportIssue.FILE_ACCESS) else item
        }
        val next = if (checked == saved.items) saved else saved.revised(items = checked).also { disk.write(BatchImagePolicy.encode(it)) }
        val retained = next.items.flatMap { listOfNotNull(it.source, it.output) }.toSet()
        // These files belong exclusively to this queue; no project or other tool refers to them.
        assets.listFiles()?.filter { it.isFile && it.name !in retained &&
            (it.name.startsWith("source-") || it.name.startsWith("output-")) }?.forEach { it.delete() }
        next
    }
    suspend fun pruneOutputs(keep: String?) = writer.withLock {
        folder.listFiles()?.filter { it.isFile && it.name.startsWith("output-") && it.name != keep }?.forEach { it.delete() }
    }
    suspend fun add(uris: List<Uri>): BatchImages = writer.withLock {
        val old = load()
        require(uris.isNotEmpty() && old.items.size + uris.size <= BatchImagePolicy.MAX_FILES)
        val added = mutableListOf<BatchImage>()
        var committed = false
        try {
            var bytes = old.items.sumOf { it.bytes }
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val imported = images.importImage(uri)
                try {
                    bytes += imported.bytes
                    require(bytes <= BatchImagePolicy.MAX_BYTES)
                    added += BatchImage(UUID.randomUUID().toString(), imported.file.name, uri.toString(),
                        imported.name.take(256).ifBlank { "image" }, imported.bytes, imported.width, imported.height)
                } finally {
                    // These previews have never been given to Compose or any drawing layer.
                    imported.preview.recycle()
                    if (added.none { it.source == imported.file.name }) imported.file.delete()
                }
            }
            currentCoroutineContext().ensureActive()
            val next = old.revised(items = old.items + added)
            // Validate untrusted display names/URI lengths before creating a durable reference.
            BatchImagePolicy.decode(BatchImagePolicy.encode(next))
            withContext(NonCancellable) { disk.write(BatchImagePolicy.encode(next)); committed = true }
            next
        } finally { if (!committed) added.forEach { source(it).delete() } }
    }
    suspend fun options(options: BatchOptions): BatchImages = writer.withLock {
        val old = load()
        if (old.options == options) return@withLock old
        val next = old.revised(options = options,
            items = old.items.map { it.copy(output = null, outputBytes = 0, issue = null) })
        withContext(NonCancellable) {
            disk.write(BatchImagePolicy.encode(next))
            old.items.filter { it.output != null }.forEach { runCatching { output(it).delete() } }
        }
        next
    }
    suspend fun include(id: String, include: Boolean): BatchImages = writer.withLock {
        load().let { old -> old.revised(items = old.items.map { if (it.id == id) it.copy(included = include) else it }) }
            .also { withContext(NonCancellable) { disk.write(BatchImagePolicy.encode(it)) } }
    }
    suspend fun remove(id: String): BatchImages = writer.withLock {
        val old = load(); val removed = old.items.filter { it.id == id }
        val next = old.revised(items = old.items.filterNot { it.id == id })
        withContext(NonCancellable) {
            disk.write(BatchImagePolicy.encode(next))
            removed.forEach { BatchImagePolicy.owned(assets, it.source, "source-").delete(); if (it.output != null) runCatching { output(it).delete() } }
        }
        next
    }
    suspend fun clear(): BatchImages = writer.withLock {
        // Commit the tombstone first. Never touch project assets, other tools or original URIs.
        withContext(NonCancellable) {
            disk.write(null)
            assets.listFiles()?.filter { it.isFile && (it.name.startsWith("source-") || it.name.startsWith("output-")) }
                ?.forEach { it.delete() }
            folder.listFiles()?.filter { it.isFile && it.name.startsWith("output-") }?.forEach { it.delete() }
        }
        BatchImages()
    }
    suspend fun convert(revision: String, changed: suspend (BatchImages, Int) -> Unit): ProcessingOutput = writer.withLock {
        var batch = load(); require(batch.revision == revision && batch.items.isNotEmpty())
        val count = batch.items.size
        batch.items.indices.forEach { index ->
            currentCoroutineContext().ensureActive()
            val item = batch.items[index]
            if (item.output == null) {
                var result: CompressedImage? = null
                var committed = false
                try {
                    result = images.compressFile(source(item), batch.options.quality, batch.options.edge, batch.options.format)
                    if (batch.items.sumOf { it.outputBytes } + result.bytes > BatchImagePolicy.MAX_BYTES)
                        throw MediaImportException(ImportIssue.IMAGE_LIMIT)
                    currentCoroutineContext().ensureActive()
                    val updated = item.copy(output = result.file.name, outputBytes = result.bytes, issue = null)
                    val next = batch.copy(items = batch.items.toMutableList().also { it[index] = updated })
                    withContext(NonCancellable) { disk.write(BatchImagePolicy.encode(next)); batch = next; committed = true }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    val updated = item.copy(issue = importIssue(failure))
                    batch = batch.copy(items = batch.items.toMutableList().also { it[index] = updated })
                    withContext(NonCancellable) { disk.write(BatchImagePolicy.encode(batch)) }
                } finally {
                    result?.preview?.recycle() // Never delivered to UI.
                    if (!committed) result?.file?.delete()
                }
            }
            changed(batch, (index + 1) * 100 / count)
        }
        // ProcessingJobs owns only this receipt; cancelled jobs cannot delete the checkpoint or completed images.
        val receipt = File(folder, "output-${UUID.randomUUID()}.json")
        try {
            FileOutputStream(receipt).use { it.write(BatchImagePolicy.encode(batch).toByteArray()); it.fd.sync() }
            ProcessingOutput(receipt.name, receipt.length())
        } catch (failure: Throwable) { receipt.delete(); throw failure }
    }
    suspend fun zip(revision: String, approved: Set<String>, progress: (Int) -> Unit = {}): ProcessingOutput = writer.withLock {
        val batch = load(); require(batch.revision == revision)
        val selected = BatchImagePolicy.requireExportable(batch, approved)
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
                    zip.putNextEntry(ZipEntry(BatchImagePolicy.entryName(index, item.name, batch.options.format)))
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
        BatchImagePolicy.requireExportable(batch, approved)
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
    companion object { private val writer = Mutex() }
}
