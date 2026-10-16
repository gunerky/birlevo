package com.example.contentstudio.storage

import android.os.StatFs
import java.io.File
import java.io.FileDescriptor
import android.system.Os

/** Call on IO; measure the actual app-private volume, never infer a SAF provider's capacity. */
fun deviceStorage(folder: File): StreamingStorage = StreamingStorage {
    val stats = StatFs(folder.absolutePath)
    StorageSnapshot(stats.totalBytes, stats.availableBytes)
}

/** Descriptor access also works inside the isolated PDF worker; no private path is disclosed. */
fun descriptorStorage(descriptor: FileDescriptor): StreamingStorage = StreamingStorage {
    val stats = Os.fstatvfs(descriptor)
    StorageSnapshot(Math.multiplyExact(stats.f_blocks,stats.f_frsize),Math.multiplyExact(stats.f_bavail,stats.f_frsize))
}
