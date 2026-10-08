package com.example.contentstudio

import com.example.contentstudio.ui.*
import org.junit.Assert.*
import org.junit.Test

class FileActionsTest {
    @Test fun namesAndProviderAliasesChooseRelevantOperationsOnly() {
        val samples = mapOf("PHOTO.HEIC" to FileCategory.IMAGE, "picture.bmp" to FileCategory.IMAGE,
            "clip.mkv" to FileCategory.VIDEO, "recording.flac" to FileCategory.AUDIO,
            "pages.pdf" to FileCategory.PDF, "table.tsv" to FileCategory.TEXT)
        samples.forEach { (name, expected) -> assertEquals(expected, FileActions.category("application/octet-stream", name)) }
        assertEquals(FileCategory.TEXT, FileActions.category("application/vnd.ms-excel", "table.csv"))
        assertEquals(FileCategory.AUDIO, FileActions.category("audio/x-wav", "unknown"))
        assertEquals(FileCategory.IMAGE, FileActions.category("image/jpeg; charset=binary", "unknown"))
        assertEquals(listOf(FileAction.VIDEO, FileAction.AUDIO, FileAction.FRAME), FileActions.actions(FileCategory.VIDEO))
        assertEquals(listOf(FileAction.PDF_READER, FileAction.PDF_PAGES, FileAction.PDF_IMAGE), FileActions.actions(FileCategory.PDF))
    }
    @Test fun conflictingOrUnsupportedHintsDoNotAdvertiseASupportedFile() {
        assertNull(FileActions.category("application/vnd.ms-excel", "table.xlsx"))
        assertNull(FileActions.category("video/mp4", "photo.png"))
        assertNull(FileActions.category("application/zip", "archive.zip"))
        assertNull(FileActions.category(null, "unknown"))
    }
}
