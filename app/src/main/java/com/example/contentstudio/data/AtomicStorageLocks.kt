package com.example.contentstudio.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** AtomicFile itself does not serialize readers against writes, including across instances.
 * Its recovery on openRead must not remove another writer's in-flight temporary file.
 * These app-private checkpoint paths are fixed and bounded; no media/bitmap is cached here.
 */
internal object AtomicStorageLocks {
    private val locks = ConcurrentHashMap<String, Any>()
    fun <T> access(file: File, action: () -> T): T =
        synchronized(locks.getOrPut(file.canonicalPath) { Any() }) { action() }
}
