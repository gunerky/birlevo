package com.example.contentstudio.archive

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private fun zip(vararg names: String, stored: Boolean = false): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> for (name in names) {
            val data = if (name.endsWith('/')) byteArrayOf() else "original bytes: $name".toByteArray()
            val entry = ZipEntry(name)
            if (stored) { entry.method = 0; entry.size = data.size.toLong(); entry.crc = CRC32().apply { update(data) }.value }
            zip.putNextEntry(entry); zip.write(data); zip.closeEntry()
        } }
    }
    private fun rejected(issue: ArchiveIssue? = null, action: () -> Unit) {
        try { action(); fail("archive accepted") } catch (error: ArchiveRejected) { if (issue != null) assertEquals(issue, error.issue) }
    }
    private fun central(data: ByteArray): Int = data.indices.first { i -> i + 4 <= data.size &&
        data[i] == 0x50.toByte() && data[i+1] == 0x4b.toByte() && data[i+2] == 1.toByte() && data[i+3] == 2.toByte() }
    private fun patch(file: File, action: (ByteArray, Int) -> Unit) { val data = file.readBytes(); action(data, central(data)); file.writeBytes(data) }

    @Test fun storedAndDeflateUnicodeNestedEmptyDirectoriesAndEmptyFilesKeepExactBytes() {
        for (stored in listOf(false, true)) {
            val file = zip("folder/", "folder/çığ-日本.txt", "raw.bin", stored = stored)
            val entries = ZipArchive.inspect(file)
            assertEquals(3, entries.size)
            for (entry in entries.filterNot { it.directory }) {
                val out = ByteArrayOutputStream(); ZipArchive.extract(file, entry, out)
                assertArrayEquals("original bytes: ${entry.path}".toByteArray(), out.toByteArray())
            }
        }
    }
    @Test fun emptyZipIsValid() { assertTrue(ZipArchive.inspect(zip()).isEmpty()) }
    @Test fun unsafePathsAndPortableNameCollisionsAreRejected() {
        for (name in listOf("../a", "/root", "a/../b", "a\\b", "C:/x", "a//b", "a/./b", "NUL.txt", "x.", "x ", "a\u0000b", "a\u202eb")) {
            rejected(ArchiveIssue.PATH) { ZipArchive.inspect(zip(name)) }
        }
        for (names in listOf(arrayOf("A.txt", "a.txt"), arrayOf("é.txt", "e\u0301.txt"), arrayOf("a", "a/b"), arrayOf("x/", "x"))) {
            rejected(ArchiveIssue.PATH) { ZipArchive.inspect(zip(*names)) }
        }
    }
    @Test fun corruptCrcIsDetectedFromActualBytes() {
        val file = zip("file.txt", stored = true)
        patch(file) { data, c -> data[14] = (data[14].toInt() xor 1).toByte(); data[c+16] = data[14] }
        rejected(ArchiveIssue.CORRUPT) { ZipArchive.inspect(file) }
    }
    @Test fun truncatedMismatchedLocalAndOverlappingIndexesFail() {
        val file = zip("one.txt", "two.txt")
        val data = file.readBytes(); file.writeBytes(data.dropLast(1).toByteArray())
        rejected { ZipArchive.inspect(file) }
        val mismatch = zip("name.txt")
        patch(mismatch) { bytes, _ -> bytes[30] = 'X'.code.toByte() }
        rejected { ZipArchive.inspect(mismatch) }
        val overlap = zip("one.txt", "two.txt")
        patch(overlap) { bytes, c ->
            val second = c + 46 + 7
            for (i in 0..3) bytes[second+42+i] = 0
        }
        rejected { ZipArchive.inspect(overlap) }
    }
    @Test fun encryptedZip64UnsupportedMethodsSplitAndSymlinksAreRejected() {
        val changes: List<(ByteArray, Int) -> Unit> = listOf(
            { d,c -> d[c+8] = 1 }, // encryption
            { d,c -> d[c+10] = 99 },
            { d,c -> d[c+6] = 45 }, // ZIP64 version
            { d,c -> for (i in 0..3) d[c+24+i] = 0xff.toByte() },
            { d,c -> d[c+34] = 1 }, // split disk
            { d,c -> d[c+5] = 3; d[c+41] = 0xa0.toByte() }, // UNIX symlink
        )
        for (change in changes) { val file = zip("x"); patch(file, change); rejected(ArchiveIssue.UNSUPPORTED) { ZipArchive.inspect(file) } }
    }
    @Test fun actualExpansionCannotExceedHeaderAndLimitsRejectBeforeInflation() {
        val forged = zip("x")
        patch(forged) { d,c -> for (i in 0..3) d[c+24+i] = 0 }
        rejected(ArchiveIssue.LIMIT) { ZipArchive.inspect(forged) }
        val large = zip("x")
        patch(large) { d,c -> d[c+27] = 9 }
        rejected(ArchiveIssue.LIMIT) { ZipArchive.inspect(large) }
        val many = zip(*(0..ZipArchive.MAX_ENTRIES).map { "$it.txt" }.toTypedArray())
        rejected(ArchiveIssue.LIMIT) { ZipArchive.inspect(many) }
    }
    @Test fun cancellationPropagatesAndSelectionCannotBeForged() {
        val file = zip("one.txt"); var calls = 0
        try { ZipArchive.inspect(file) { if (++calls > 4) throw CancellationException() }; fail() }
        catch (_: CancellationException) { }
        val entry = ZipArchive.inspect(file).single()
        rejected { ZipArchive.extract(file, entry.copy(bytes = 1), ByteArrayOutputStream()) }
        rejected { ZipArchive.extract(file, entry.copy(path = "../x"), ByteArrayOutputStream()) }
    }
    @Test fun metadataIsBoundedBeforePlatformZipAllocationAndWriteFailureIsAccess() {
        val file = zip("x")
        val bytes = file.readBytes()
        val size = ZipArchive.MAX_INDEX + 1
        for (i in 0..3) bytes[bytes.size-10+i] = (size ushr (i*8)).toByte()
        file.writeBytes(bytes)
        rejected(ArchiveIssue.LIMIT) { ZipArchive.inspect(file) }
        val valid = zip("file.txt")
        rejected(ArchiveIssue.ACCESS) {
            ZipArchive.extract(valid, ZipArchive.inspect(valid).single(), object: java.io.OutputStream() {
                override fun write(value: Int) { throw java.io.IOException("synthetic full disk") }
            })
        }
    }
}
