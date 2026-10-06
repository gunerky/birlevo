package com.example.contentstudio.media

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serialize disposable UI previews; exports retain their independent processing pipeline.
 * A decode can finish after its requesting composable has left the screen. Keep ownership
 * across the IO-to-main handoff so that cancelled, unpublished bitmaps are released.
 * Published bitmaps are GC-managed because Compose/RenderThread can still reference them.
 */
internal object PreviewLoader {
    private val decode = Mutex()

    suspend fun load(block: suspend () -> Bitmap): Bitmap {
        var pending: Bitmap? = null
        var published = false
        try {
            val result = withContext(Dispatchers.IO) {
                decode.withLock { block().also { pending = it } }
            }
            published = true
            return result
        } finally {
            if (!published) pending?.recycle()
        }
    }
}
