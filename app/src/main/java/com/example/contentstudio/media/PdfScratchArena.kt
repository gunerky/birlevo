package com.example.contentstudio.media

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccess
import com.tom_roush.pdfbox.io.RandomAccessRead
import com.tom_roush.pdfbox.io.ScratchFile
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque

/** Positional IO, independent of filesystem paths and the caller's descriptor offset. */
internal interface PdfByteStore : Closeable {
    fun size(): Long
    fun read(position: Long, bytes: ByteArray, offset: Int, count: Int): Int
    fun write(position: Long, bytes: ByteArray, offset: Int, count: Int)
}

/** A fixed-size read cache avoids copying an entire source PDF into parser scratch memory. */
internal class PdfSeekableRead(private val store: PdfByteStore,
    private val admission: (Int) -> Unit = {}) : RandomAccessRead {
    private val size = store.size()
    private val cache = ByteArray(65536)
    private var cacheStart = -1L
    private var cached = 0
    private var cursor = 0L
    private var closed = false
    private fun checkOpen() { if (closed) throw IOException("PDF input closed") }
    override fun length(): Long { checkOpen(); return size }
    override fun getPosition(): Long { checkOpen(); return cursor }
    override fun isClosed() = closed
    override fun seek(position: Long) { checkOpen(); if (position < 0) throw IOException("Negative PDF offset"); cursor = position }
    override fun read(): Int {
        checkOpen()
        if (cursor >= size) return -1
        if (cursor < cacheStart || cursor >= cacheStart + cached) {
            cacheStart = cursor
            cached = store.read(cursor, cache, 0, minOf(cache.size.toLong(), size - cursor).toInt())
            if (cached <= 0) throw EOFException("Truncated PDF input")
        }
        return cache[(cursor++ - cacheStart).toInt()].toInt() and 255
    }
    override fun read(bytes: ByteArray) = read(bytes, 0, bytes.size)
    override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
        checkOpen(); require(offset >= 0 && count >= 0 && offset <= bytes.size - count)
        if (count == 0) return 0
        if (cursor >= size) return -1
        var copied = 0
        while (copied < count && cursor < size) {
            val value = read()
            bytes[offset + copied++] = value.toByte()
            val remaining = minOf((count-copied).toLong(), cacheStart+cached-cursor).toInt()
            if (remaining > 0) {
                cache.copyInto(bytes, offset+copied, (cursor-cacheStart).toInt(), (cursor-cacheStart).toInt()+remaining)
                cursor += remaining; copied += remaining
            }
        }
        return copied
    }
    override fun peek(): Int { val at = cursor; return read().also { cursor = at } }
    override fun rewind(count: Int) { require(count >= 0); seek(cursor-count) }
    override fun available(): Int { checkOpen(); return (size-cursor).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
    override fun isEOF(): Boolean { checkOpen(); return cursor >= size }
    override fun readFully(count: Int): ByteArray {
        require(count >= 0); checkOpen(); if (count > 0 && count.toLong() > size-cursor) throw EOFException()
        admission(count)
        return ByteArray(count).also { var done = 0; while (done < count) done += read(it, done, count-done) }
    }
    override fun close() { if (!closed) { closed = true; store.close() } }
}

/** Shared disk pages, one 64 KiB cache. Each document releases only its own stream buffers. */
internal class PdfScratchArena(private val store: PdfByteStore,
    private val maxBytes: Long = 320L * 1024 * 1024,
    private val admission: (Int) -> Unit = {}) : Closeable {
    private val blockSize = 65536
    private val cache = ByteArray(blockSize)
    private var cacheId = -1
    private var dirty = false
    private var next = 0
    private val free = ArrayDeque<Int>()
    private var closed = false
    init { require(maxBytes in blockSize.toLong()..Int.MAX_VALUE.toLong()*blockSize) }
    private fun checkOpen() { if (closed) throw IOException("PDF scratch closed") }
    private fun flush() {
        if (dirty) { store.write(cacheId.toLong()*blockSize, cache, 0, blockSize); dirty = false }
    }
    private fun load(id: Int) {
        checkOpen()
        if (cacheId == id) return
        flush(); cacheId = -1
        var done = 0
        while (done < blockSize) {
            val count = store.read(id.toLong()*blockSize+done, cache, done, blockSize-done)
            if (count <= 0) throw EOFException("Truncated PDF scratch")
            done += count
        }
        cacheId = id
    }
    private fun allocate(): Int {
        checkOpen()
        val id = if (free.isNotEmpty()) free.removeFirst() else {
            if ((next.toLong()+1)*blockSize > maxBytes) throw PdfPageException(PdfPageIssue.LIMIT)
            next++
        }
        flush(); cache.fill(0); cacheId = id; dirty = true
        return id
    }
    private fun release(id: Int) {
        if (cacheId == id) { cacheId = -1; dirty = false }
        free.addLast(id)
    }

    fun scope(): ScratchFile { checkOpen(); return Scope() }

    private inner class Scope : ScratchFile(MemoryUsageSetting.setupMainMemoryOnly(4096)) {
        private val buffers = linkedSetOf<Buffer>()
        private var ended = false
        override fun createBuffer(): RandomAccess {
            checkOpen(); if (ended) throw IOException("PDF document scratch closed")
            return Buffer(buffers).also(buffers::add)
        }
        override fun createBuffer(input: InputStream): RandomAccess {
            val buffer = createBuffer()
            try {
                val bytes = ByteArray(blockSize)
                while (true) { val count = input.read(bytes); if (count < 0) break; if (count > 0) buffer.write(bytes, 0, count) }
                buffer.seek(0)
                return buffer
            } catch (error: Throwable) { buffer.close(); throw error }
        }
        override fun close() {
            if (ended) return
            ended = true
            while (buffers.isNotEmpty()) buffers.first().close()
            super.close()
        }
    }

    private inner class Buffer(private val owner: MutableSet<Buffer>) : RandomAccess {
        private val blocks = ArrayList<Int>()
        private var cursor = 0L
        private var size = 0L
        private var ended = false
        private val one = ByteArray(1)
        private fun checkBuffer() { checkOpen(); if (ended) throw IOException("PDF stream closed") }
        override fun isClosed() = ended || closed
        override fun length(): Long { checkBuffer(); return size }
        override fun getPosition(): Long { checkBuffer(); return cursor }
        override fun seek(position: Long) {
            checkBuffer()
            if (position !in 0..size) throw IOException("PDF stream offset outside its length")
            cursor = position
        }
        override fun read(): Int = if (read(one) < 0) -1 else one[0].toInt() and 255
        override fun read(bytes: ByteArray) = read(bytes, 0, bytes.size)
        override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
            checkBuffer(); require(offset >= 0 && count >= 0 && offset <= bytes.size-count)
            if (count == 0) return 0
            if (cursor >= size) return -1
            val total = minOf(count.toLong(), size-cursor).toInt()
            var done = 0
            while (done < total) {
                load(blocks[(cursor/blockSize).toInt()])
                val at = (cursor%blockSize).toInt()
                val amount = minOf(total-done, blockSize-at)
                cache.copyInto(bytes, offset+done, at, at+amount)
                cursor += amount; done += amount
            }
            return total
        }
        override fun write(value: Int) { one[0] = value.toByte(); write(one) }
        override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
        override fun write(bytes: ByteArray, offset: Int, count: Int) {
            checkBuffer(); require(offset >= 0 && count >= 0 && offset <= bytes.size-count)
            var done = 0
            while (done < count) {
                val block = (cursor/blockSize).toInt()
                if (block == blocks.size) blocks.add(allocate()) else load(blocks[block])
                val at = (cursor%blockSize).toInt()
                val amount = minOf(count-done, blockSize-at)
                bytes.copyInto(cache, at, offset+done, offset+done+amount)
                dirty = true; cursor += amount; done += amount; size = maxOf(size, cursor)
            }
        }
        override fun peek(): Int { val at = cursor; return read().also { cursor = at } }
        override fun rewind(count: Int) { require(count >= 0); seek(cursor-count) }
        override fun available(): Int { checkBuffer(); return minOf(size-cursor, Int.MAX_VALUE.toLong()).toInt() }
        override fun isEOF(): Boolean { checkBuffer(); return cursor >= size }
        override fun readFully(count: Int): ByteArray {
            require(count >= 0); checkBuffer(); if (count.toLong() > size-cursor) throw EOFException()
            admission(count)
            return ByteArray(count).also { var done = 0; while (done < count) done += read(it, done, count-done) }
        }
        override fun clear() { checkBuffer(); blocks.forEach(::release); blocks.clear(); cursor = 0; size = 0 }
        override fun close() {
            if (ended) return
            ended = true; blocks.forEach(::release); blocks.clear(); owner.remove(this)
        }
    }

    override fun close() {
        if (!closed) { closed = true; dirty = false; free.clear(); store.close() }
    }
}
