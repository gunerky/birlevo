package com.example.contentstudio.media

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfScratchArenaTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Store(val file: RandomAccessFile) : PdfByteStore {
        var closed = false
        var failWrites = false
        override fun size() = file.length()
        override fun read(position: Long, bytes: ByteArray, offset: Int, count: Int): Int {
            file.seek(position); return file.read(bytes, offset, count)
        }
        override fun write(position: Long, bytes: ByteArray, offset: Int, count: Int) {
            if (failWrites) throw IOException("Simulated volume pressure")
            file.seek(position); file.write(bytes, offset, count)
        }
        override fun close() { closed = true; file.close() }
    }
    private fun store() = Store(RandomAccessFile(temporary.newFile(), "rw"))
    private inline fun <reified T: Throwable> fails(block: () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }

    @Test fun cachedInputPreservesOffsetsShortReadsAndEof() {
        val store = store()
        val bytes = Random(10).nextBytes(65536*3+7)
        store.write(0, bytes, 0, bytes.size)
        PdfSeekableRead(store).use { input ->
            assertEquals(bytes[0].toInt() and 255, input.peek())
            assertEquals(0L, input.position)
            assertArrayEquals(bytes, input.readFully(bytes.size))
            assertTrue(input.isEOF); assertEquals(-1, input.read()); assertEquals(0, input.available())
            input.rewind(65537)
            assertEquals((bytes.size-65537).toLong(), input.position)
            assertArrayEquals(bytes.takeLast(65537).toByteArray(), input.readFully(65537))
            input.seek(65530)
            assertArrayEquals(bytes.copyOfRange(65530, 65550), input.readFully(20))
            fails<EOFException> { input.readFully(bytes.size) }
            fails<IOException> { input.seek(-1) }
        }
        assertTrue(store.closed)
    }

    @Test fun scratchInterleavedStreamsMatchIndependentByteArrays() {
        val random = Random(20261016)
        val expected = List(3) { random.nextBytes(65536*3+101) }
        PdfScratchArena(store()).use { arena ->
            arena.scope().use { scope ->
                val buffers = expected.map { scope.createBuffer(ByteArrayInputStream(it)) }
                repeat(500) {
                    val index = random.nextInt(3)
                    val at = random.nextInt(expected[index].size-500)
                    val patch = random.nextBytes(random.nextInt(1, 500))
                    buffers[index].seek(at.toLong()); buffers[index].write(patch)
                    patch.copyInto(expected[index], at)
                    val other = random.nextInt(3)
                    val start = random.nextInt(expected[other].size-500)
                    buffers[other].seek(start.toLong())
                    assertArrayEquals(expected[other].copyOfRange(start, start+500), buffers[other].readFully(500))
                }
                buffers.forEachIndexed { index, buffer ->
                    buffer.seek(0); assertArrayEquals(expected[index], buffer.readFully(expected[index].size))
                    assertTrue(buffer.isEOF); assertEquals(-1, buffer.peek())
                    buffer.rewind(1); assertEquals(expected[index].last().toInt() and 255, buffer.read())
                }
            }
        }
    }

    @Test fun closingSourceScopeDoesNotCloseDestinationOrItsBackingStore() {
        val store = store()
        PdfScratchArena(store).use { arena ->
            val destination = arena.scope()
            val kept = destination.createBuffer().apply { write(byteArrayOf(7, 8, 9)) }
            val source = arena.scope()
            val removed = source.createBuffer().apply { write(ByteArray(65537) { 42 }) }
            source.close(); source.close()
            assertFalse(store.closed); assertTrue(removed.isClosed)
            fails<IOException> { removed.read() }
            fails<IOException> { source.createBuffer() }
            kept.seek(0); assertArrayEquals(byteArrayOf(7, 8, 9), kept.readFully(3))
            destination.close(); assertTrue(kept.isClosed)
        }
        assertTrue(store.closed)
    }

    @Test fun releasedBlocksAreReusedAndOldBytesCannotBecomeVisible() {
        val store = store()
        PdfScratchArena(store, maxBytes = 2L*65536).use { arena ->
            arena.scope().use { scope ->
                val first = scope.createBuffer().apply { write(ByteArray(2*65536) { 77 }) }
                val second = scope.createBuffer()
                val error = runCatching { second.write(1) }.exceptionOrNull()
                assertEquals(PdfPageIssue.LIMIT, (error as PdfPageException).issue)
                first.clear()
                assertEquals(0L, first.length()); assertEquals(-1, first.read())
                second.write(byteArrayOf(1, 2, 3)); second.seek(0)
                assertArrayEquals(byteArrayOf(1, 2, 3), second.readFully(3))
                assertEquals(-1, second.read())
                fails<IOException> { second.seek(4) }
                second.clear(); second.write(ByteArray(2*65536) { 12 }); second.seek(0)
                assertTrue(second.readFully(2*65536).all { it == 12.toByte() })
                assertTrue(store.size() <= 2*65536)
            }
        }
    }

    @Test fun failedStreamCopyReleasesItsPagesAndBackingIoErrorsPropagate() {
        val store = store()
        PdfScratchArena(store, maxBytes = 2L*65536).use { arena ->
            arena.scope().use { scope ->
                fails<PdfPageException> { scope.createBuffer(ByteArrayInputStream(ByteArray(2*65536+1))) }
                val buffer = scope.createBuffer().apply { write(ByteArray(65536)) }
                buffer.clear()
                store.failWrites = true
                buffer.write(ByteArray(65536))
                // Switching to another page forces a backing-store write.
                fails<IOException> { arena.scope().createBuffer().write(1) }
            }
        }
    }

    @Test fun readFullyAdmissionRunsBeforeAllocatingAndDoesNotAdvanceOnRejection() {
        val store = store()
        store.write(0, ByteArray(100), 0, 100)
        PdfSeekableRead(store) { throw IOException("Memory admission") }.use { source ->
            fails<IOException> { source.readFully(100) }; assertEquals(0L, source.position)
        }
        PdfScratchArena(store(), admission = { throw IOException("Memory admission") }).use { arena ->
            arena.scope().use { scope ->
                val buffer = scope.createBuffer().apply { write(ByteArray(100)); seek(0) }
                fails<IOException> { buffer.readFully(100) }; assertEquals(0L, buffer.position)
            }
        }
    }
}
