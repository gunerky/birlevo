package com.example.contentstudio.storage

import java.io.IOException
import java.io.OutputStream

/** Encoded content stays on disk. These ceilings do not describe decoded bitmap/codec memory. */
object StreamingLimits {
    const val CONTENT_BYTES = 512L * 1024 * 1024
    // ZIP headers and stored DEFLATE blocks must not make our own maximum-size batch unreadable.
    const val ZIP_BYTES = CONTENT_BYTES + 16L * 1024 * 1024
    fun zipWorkingBytes(content: Long, entries: Int): Long {
        require(content in 0..CONTENT_BYTES && entries in 1..20)
        return content + content / 100 + entries * 2048L + 65536
    }
}

data class StorageSnapshot(val total: Long, val available: Long)
class StorageCapacityException : IOException("Insufficient device working space")

/** Reserve is a product safety margin, not a platform guarantee or a reservation of disk blocks. */
object StreamingStoragePolicy {
    fun available(snapshot: StorageSnapshot): Long {
        if (snapshot.total <= 0 || snapshot.available < 0 || snapshot.available > snapshot.total)
            throw StorageCapacityException()
        val reserve = (snapshot.total / 50).coerceIn(64L * 1024 * 1024, 512L * 1024 * 1024)
        return (snapshot.available - reserve).coerceAtLeast(0)
    }
}

/** Query again at most each MiB of writes; no unbounded allocation or per-byte syscall. */
class StreamingStorage(private val snapshot: () -> StorageSnapshot) {
    fun limit(ceiling: Long): Long {
        require(ceiling >= 0)
        return minOf(ceiling, StreamingStoragePolicy.available(snapshot()))
    }
    fun requireRoom(bytes: Long) {
        require(bytes >= 0)
        if (bytes > limit(Long.MAX_VALUE)) throw StorageCapacityException()
    }
    fun guard(output: OutputStream): OutputStream = object : OutputStream() {
        private var credit = 0L
        override fun write(value: Int) { write(byteArrayOf(value.toByte())) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            var at = offset
            var remaining = length
            while (remaining > 0) {
                if (credit == 0L) {
                    credit = limit(1024L * 1024)
                    if (credit == 0L) throw StorageCapacityException()
                }
                val count = minOf(remaining.toLong(), credit).toInt()
                output.write(bytes, at, count)
                credit -= count
                at += count; remaining -= count
            }
        }
        override fun flush() = output.flush()
        override fun close() = output.close()
    }
}
