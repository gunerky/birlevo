package com.example.contentstudio.documents

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DocumentSource(val file: File, val originalUri: Uri, val name: String, val bytes: Long)
data class ImportedDocument(val source: DocumentSource, val content: DocumentContent)
data class DocumentResult(val file: File, val target: DocumentTarget, val pages: Int = 0)

/** Owned raw snapshot; all read/parse/write work belongs on Dispatchers.IO. */
class TextDocumentEngine(private val context: Context) {
    private val folder get() = File(context.noBackupFilesDir, "text-documents").apply { check(isDirectory || mkdirs()) }
    suspend fun import(uri: Uri, encoding: DocumentEncoding): ImportedDocument {
        val file = File.createTempFile("source-", ".bin", folder)
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.take(256) ?: "document.txt"
            val input = context.contentResolver.openInputStream(uri) ?: throw DocumentException(DocumentIssue.ACCESS)
            input.use { stream -> file.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var bytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    bytes += read
                    if (bytes > DocumentPolicy.MAX_BYTES) throw DocumentException(DocumentIssue.LIMIT)
                    output.write(buffer, 0, read)
                }
            } }
            val source = DocumentSource(file, uri, name, file.length())
            val kind = TextDocumentCodec.inferKind(name, context.contentResolver.getType(uri))
            val content = read(source, kind, encoding)
            currentCoroutineContext().ensureActive()
            return ImportedDocument(source, content)
        } catch (error: Throwable) { file.delete(); throw error }
    }

    suspend fun read(source: DocumentSource, kind: DocumentKind, encoding: DocumentEncoding,
        delimiter: Char? = null): DocumentContent {
        val bytes = source.file.inputStream().use { bounded(it) }
        val job = currentCoroutineContext()
        job.ensureActive()
        return TextDocumentCodec.parse(bytes, kind, encoding, delimiter) { job.ensureActive() }
    }

    private suspend fun bounded(input: InputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (bytes.size().toLong() + read > DocumentPolicy.MAX_BYTES) throw DocumentException(DocumentIssue.LIMIT)
            bytes.write(buffer, 0, read)
        }
        return bytes.toByteArray()
    }

    suspend fun create(content: DocumentContent, target: DocumentTarget): DocumentResult {
        require(target in DocumentPolicy.targets(content.kind))
        val file = File.createTempFile("result-", ".${target.extension}", folder)
        try {
            val job = currentCoroutineContext()
            val pages = if (target == DocumentTarget.PDF) TextDocumentPdf(context).write(content, file)
            else {
                file.outputStream().bufferedWriter(Charsets.UTF_8).use {
                    TextDocumentCodec.write(content, target, it) { job.ensureActive() }
                }
                0
            }
            job.ensureActive()
            return DocumentResult(file, target, pages)
        } catch (error: Throwable) { file.delete(); throw error }
    }

    suspend fun export(result: DocumentResult, original: Uri, destination: Uri) {
        require(destination != original)
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw DocumentException(DocumentIssue.EXPORT)
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw error
        }
    }
}
