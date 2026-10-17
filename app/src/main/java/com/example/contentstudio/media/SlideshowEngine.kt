package com.example.contentstudio.media

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.example.contentstudio.data.StudioProject
import com.example.contentstudio.storage.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

/** One bounded raster at a time. Intermediate PNGs are private job assets, never project assets. */
@androidx.annotation.OptIn(UnstableApi::class)
class SlideshowEngine(
    private val context: Context,
    private val capacity: (File) -> StreamingStorage = ::deviceStorage,
    private val memory: WorkingMemory = deviceMemory(context),
) {
    val folder by lazy {
        File(context.noBackupFilesDir, FOLDER).apply { check(isDirectory || mkdirs()) }
    }
    private val painter = PresentationPdf(context, memory = memory)
    private val videos = VideoCompressor(context, FOLDER)
    private val support = VideoOutputSupport()

    companion object {
        const val FOLDER = "slideshow-video"
    }

    fun files(value: SlideshowTimeline): Set<File> =
        value.assets.map { it.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES) }.toSet()

    /** Only inactive, unreferenced private job files are eligible; call after durable recovery. */
    fun removeStaleFiles(retained: Set<File>, now: Long = System.currentTimeMillis()) {
        com.example.contentstudio.storage.OwnedFileCleanup.files(
            folder, Regex("(?:page-[0-9]+\\.(?:jpg|png|webp|bmp|heic|heif|avif)|(?:working|output)-[0-9a-f-]{36}\\.mp4)"),
            retained, age = 7 * com.example.contentstudio.storage.OwnedFileCleanup.DAY, now = now,
        )
        com.example.contentstudio.storage.OwnedFileCleanup.directories(
            folder, Regex("render-[0-9a-f-]{36}"), Regex("[0-9]+\\.png"), retained, now = now,
        )
    }

    fun restore(value: SlideshowTimeline): SlideshowTimeline {
        value.validate()
        files(value)
        value.assets.forEach {
            SlideImages.validate(
                context,
                Uri.fromFile(it.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES)),
            )
        }
        return value
    }

    suspend fun importImages(
        uris: List<Uri>,
        current: SlideshowTimeline,
        progress: (Int) -> Unit,
    ): SlideshowTimeline {
        val pages =
            ImageSnapshots(context, folder)
                .importPages(
                    uris,
                    100 - current.frames.size,
                    PhotoPdf.MAX_BATCH_BYTES - current.assets.sumOf { it.bytes },
                    progress,
                )
        try {
            return current
                .copy(
                    frames =
                        current.frames +
                            pages.map {
                                SlideshowFrame(
                                    it.id,
                                    MediaSessionCodec.asset(it),
                                    slide =
                                        if (current.presentation)
                                            com.example.contentstudio.data.Slide(id = it.id)
                                        else null,
                                )
                            }
                )
                .also {
                    it.validate()
                    currentCoroutineContext().ensureActive()
                }
        } catch (failure: Throwable) {
            pages.forEach { it.file.delete() }
            throw failure
        }
    }

    suspend fun importPresentation(
        project: StudioProject,
        progress: (Int) -> Unit,
    ): SlideshowTimeline {
        require(project.slides.size in 1..100)
        if (project.slides.all { it.heading.isBlank() && it.body.isBlank() && it.imageUri == null })
            throw PdfEmptyPresentation()
        val uris = project.slides.mapNotNull { it.imageUri }.distinct().map(Uri::parse)
        val pages =
            if (uris.isEmpty()) emptyList()
            else
                ImageSnapshots(context, folder)
                    .importPages(uris, 100, PhotoPdf.MAX_BATCH_BYTES, progress)
        try {
            val assets =
                uris.zip(pages.map(MediaSessionCodec::asset)).associate {
                    it.first.toString() to it.second
                }
            return SlideshowTimeline(
                    project.slides.map { slide ->
                        SlideshowFrame(
                            slide.id,
                            slide.imageUri?.let(assets::getValue),
                            slide = slide.copy(imageUri = null, notes = ""),
                        )
                    },
                    project.title,
                    project.theme,
                    presentation = true,
                )
                .also {
                    it.validate()
                    currentCoroutineContext().ensureActive()
                }
        } catch (failure: Throwable) {
            pages.forEach { it.file.delete() }
            throw failure
        }
    }

    fun issue(value: SlideshowTimeline): VideoOutputIssue? {
        val target = value.target()
        return support.issue(
            VideoInfo(target.width, target.height, value.durationMs, false, null, frameRate = 30f),
            target,
            VideoOutputFormat.MP4_AVC,
            forceAudioEncoding = true,
        )
    }

    suspend fun preview(value: SlideshowTimeline, index: Int, edge: Int = 512): Bitmap {
        require(index in value.frames.indices && edge in 16..1920)
        val target = value.target()
        val scale = edge.toDouble() / maxOf(target.width, target.height)
        return raster(
            value,
            index,
            (target.width * scale).toInt().coerceAtLeast(16),
            (target.height * scale).toInt().coerceAtLeast(16),
        )
    }

    private suspend fun raster(
        value: SlideshowTimeline,
        index: Int,
        width: Int,
        height: Int,
        preparedProject: StudioProject? = null,
    ): Bitmap {
        currentCoroutineContext().ensureActive()
        memory.requireBytes(MediaResourcePlan.rasterMemory(width, height))
        val frame = value.frames[index]
        if (value.presentation) {
            val project =
                preparedProject ?: value.project {
                    Uri.fromFile(it.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES))
                        .toString()
                }
            // Paint vectors/text at their final scale, including portrait center crop.
            return painter.renderSlideBitmap(
                project,
                index,
                width,
                height,
                crop = value.fit == VideoFit.CENTER_CROP,
            )
        }
        val raw =
            SlideImages.decode(
                context,
                Uri.fromFile(
                    requireNotNull(frame.asset)
                        .resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES)
                ),
                minOf(4096, maxOf(width, height) * 2),
            )
        var output: Bitmap? = null
        try {
            output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.BLACK)
            val sx = width.toFloat() / raw.width
            val sy = height.toFloat() / raw.height
            val scale = if (value.fit == VideoFit.CENTER_CROP) maxOf(sx, sy) else minOf(sx, sy)
            val w = raw.width * scale
            val h = raw.height * scale
            canvas.drawBitmap(
                raw,
                null,
                RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            currentCoroutineContext().ensureActive()
            return output
        } catch (failure: Throwable) {
            output?.recycle()
            throw failure
        } finally {
            raw.recycle()
        }
    }

    suspend fun create(value: SlideshowTimeline, progress: (Int?) -> Unit): CompressedVideo {
        require(value.frames.isNotEmpty())
        value.validate()
        files(value)
        issue(value)?.let { throw VideoOutputUnavailable(it) }
        val target = value.target()
        val storage = capacity(folder)
        val encodedSpace = MediaResourcePlan.videoDisk(target.bitrate, 0, value.durationMs)
        memory.requireBytes(MediaResourcePlan.videoMemory(target.width, target.height, target.width, target.height))
        val scratch = File(folder, "render-${UUID.randomUUID()}")
        val output = File(folder, "output-${UUID.randomUUID()}.mp4")
        val working = File(folder, "working-${UUID.randomUUID()}.mp4")
        var result: CompressedVideo? = null
        var delivered = false
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            storage.requireRoom(encodedSpace + MediaResourcePlan.rasterDisk(target.width, target.height))
            check(scratch.mkdir())
            // Keep one lightweight immutable project for this job, rather than rebuilding it per frame.
            val project = if (value.presentation) value.project {
                Uri.fromFile(it.resolve(folder, "page-", FormatSupport.MAX_IMAGE_BYTES)).toString()
            } else null
            withTimeout(20 * 60 * 1000L) {
                val monitor = launch(Dispatchers.IO) {
                    while (isActive) { storage.requireRoom(1024 * 1024); delay(250) }
                }
                try {
                    val rendered =
                        value.frames.mapIndexed { index, _ ->
                            currentCoroutineContext().ensureActive()
                            storage.requireRoom(encodedSpace + MediaResourcePlan.rasterDisk(target.width, target.height))
                            val bitmap = raster(value, index, target.width, target.height, project)
                            val file = File(scratch, "$index.png")
                            try {
                                file.outputStream().use {
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, storage.guard(it)))
                                    it.fd.sync()
                                }
                            } finally {
                                bitmap.recycle()
                            }
                            progress((index + 1) * 20 / value.frames.size)
                            file
                        }
                    memory.requireBytes(MediaResourcePlan.videoMemory(target.width, target.height, target.width, target.height))
                    storage.requireRoom(encodedSpace)
                    withContext(Dispatchers.Main.immediate) {
                        val handler = Handler(Looper.getMainLooper())
                        var transformer: Transformer? = null
                        var poll: Runnable? = null
                        var completed = false
                        try {
                            suspendCancellableCoroutine<Unit> { continuation ->
                                val listener =
                                    object : Transformer.Listener {
                                        override fun onCompleted(
                                            composition: Composition,
                                            exportResult: ExportResult,
                                        ) {
                                            completed = true
                                            if (continuation.isActive) continuation.resume(Unit)
                                        }

                                        override fun onError(
                                            composition: Composition,
                                            exportResult: ExportResult,
                                            exception: ExportException,
                                        ) {
                                            if (continuation.isActive)
                                                continuation.resumeWithException(exception)
                                        }
                                    }
                                val encoder =
                                    DefaultEncoderFactory.Builder(context)
                                        .setEnableFallback(false)
                                        .setRequestedVideoEncoderSettings(
                                            VideoEncoderSettings.Builder()
                                                .setBitrate(target.bitrate)
                                                .build()
                                        )
                                        .build()
                                val active =
                                    Transformer.Builder(context)
                                        .setVideoMimeType("video/avc")
                                        .setEncoderFactory(encoder)
                                        .setMaxDelayBetweenMuxerSamplesMs(120_000L)
                                        .addListener(listener)
                                        .build()
                                transformer = active
                                val items =
                                    rendered.mapIndexed { index, file ->
                                        EditedMediaItem.Builder(
                                                MediaItem.Builder()
                                                    .setUri(Uri.fromFile(file))
                                                    .setMimeType("image/png")
                                                    .setImageDurationMs(value.frames[index].durationMs)
                                                    .build()
                                            )
                                            .setFrameRate(30)
                                            .setRemoveAudio(true)
                                            .setEffects(
                                                Effects(
                                                    emptyList(),
                                                    listOf(
                                                        Presentation.createForWidthAndHeight(
                                                            target.width,
                                                            target.height,
                                                            Presentation.LAYOUT_SCALE_TO_FIT,
                                                        )
                                                    ),
                                                )
                                            )
                                            .build()
                                    }
                                val composition =
                                    Composition.Builder(EditedMediaItemSequence.withVideoFrom(items))
                                        .build()
                                poll =
                                    object : Runnable {
                                        override fun run() {
                                            if (!continuation.isActive) return
                                            val holder = ProgressHolder()
                                            progress(
                                                if (
                                                    active.getProgress(holder) ==
                                                        Transformer.PROGRESS_STATE_AVAILABLE
                                                )
                                                    20 + holder.progress * 80 / 100
                                                else null
                                            )
                                            handler.postDelayed(this, 250)
                                        }
                                    }
                                active.start(composition, working.path)
                                handler.post(requireNotNull(poll))
                            }
                        } finally {
                            poll?.let(handler::removeCallbacks)
                            try {
                                if (completed) check(working.renameTo(output))
                            } finally {
                                transformer?.cancel()
                            }
                        }
                    }
                } finally { monitor.cancelAndJoin() }
            }
            currentCoroutineContext().ensureActive()
            result =
                videos.restoreOutput(
                    output,
                    output.length(),
                    android.os.SystemClock.elapsedRealtime() - started,
                )
            validateResult(value, result.info)
            currentCoroutineContext().ensureActive()
            delivered = true
            return result
        } finally {
            scratch.listFiles()?.forEach { it.delete() }
            scratch.delete()
            working.delete()
            if (!delivered) {
                output.delete()
                result?.info?.poster?.recycle()
            }
        }
    }

    fun validateResult(value: SlideshowTimeline, info: VideoInfo) {
        val target = value.target()
        require(
            info.width == target.width &&
                info.height == target.height &&
                !info.hasAudio &&
                info.videoMime == "video/avc"
        )
        require(kotlin.math.abs(info.durationMs - value.durationMs) <= 100) {
            "Still-video timing mismatch"
        }
        require(info.frameRate == null || kotlin.math.abs(info.frameRate - 30f) < 0.1f)
    }

    suspend fun restoreOutput(
        value: SlideshowTimeline,
        file: File,
        bytes: Long,
        elapsed: Long,
    ): CompressedVideo {
        val result = videos.restoreOutput(file, bytes, elapsed)
        try {
            validateResult(value, result.info)
            return result
        } catch (failure: Throwable) {
            result.info.poster?.recycle()
            throw failure
        }
    }

    suspend fun export(value: SlideshowTimeline, result: CompressedVideo, destination: Uri) {
        require(value.assets.none { it.uri == destination.toString() }) {
            "Cannot overwrite original"
        }
        require(result.file.isFile && result.file.length() == result.bytes && result.bytes > 0)
        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                result.file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    out.flush()
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw IOException("Destination unavailable")
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw failure
        }
    }
}
