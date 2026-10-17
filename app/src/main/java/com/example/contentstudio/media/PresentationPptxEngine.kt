package com.example.contentstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.example.contentstudio.data.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

enum class PptxMode { KEEP_APPEARANCE, EDITABLE }

data class PptxOptions(val edge: Int = 2048, val includeNotes: Boolean = false,
    val mode: PptxMode = PptxMode.KEEP_APPEARANCE) {
    init {
        require(edge in listOf(1024, 2048, 4096))
    }

    val height
        get() = edge * 9 / 16
}

/** Identity excludes repository timestamps and notes only when the user excluded them. */
object PptxRequest {
    const val MAX_BYTES = 8 * 1024 * 1024

    fun snapshot(project: StudioProject, options: PptxOptions) =
        project.copy(
            updatedAt = 0,
            slides = project.slides.map { if (options.includeNotes) it else it.copy(notes = "") },
        )

    fun encode(project: StudioProject, options: PptxOptions): String =
        JSONObject()
            .put("version", 1)
            .put("edge", options.edge)
            .put("notes", options.includeNotes)
            .put("project", JSONObject(ProjectCodec.encode(listOf(snapshot(project, options)))))
            .apply { if (options.mode != PptxMode.KEEP_APPEARANCE) put("mode", options.mode.name) }
            .toString()

    fun decode(text: String): Pair<StudioProject, PptxOptions> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val project = ProjectCodec.decode(root.getJSONObject("project").toString()).single()
        val options = PptxOptions(root.getInt("edge"), root.getBoolean("notes"),
            PptxMode.valueOf(root.optString("mode", PptxMode.KEEP_APPEARANCE.name)))
        require(project.kind == ProjectKind.PRESENTATION && project.slides.size in 1..100)
        return snapshot(project, options) to options
    }
}

data class PresentationPptxResult(
    val file: File,
    val pages: Int,
    val bytes: Long,
    val edge: Int,
    val hash: String,
    val mode: PptxMode = PptxMode.KEEP_APPEARANCE,
)

class PresentationPptxEngine(private val context: Context) {
    val folder = File(context.noBackupFilesDir, FOLDER).apply { check(isDirectory || mkdirs()) }

    suspend fun create(
        project: StudioProject,
        options: PptxOptions,
        progress: (Int) -> Unit = {},
    ): PresentationPptxResult {
        validate(project, options)
        val file = File(folder, "output-${UUID.randomUUID()}.pptx")
        val partial = File(file.path + ".part")
        val coroutine = currentCoroutineContext()
        try {
            FileOutputStream(partial).use { disk ->
                var written = 0L
                val bounded =
                    object : OutputStream() {
                        override fun write(b: Int) {
                            write(byteArrayOf(b.toByte()), 0, 1)
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            coroutine.ensureActive()
                            require(written + len <= MAX_BYTES) { "PPTX output exceeds size limit" }
                            disk.write(b, off, len)
                            written += len
                        }

                        override fun flush() = disk.flush()
                        // Package close must not close disk before fd.sync().
                    }
                PptxPackage(bounded, project.slides.size, options.includeNotes).use { archive ->
                    if (options.mode == PptxMode.EDITABLE) archive.markEditable()
                    project.slides.forEachIndexed { index, slide ->
                        coroutine.ensureActive()
                        if (options.mode == PptxMode.EDITABLE) {
                            EditablePptxSlide.write(context, archive, project, index, options)
                        } else {
                        val bitmap =
                            PresentationPdf(context)
                                .renderSlideBitmap(project, index, options.edge, options.height)
                        try {
                            archive.slide(slide.notes) {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        }
                        coroutine.ensureActive()
                        progress((index + 1) * 100 / project.slides.size)
                    }
                    archive.finish()
                }
                disk.fd.sync()
            }
            coroutine.ensureActive()
            check(partial.renameTo(file)) { "Could not commit PPTX" }
            val hash = digest(file)
            FileOutputStream(File(file.path + ".sha256")).use {
                it.write(hash.toByteArray())
                it.fd.sync()
            }
            coroutine.ensureActive()
            return restore(file, project.slides.size, file.length(), options)
        } catch (failure: Throwable) {
            discard(file)
            throw failure
        } finally {
            partial.delete()
        }
    }

    suspend fun restore(
        file: File,
        pages: Int,
        bytes: Long,
        options: PptxOptions,
    ): PresentationPptxResult {
        require(
            file.canonicalFile.parentFile == folder.canonicalFile &&
                file.name.startsWith("output-") &&
                file.extension == "pptx"
        )
        require(pages in 1..100 && bytes in 1..MAX_BYTES && file.length() == bytes)
        val expected = File(file.path + ".sha256").also { require(it.length() == 64L) }.readText()
        require(expected.matches(Regex("[a-f0-9]{64}")) && digest(file) == expected) {
            "PPTX integrity mismatch"
        }
        ZipFile(file).use { zip ->
            require(zip.size() <= (if (options.mode == PptxMode.EDITABLE) 12 else 11) + pages * 5)
            require((zip.getEntry("birlevo/editable.xml") != null) == (options.mode == PptxMode.EDITABLE))
            require(
                (zip.getEntry("ppt/notesMasters/notesMaster1.xml") != null) == options.includeNotes
            )
            for (number in 1..pages) {
                currentCoroutineContext().ensureActive()
                require(zip.getEntry("ppt/slides/slide$number.xml") != null)
                val entry = zip.getEntry("ppt/media/image$number.png")
                if (options.mode == PptxMode.EDITABLE && entry == null) continue
                requireNotNull(entry)
                require(entry.size in 1..MAX_BYTES)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(
                    (if (options.mode == PptxMode.KEEP_APPEARANCE)
                        bounds.outWidth == options.edge && bounds.outHeight == options.height
                     else bounds.outWidth in 1..options.edge && bounds.outHeight in 1..options.edge) &&
                        bounds.outMimeType == "image/png"
                )
            }
        }
        return PresentationPptxResult(file, pages, bytes, options.edge, expected, options.mode)
    }

    suspend fun preview(result: PresentationPptxResult, index: Int, project: StudioProject? = null): Bitmap {
        require(index in 0 until result.pages && result.file.length() == result.bytes)
        currentCoroutineContext().ensureActive()
        if (result.mode == PptxMode.EDITABLE)
            return PresentationPdf(context).renderSlideBitmap(requireNotNull(project), index, 512, 288)
        val bitmap =
            ZipFile(result.file).use { zip ->
                zip.getInputStream(requireNotNull(zip.getEntry("ppt/media/image${index + 1}.png")))
                    .use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            BitmapFactory.Options().apply { inSampleSize = result.edge / 512 },
                        )
                    }
            } ?: throw IOException("PPTX preview unavailable")
        try {
            currentCoroutineContext().ensureActive()
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    suspend fun verify(result: PresentationPptxResult) {
        require(result.file.length() == result.bytes && digest(result.file) == result.hash) {
            "PPTX integrity mismatch"
        }
    }

    suspend fun export(project: StudioProject, result: PresentationPptxResult, destination: Uri) {
        val sources =
            (project.slides.mapNotNull { it.imageUri } + listOfNotNull(project.sourceUri)).map(
                Uri::parse
            )
        require(sources.none { it == destination } && destination != Uri.fromFile(result.file)) {
            "Source overwrite refused"
        }
        require(
            destination.scheme != "file" ||
                sources
                    .filter { it.scheme == "file" }
                    .none {
                        File(requireNotNull(it.path)).canonicalFile ==
                            File(requireNotNull(destination.path)).canonicalFile
                    }
        ) {
            "Source overwrite refused"
        }
        val destinationIdentity = identity(destination)
        require(
            destinationIdentity == null ||
                (sources + Uri.fromFile(result.file)).none { identity(it) == destinationIdentity }
        ) {
            "Source overwrite refused"
        }
        verify(result)
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    val digest = MessageDigest.getInstance("SHA-256")
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(hex(digest.digest()) == result.hash)
                    output.flush()
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw IOException("Destination unavailable")
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        }
    }

    fun discard(file: File) {
        file.delete()
        File(file.path + ".sha256").delete()
    }

    /** Only old orphan outputs are retired; the durable job result and metadata remain intact. */
    fun cleanOldFiles(retainedName: String?) {
        val retained = retainedName?.let { setOf(File(folder, it), File(folder, "$it.sha256")) } ?: emptySet()
        com.example.contentstudio.storage.OwnedFileCleanup.files(
            folder, Regex("output-[0-9a-f-]{36}\\.pptx(?:\\.part|\\.sha256)?"), retained,
            age = 7 * com.example.contentstudio.storage.OwnedFileCleanup.DAY,
        )
    }

    private fun identity(uri: Uri): Pair<Long, Long>? =
        runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    val stat = android.system.Os.fstat(descriptor.fileDescriptor)
                    stat.st_dev to stat.st_ino
                }
            }
            .getOrNull()

    suspend fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hex(hash.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun validate(project: StudioProject, options: PptxOptions) {
        if (options.mode == PptxMode.EDITABLE) require(EditablePptxPolicy.supports(project)) {
            "Editable PPTX supports Latin text only; choose keep appearance for other scripts"
        }
        require(project.kind == ProjectKind.PRESENTATION && project.slides.size in 1..100)
        require(
            project.title.length <= 500 &&
                project.slides.sumOf { it.heading.length + it.body.length } <= 200000
        )
        require(
            project.slides.all {
                it.heading.length <= 1000 &&
                    it.body.length <= 8000 &&
                    (!options.includeNotes || it.notes.length <= 8000)
            }
        )
        if (project.slides.all { it.heading.isBlank() && it.body.isBlank() && it.imageUri == null })
            throw PdfEmptyPresentation()
    }

    companion object {
        const val FOLDER = "presentation-pptx"
        const val MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val MAX_BYTES = 512L * 1024 * 1024
    }
}
