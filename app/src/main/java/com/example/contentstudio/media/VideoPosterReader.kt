package com.example.contentstudio.media

import androidx.core.graphics.scale
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.contentstudio.storage.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlin.math.max

/** Optional preview only. Its sampling never changes encoded video or full still export. */
internal class VideoPosterReader(private val context: Context, private val memory: WorkingMemory) {
    suspend fun read(file: File, retriever: MediaMetadataRetriever, width: Int, height: Int, durationMs: Long): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 27) {
            memory.requireBytes(MediaResourcePlan.rasterMemory(minOf(512, width), minOf(512, height)))
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
        } else try {
            // Legacy retrievers have no scaled-frame API. Sample the decoder's YUV planes
            // directly into the preview instead of creating a full-size ARGB bitmap.
            VideoFrameExtractor(context, memory).read(VideoFrameTarget(file, durationMs, file.name), 0, true).bitmap
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (pressure: WorkingMemoryException) { throw pressure }
        catch (_: Exception) {
            // Preserve previews on vendor decoders that cannot expose Image planes.
            // This fallback still requires admission for the full-resolution allocation.
            memory.requireBytes(MediaResourcePlan.rasterMemory(width, height))
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                var retained = false
                try {
                    val ratio = minOf(1f, 512f / max(full.width, full.height))
                    full.scale(max(1, (full.width * ratio).toInt()),
                        max(1, (full.height * ratio).toInt()), true).also { retained = it === full }
                } finally { if (!retained) full.recycle() }
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
    catch (_: OutOfMemoryError) { null }
}
