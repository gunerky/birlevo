package com.example.contentstudio.media

import java.io.InputStream

enum class ImageOutputFormat(val mime: String, val extension: String, val usesQuality: Boolean) {
    JPEG("image/jpeg", "jpg", true), PNG("image/png", "png", false), WEBP("image/webp", "webp", true)
}

/** Shared import contract for conversion, slides and photo PDFs. */
object ImageFormats {
    val inputMimes get() = FormatSupport.imageMimes.toTypedArray()
    fun extension(mime: String): String = when (mime) {
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/avif" -> "avif"
        else -> ImageOutputFormat.entries.first { it.mime == mime }.extension
    }

    /** Bounded ISO BMFF brand check. Provider MIME and filename are not trusted. */
    fun modernMime(input: InputStream): String? {
        val head = readPrefix(input, 16)
        if (head.size < 8 || String(head, 4, 4, Charsets.US_ASCII) != "ftyp") return null
        val size = head.take(4).fold(0L) { n, byte -> (n shl 8) or (byte.toLong() and 255) }
        if (size !in 16..4096 || size % 4 != 0L || head.size != 16) throw MediaImportException(ImportIssue.INVALID_MEDIA)
        val rest = readPrefix(input, size.toInt() - 16)
        if (rest.size != size.toInt() - 16) throw MediaImportException(ImportIssue.INVALID_MEDIA)
        val brands = listOf(String(head, 8, 4, Charsets.US_ASCII)) + rest.toList().chunked(4).map {
            String(it.toByteArray(), Charsets.US_ASCII)
        }
        if (brands.any { it in setOf("msf1", "hevc", "hevx", "avis") }) throw MediaImportException(ImportIssue.IMAGE_ANIMATED)
        return when {
            "avif" in brands -> "image/avif"
            brands.any { it in setOf("heic", "heix", "mif1") } -> "image/heif"
            else -> null
        }
    }

    private fun readPrefix(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var count = 0
        while (count < length) {
            val read = input.read(bytes, count, length - count)
            if (read < 0) break
            if (read == 0) { val byte = input.read(); if (byte < 0) break; bytes[count++] = byte.toByte() }
            else count += read
        }
        return bytes.copyOf(count)
    }

    fun requireStillWebp(input: InputStream) {
        val header = ByteArray(21)
        var count = 0
        while (count < header.size) {
            val read = input.read(header, count, header.size - count)
            if (read < 0) break
            count += read
        }
        if (count < 20 || String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, Charsets.US_ASCII) != "WEBP") throw MediaImportException(ImportIssue.INVALID_MEDIA)
        // In an extended WebP, VP8X is the first chunk. Its animation flag is bit 1.
        if (String(header, 12, 4, Charsets.US_ASCII) == "VP8X") {
            if (count != 21) throw MediaImportException(ImportIssue.INVALID_MEDIA)
            if ((header[20].toInt() and 2) != 0) throw MediaImportException(ImportIssue.IMAGE_ANIMATED)
        }
    }
}
