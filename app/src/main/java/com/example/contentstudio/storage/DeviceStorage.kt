package com.example.contentstudio.storage

import android.os.StatFs
import java.io.File

/** Call on IO; measure the actual app-private volume, never infer a SAF provider's capacity. */
fun deviceStorage(folder: File): StreamingStorage = StreamingStorage {
    val stats = StatFs(folder.absolutePath)
    StorageSnapshot(stats.totalBytes, stats.availableBytes)
}
