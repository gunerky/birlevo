package com.example.contentstudio.storage

import org.junit.Assert.*
import org.junit.Test

class WorkingMemoryTest {
    private val mib = 1024L*1024
    @Test fun heapAndSystemPressureBothConstrainAdmissionAndAreRechecked() {
        var system = 500*mib
        val memory = WorkingMemory { MemorySnapshot(256*mib,64*mib,system,100*mib) }
        assertEquals(160*mib,memory.available())
        memory.requireBytes(128*mib)
        system = 120*mib
        assertEquals(20*mib,memory.available())
        try { memory.requireBytes(128*mib); fail() } catch (_: WorkingMemoryException) { }
        assertEquals(2560,MediaResourcePlan.decode(4000,4000,2560).targetWidth)
    }
    @Test fun invalidMeasurementsFailClosedAndMissingSystemDataUsesHeapOnly() {
        for (state in listOf(MemorySnapshot(0,0),MemorySnapshot(100,101),MemorySnapshot(100,-1),MemorySnapshot(100,0,-1))) {
            try { WorkingMemory { state }.available(); fail() } catch (_: WorkingMemoryException) { }
        }
        assertEquals(112*mib,WorkingMemory { MemorySnapshot(128*mib,0) }.available())
    }
    @Test fun samplingNeverDropsPixelsNeededByTheRequestedOutput() {
        for (width in listOf(17,512,1920,4000,10000)) for (height in listOf(11,512,3000,10000))
            for (edge in listOf(512,1280,2048,2560,4096)) {
                val plan = MediaResourcePlan.decode(width,height,edge)
                assertTrue(plan.width >= plan.targetWidth && plan.height >= plan.targetHeight)
                assertTrue(maxOf(plan.targetWidth,plan.targetHeight) <= edge)
                assertTrue(plan.targetWidth <= width && plan.targetHeight <= height)
                assertTrue(plan.sample and (plan.sample-1) == 0)
            }
        assertEquals(1,MediaResourcePlan.decode(4000,4000,2560).sample)
        assertEquals(2560,MediaResourcePlan.decode(4000,4000,2560).targetHeight)
        assertEquals(2,MediaResourcePlan.decode(10000,10000,4096).sample)
    }
    @Test fun malformedHugeCostsCannotWrapIntoAnAffordableSmallRequest() {
        assertEquals(Long.MAX_VALUE,MediaResourcePlan.pixels(Int.MAX_VALUE,Int.MAX_VALUE,10))
        assertEquals(Long.MAX_VALUE,MediaResourcePlan.videoDisk(Int.MAX_VALUE,Int.MAX_VALUE,Long.MAX_VALUE))
        val memory = WorkingMemory { MemorySnapshot(128*mib,0) }
        try { memory.requireBytes(MediaResourcePlan.rasterMemory(Int.MAX_VALUE,Int.MAX_VALUE)); fail() }
        catch (_: WorkingMemoryException) { }
        assertTrue(MediaResourcePlan.videoDisk(4_000_000,128_000,60_000) >= 30_960_000L)
    }
    @Test fun compositionAccountsForSourceAndDestinationWithoutIntegerOverflow() {
        val source = MediaResourcePlan.rasterMemory(4096,2048)
        assertEquals(source + 1920L*1080*8, MediaResourcePlan.compositeMemory(source,1920,1080))
        assertEquals(Long.MAX_VALUE, MediaResourcePlan.compositeMemory(Long.MAX_VALUE,1920,1080))
        try { MediaResourcePlan.compositeMemory(-1,1920,1080); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun presentationGroupsAdaptWithoutChangingPageCountOrQuality() {
        assertEquals(1,MediaResourcePlan.presentationPageBatch(16*mib))
        assertEquals(3,MediaResourcePlan.presentationPageBatch(48*mib))
        assertEquals(8,MediaResourcePlan.presentationPageBatch(256*mib))
        assertEquals(8,MediaResourcePlan.presentationPageBatch(Long.MAX_VALUE))
    }
}
