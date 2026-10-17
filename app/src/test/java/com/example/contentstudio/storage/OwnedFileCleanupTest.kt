package com.example.contentstudio.storage

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OwnedFileCleanupTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 20 * OwnedFileCleanup.DAY
    private val names = Regex("output-[0-9]+\\.mp4(?:\\.part)?")
    private fun aged(folder: File, name: String, modified: Long = 1) = File(folder, name).apply {
        writeText(name); check(setLastModified(modified))
    }

    @Test fun removesOnlyKnownOldUnretainedFilesAndNeverMetadataOrDirectories() {
        val root = temporary.newFolder()
        val stale = aged(root, "output-1.mp4.part")
        val keep = aged(root, "output-2.mp4")
        val unknown = aged(root, "output-important.mp4")
        val metadata = aged(root, "session.json.bak")
        val directory = File(root, "output-3.mp4").apply { mkdir() }
        val nested = aged(directory, "output-4.mp4")
        val recent = aged(root, "output-5.mp4", now)
        OwnedFileCleanup.files(root, names, setOf(File(root, "./${keep.name}")), now = now)
        assertFalse(stale.exists())
        listOf(keep, unknown, metadata, directory, nested, recent).forEach { assertTrue(it.path, it.exists()) }
    }

    @Test fun futureAndUnmeasuredTimestampsAndExactBoundaryArePreserved() {
        val root = temporary.newFolder()
        val files = listOf(0L, now - OwnedFileCleanup.DAY, now + 1000).mapIndexed { i, modified ->
            aged(root, "output-$i.mp4", modified)
        }
        OwnedFileCleanup.files(root, names, now = now)
        files.forEach { assertTrue(it.exists()) }
    }

    @Test fun retainedChildProtectsItsWholeFlatSnapshotAndOtherOldSnapshotIsRemoved() {
        val root = temporary.newFolder()
        val kept = File(root, "share-1").apply { mkdir() }
        val old = File(root, "share-2").apply { mkdir() }
        val source = aged(kept, "output-1.mp4")
        aged(old, "output-2.mp4")
        kept.setLastModified(1); old.setLastModified(1)
        OwnedFileCleanup.directories(root, Regex("share-[0-9]+"), names, setOf(source), now = now)
        assertTrue(source.exists()); assertFalse(old.exists())
    }

    @Test fun unknownOrRecentChildrenPreventPartialDirectoryCleanup() {
        val root = temporary.newFolder()
        val snapshots = listOf("share-1", "share-2").map { File(root, it).apply { mkdir() } }
        snapshots.forEach { aged(it, "output-1.mp4") }
        aged(snapshots[0], "unknown.dat")
        aged(snapshots[1], "output-2.mp4", now)
        snapshots.forEach { it.setLastModified(1) }
        OwnedFileCleanup.directories(root, Regex("share-[0-9]+"), names, now = now)
        snapshots.forEach { assertTrue(File(it, "output-1.mp4").exists()); assertEquals(2, it.list()!!.size) }
    }
}
