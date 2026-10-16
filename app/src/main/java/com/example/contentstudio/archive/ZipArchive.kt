package com.example.contentstudio.archive

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import com.example.contentstudio.storage.StreamingLimits

data class ArchiveEntry(val path: String, val bytes: Long, val crc: Long, val directory: Boolean)

enum class ArchiveIssue { INVALID, UNSUPPORTED, PATH, LIMIT, CORRUPT, ACCESS, STORAGE }
class ArchiveRejected(val issue: ArchiveIssue) : IllegalArgumentException(issue.name)

/** ZIP32, stored/deflate only. No archive path is ever resolved as a destination path. */
object ZipArchive {
    const val MAX_SOURCE = StreamingLimits.ZIP_BYTES
    const val MAX_TOTAL = StreamingLimits.CONTENT_BYTES
    const val MAX_FILE = StreamingLimits.CONTENT_BYTES
    const val MAX_ENTRIES = 1000
    const val MAX_INDEX = 8L * 1024 * 1024
    private val cp437 = charset("IBM437")
    private fun gate(ok: Boolean, issue: ArchiveIssue = ArchiveIssue.INVALID) {
        if (!ok) throw ArchiveRejected(issue)
    }

    internal fun safePath(raw: String): String {
        val path = Normalizer.normalize(raw, Normalizer.Form.NFC)
        gate(path.isNotEmpty() && path.length <= 1024 && !path.startsWith('/') &&
            path.none { it == '\\' || it == ':' || it.code < 32 || it.code == 127 || it in "\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069" }, ArchiveIssue.PATH)
        val parts = path.removeSuffix("/").split('/')
        gate(parts.size <= 32 && parts.all { part ->
            part.isNotEmpty() && part != "." && part != ".." && part.length <= 255 &&
                !part.endsWith(' ') && !part.endsWith('.') && part.none { it in "<>\"|?*" } &&
                !part.substringBefore('.').uppercase(Locale.ROOT).matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
        }, ArchiveIssue.PATH)
        return path
    }

    private data class Header(val entry: ArchiveEntry, val raw: String, val offset: Long,
        val compressed: Long, val flags: Int, val method: Int, val name: ByteArray)

    private fun headers(file: File, active: () -> Unit): List<Header> {
        gate(file.length() in 22..MAX_SOURCE, ArchiveIssue.LIMIT)
        RandomAccessFile(file, "r").use { input ->
            fun u16(): Int = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)
            val end = file.length()
            var eocd = -1L
            var at = end - 22
            while (at >= maxOf(0, end - 65557)) {
                active(); input.seek(at)
                if (u32() == 0x06054b50L) {
                    input.seek(at + 20)
                    if (at + 22 + u16() == end) { eocd = at; break }
                }
                at--
            }
            gate(eocd >= 0)
            input.seek(eocd + 4)
            gate(u16() == 0 && u16() == 0, ArchiveIssue.UNSUPPORTED)
            val onDisk = u16(); val count = u16()
            gate(count != 65535 && count == onDisk, ArchiveIssue.UNSUPPORTED)
            gate(count <= MAX_ENTRIES, ArchiveIssue.LIMIT)
            val centralBytes = u32(); val central = u32()
            gate(centralBytes <= MAX_INDEX, ArchiveIssue.LIMIT)
            gate(central + centralBytes == eocd)
            val result = ArrayList<Header>()
            val paths = mutableMapOf<String, Boolean>()
            var total = 0L
            input.seek(central)
            repeat(count) {
                active()
                gate(input.filePointer + 46 <= eocd && u32() == 0x02014b50L)
                val creator = u16(); val version = u16(); val flags = u16(); val method = u16()
                gate(version <= 20 && flags and 0x080e.inv() == 0 && method in setOf(0, 8), ArchiveIssue.UNSUPPORTED)
                u32() // DOS time/date
                val crc = u32(); val compressed = u32(); val size = u32()
                val nameLength = u16(); val extra = u16(); val comment = u16()
                gate(u16() == 0, ArchiveIssue.UNSUPPORTED)
                u16(); val attributes = u32(); val offset = u32()
                gate(size != 0xffffffffL && compressed != 0xffffffffL && offset != 0xffffffffL, ArchiveIssue.UNSUPPORTED)
                gate(size <= MAX_FILE && compressed <= MAX_SOURCE, ArchiveIssue.LIMIT)
                gate(nameLength in 1..4096 && input.filePointer + nameLength + extra + comment <= eocd)
                val name = ByteArray(nameLength).also { input.readFully(it) }
                val raw = (if (flags and 0x800 != 0) Charsets.UTF_8 else cp437).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(name)).toString()
                val path = safePath(raw); val directory = path.endsWith('/')
                val mode = ((attributes ushr 16) and 0xf000).toInt()
                gate(creator ushr 8 != 3 || mode == 0 || mode == (if (directory) 0x4000 else 0x8000), ArchiveIssue.UNSUPPORTED)
                gate(attributes and 0x10L == 0L || directory, ArchiveIssue.UNSUPPORTED)
                gate(!directory || size == 0L)
                gate(method != 0 || compressed == size)
                val key = path.removeSuffix("/").lowercase(Locale.ROOT)
                gate(paths.put(key, directory) == null, ArchiveIssue.PATH)
                total += size; gate(total <= MAX_TOTAL, ArchiveIssue.LIMIT)
                result.add(Header(ArchiveEntry(path, size, crc, directory), raw, offset, compressed, flags, method, name))
                input.seek(input.filePointer + extra + comment)
            }
            gate(input.filePointer == eocd)
            for ((path, _) in paths) {
                var parent = path.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    gate(paths[parent] != false, ArchiveIssue.PATH)
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            var previousEnd = 0L
            for (header in result.sortedBy { it.offset }) {
                active(); gate(header.offset >= previousEnd && header.offset + 30 <= central)
                input.seek(header.offset); gate(u32() == 0x04034b50L)
                gate(u16() <= 20 && u16() == header.flags && u16() == header.method)
                u32(); val crc = u32(); val compressed = u32(); val size = u32()
                if (header.flags and 8 == 0) gate(crc == header.entry.crc && compressed == header.compressed && size == header.entry.bytes)
                val length = u16(); val extra = u16()
                gate(length == header.name.size && input.filePointer + length + extra <= central)
                gate(ByteArray(length).also { input.readFully(it) }.contentEquals(header.name))
                previousEnd = input.filePointer + extra + header.compressed
                gate(previousEnd <= central)
            }
            return result
        }
    }

    /** Checks the complete central/local index and actual inflated bytes + CRC before publishing. */
    fun inspect(file: File, active: () -> Unit = {}): List<ArchiveEntry> = checked {
        val headers = headers(file, active)
        ZipFile(file, cp437).use { zip ->
            gate(zip.size() == headers.size)
            for (header in headers) {
                active()
                val entry = zip.getEntry(header.raw) ?: throw ArchiveRejected(ArchiveIssue.INVALID)
                zip.getInputStream(entry).use { input ->
                    copyVerified(input, null, header.entry, active)
                }
            }
        }
        headers.map { it.entry }
    }

    /** Metadata only for an already verified private snapshot; never presented as verification. */
    internal fun index(file: File, active: () -> Unit): List<ArchiveEntry> = checked {
        headers(file, active).map { it.entry }
    }

    /** Writes only bytes, never directory names or links. Caller supplies a newly owned file. */
    fun extract(file: File, selected: ArchiveEntry, output: OutputStream, active: () -> Unit = {}) = checked {
        gate(!selected.directory)
        val header = headers(file, active).singleOrNull { it.entry == selected }
            ?: throw ArchiveRejected(ArchiveIssue.INVALID)
        ZipFile(file, cp437).use { zip ->
            val entry = zip.getEntry(header.raw) ?: throw ArchiveRejected(ArchiveIssue.INVALID)
            zip.getInputStream(entry).use { copyVerified(it, output, selected, active) }
        }
    }

    /** One validated index and ZipFile for the whole selection; the caller owns each staged output. */
    internal fun extractMany(file: File, selected: List<ArchiveEntry>, active: () -> Unit = {},
        output: (ArchiveEntry, (OutputStream) -> Unit) -> Unit) = checked {
        gate(selected.isNotEmpty() && selected.size <= MAX_ENTRIES && selected.distinct().size == selected.size)
        gate(selected.none { it.directory })
        val available = headers(file, active).associateBy { it.entry }
        val requested = selected.map { available[it] ?: throw ArchiveRejected(ArchiveIssue.INVALID) }
        ZipFile(file, cp437).use { zip ->
            for (header in requested) {
                active()
                output(header.entry) { out -> checked {
                    val entry = zip.getEntry(header.raw) ?: throw ArchiveRejected(ArchiveIssue.INVALID)
                    zip.getInputStream(entry).use { copyVerified(it, out, header.entry, active) }
                } }
            }
        }
    }

    private fun copyVerified(input: java.io.InputStream, output: OutputStream?, entry: ArchiveEntry, active: () -> Unit) {
        val buffer = ByteArray(65536); val crc = CRC32(); var bytes = 0L
        while (true) {
            active(); val count = input.read(buffer); if (count < 0) break
            bytes += count
            gate(bytes <= entry.bytes && bytes <= MAX_FILE, ArchiveIssue.LIMIT)
            crc.update(buffer, 0, count)
            try { output?.write(buffer, 0, count) }
            catch (error: com.example.contentstudio.storage.StorageCapacityException) { throw error }
            catch (error: java.io.IOException) { throw ArchiveRejected(ArchiveIssue.ACCESS).apply { initCause(error) } }
        }
        gate(bytes == entry.bytes && crc.value == entry.crc, ArchiveIssue.CORRUPT)
    }

    private inline fun <T> checked(block: () -> T): T = try { block() }
        catch (error: ArchiveRejected) { throw error }
        catch (error: com.example.contentstudio.storage.StorageCapacityException) { throw error }
        catch (error: java.io.IOException) { throw ArchiveRejected(ArchiveIssue.CORRUPT).apply { initCause(error) } }
}
