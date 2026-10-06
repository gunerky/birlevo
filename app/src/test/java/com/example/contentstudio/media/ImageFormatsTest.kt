package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class ImageFormatsTest {
    private fun extended(flag: Int) = ByteArray(21).also {
        "RIFF".toByteArray().copyInto(it); "WEBP".toByteArray().copyInto(it, 8)
        "VP8X".toByteArray().copyInto(it, 12); it[20] = flag.toByte()
    }
    @Test fun animatedWebpIsRejectedWhileAlphaOnlyIsAllowed() {
        ImageFormats.requireStillWebp(extended(16).inputStream())
        assertThrows(IllegalArgumentException::class.java) { ImageFormats.requireStillWebp(extended(18).inputStream()) }
    }
    @Test fun truncatedOrNonWebpInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImageFormats.requireStillWebp(byteArrayOf(1, 2).inputStream()) }
        assertThrows(IllegalArgumentException::class.java) { ImageFormats.requireStillWebp(ByteArray(21).inputStream()) }
    }
    @Test fun outputContractUsesCorrectMimeAndExtension() {
        assertEquals("image/webp", ImageOutputFormat.WEBP.mime)
        assertEquals("png", ImageFormats.extension("image/png"))
        assertFalse(ImageOutputFormat.PNG.usesQuality)
        assertTrue(ImageOutputFormat.JPEG.usesQuality && ImageOutputFormat.WEBP.usesQuality)
    }
    private fun ftyp(major: String, vararg compatible: String): ByteArray = java.nio.ByteBuffer
        .allocate(16 + compatible.size * 4).putInt(16 + compatible.size * 4)
        .put("ftyp".toByteArray()).put(major.toByteArray()).putInt(0)
        .also { buffer -> compatible.forEach { buffer.put(it.toByteArray()) } }.array()

    @Test fun stillHeifAndAvifAreDetectedFromCompatibleBrands() {
        assertEquals("image/heif", ImageFormats.modernMime(ftyp("mif1", "heic").inputStream()))
        assertEquals("image/avif", ImageFormats.modernMime(ftyp("avif", "mif1").inputStream()))
        assertNull(ImageFormats.modernMime(ftyp("isom", "mp42").inputStream()))
        assertNull(ImageFormats.modernMime("a JPEG filename is not evidence".byteInputStream()))
        assertEquals("heif", ImageFormats.extension("image/heif"))
        assertEquals("avif", ImageFormats.extension("image/avif"))
    }
    @Test fun sequencesAndInvalidBoxSizesAreRejectedBeforeDecoding() {
        for (brand in listOf("avis", "msf1", "hevc", "hevx")) {
            assertEquals(ImportIssue.IMAGE_ANIMATED, assertThrows(MediaImportException::class.java) {
                ImageFormats.modernMime(ftyp("mif1", brand).inputStream())
            }.issue)
        }
        for (size in listOf(0, 1, 12, 17, 8192, Int.MAX_VALUE)) {
            val header = ftyp("avif").also { java.nio.ByteBuffer.wrap(it).putInt(size) }
            assertEquals(ImportIssue.INVALID_MEDIA, assertThrows(MediaImportException::class.java) {
                ImageFormats.modernMime(header.inputStream())
            }.issue)
        }
        assertThrows(MediaImportException::class.java) { ImageFormats.modernMime(ftyp("avif", "mif1").copyOf(18).inputStream()) }
    }
}
