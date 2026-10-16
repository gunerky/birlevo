package com.example.contentstudio.storage

import java.io.IOException

data class MemorySnapshot(val heapLimit: Long, val heapUsed: Long,
    val systemAvailable: Long? = null, val systemThreshold: Long = 0)
class WorkingMemoryException : IOException("Insufficient memory for the requested operation")

/** Admission policy, not a native/GPU allocation guarantee. Never changes output settings. */
class WorkingMemory(private val snapshot: () -> MemorySnapshot) {
    fun available(): Long {
        val state = snapshot()
        if (state.heapLimit <= 0 || state.heapUsed !in 0..state.heapLimit || state.systemThreshold < 0 ||
            state.systemAvailable?.let { it < 0 } == true) throw WorkingMemoryException()
        val reserve = (state.heapLimit / 8).coerceIn(4L * 1024 * 1024,32L * 1024 * 1024)
        val heap = (state.heapLimit - state.heapUsed - reserve).coerceAtLeast(0)
        return minOf(heap, state.systemAvailable?.let { (it - state.systemThreshold).coerceAtLeast(0) } ?: heap)
    }
    fun requireBytes(bytes: Long) {
        require(bytes >= 0)
        if (bytes > available()) throw WorkingMemoryException()
    }
}

object MediaResourcePlan {
    private const val MIB = 1024L * 1024
    private fun multiply(a: Long,b: Long): Long = if (a < 0 || b < 0 || (b != 0L && a > Long.MAX_VALUE/b)) Long.MAX_VALUE else a*b
    private fun add(a: Long,b: Long): Long = if (a > Long.MAX_VALUE-b) Long.MAX_VALUE else a+b
    fun pixels(width: Int,height: Int,copies: Int = 1): Long {
        require(width > 0 && height > 0 && copies > 0)
        return multiply(multiply(width.toLong(),height.toLong()),copies.toLong()*4)
    }
    fun rasterMemory(width: Int,height: Int) = add(pixels(width,height,2),8*MIB)
    /** Source decode/orientation and the destination raster can coexist during composition. */
    fun compositeMemory(sourceMemory: Long, width: Int, height: Int): Long {
        require(sourceMemory >= 0)
        return add(sourceMemory, pixels(width,height,2))
    }
    /** Bound native page retention while allowing a small group to share font subsets. */
    fun presentationPageBatch(availableMemory: Long): Int {
        require(availableMemory >= 0)
        return ((availableMemory - minOf(availableMemory,24*MIB)) / (8*MIB)).coerceIn(1,8).toInt()
    }
    fun rasterDisk(width: Int,height: Int) = add(pixels(width,height),MIB + pixels(width,height)/20)
    fun videoMemory(sourceWidth: Int,sourceHeight: Int,width: Int,height: Int): Long =
        add(add(pixels(sourceWidth,sourceHeight,3),pixels(width,height,5)),16*MIB)
    /** VBR estimate for admission only. Space is measured again while the encoder is running. */
    fun videoDisk(bitrate: Int,audioBitrate: Int,durationMs: Long): Long {
        require(bitrate > 0 && audioBitrate >= 0 && durationMs > 0)
        val bits = multiply(bitrate.toLong()+audioBitrate,durationMs)
        val bytes = if (bits > Long.MAX_VALUE-7999) Long.MAX_VALUE else (bits+7999)/8000
        return add(add(bytes,bytes/4),4*MIB)
    }
    data class Decode(val sample: Int,val width: Int,val height: Int,val targetWidth: Int,val targetHeight: Int) {
        val memory get() = rasterMemory(width,height)
    }
    /** Largest power-of-two sample which still has every pixel required by the selected size. */
    fun decode(width: Int,height: Int,edge: Int): Decode {
        require(width > 0 && height > 0 && edge in 1..4096)
        val ratio = minOf(1.0,edge.toDouble()/maxOf(width,height))
        val targetWidth = maxOf(1,kotlin.math.round(width*ratio).toInt())
        val targetHeight = maxOf(1,kotlin.math.round(height*ratio).toInt())
        var sample = 1
        while (sample <= Int.MAX_VALUE/2 && width/(sample*2) >= targetWidth && height/(sample*2) >= targetHeight) sample *= 2
        return Decode(sample,((width.toLong()+sample-1)/sample).toInt(),((height.toLong()+sample-1)/sample).toInt(),targetWidth,targetHeight)
    }
}
