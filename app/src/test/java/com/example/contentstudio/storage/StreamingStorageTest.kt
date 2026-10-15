package com.example.contentstudio.storage

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class StreamingStorageTest {
    private val mib = 1024L * 1024
    @Test fun actualVolumeHeadroomChangesLimitWithoutChangingContentCeilings() {
        val small = StreamingStorage { StorageSnapshot(1024*mib, 100*mib) }
        val large = StreamingStorage { StorageSnapshot(128*1024*mib, 10*1024*mib) }
        assertEquals(36*mib, small.limit(StreamingLimits.CONTENT_BYTES))
        assertEquals(StreamingLimits.CONTENT_BYTES, large.limit(StreamingLimits.CONTENT_BYTES))
        assertEquals(0L, StreamingStorage { StorageSnapshot(1024*mib, 32*mib) }.limit(Long.MAX_VALUE))
        assertTrue(StreamingLimits.zipWorkingBytes(StreamingLimits.CONTENT_BYTES,20) <= StreamingLimits.ZIP_BYTES)
    }
    @Test fun invalidMeasurementsFailClosedAndLargeArithmeticDoesNotOverflow() {
        for (snapshot in listOf(StorageSnapshot(0,0), StorageSnapshot(100,-1), StorageSnapshot(100,101))) {
            try { StreamingStoragePolicy.available(snapshot); fail() } catch (_: StorageCapacityException) { }
        }
        assertTrue(StreamingStoragePolicy.available(StorageSnapshot(Long.MAX_VALUE,Long.MAX_VALUE)) > 0)
    }
    @Test fun guardStopsOnChangingFreeSpaceBeforeForwardingMoreBytes() {
        var queries = 0
        val storage = StreamingStorage {
            StorageSnapshot(1024*mib, if (++queries == 1) 100*mib else 64*mib)
        }
        val bytes = ByteArrayOutputStream()
        val guarded = storage.guard(bytes)
        val block = ByteArray(65536)
        repeat(16) { guarded.write(block) }
        try { guarded.write(block); fail() } catch (_: StorageCapacityException) { }
        assertEquals(mib.toInt(), bytes.size())
        assertEquals(2,queries)
    }
    @Test fun unknownRemoteDestinationIsNotAssumedToConsumeLocalBytes() {
        val storage = StreamingStorage { StorageSnapshot(1024*mib,65*mib) }
        val output = object: java.io.OutputStream() { var count=0L
            override fun write(value: Int) { count++ }
            override fun write(b: ByteArray, off: Int, len: Int) { count += len }
        }
        storage.guard(output).use { guarded -> repeat(48) { guarded.write(ByteArray(65536)) } }
        assertEquals(3*mib, output.count)
        try { storage.requireRoom(2*mib); fail() } catch (_: StorageCapacityException) { }
    }
}
