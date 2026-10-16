package com.example.contentstudio.media

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import com.example.contentstudio.storage.StreamingStorage
import java.io.IOException

/** Works with received descriptors inside an isolated UID; never opens an application path. */
internal class PdfDescriptorStore(private val descriptor: ParcelFileDescriptor,
    private val storage: StreamingStorage? = null) : PdfByteStore {
    private inline fun <T> io(block: () -> T): T = try { block() }
        catch (error: ErrnoException) { throw IOException("PDF descriptor IO failed", error) }
    override fun size(): Long = io { Os.fstat(descriptor.fileDescriptor).st_size }
    override fun read(position: Long, bytes: ByteArray, offset: Int, count: Int): Int = io {
        Os.pread(descriptor.fileDescriptor, bytes, offset, count, position)
    }
    override fun write(position: Long, bytes: ByteArray, offset: Int, count: Int) {
        val capacity = storage ?: throw IOException("Read-only PDF descriptor")
        capacity.requireRoom(count.toLong())
        io {
            var done = 0
            while (done < count) {
                val written = Os.pwrite(descriptor.fileDescriptor, bytes, offset+done, count-done, position+done)
                if (written <= 0) throw IOException("PDF descriptor write made no progress")
                done += written
            }
        }
    }
    override fun close() = descriptor.close()
}
