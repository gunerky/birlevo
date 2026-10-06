package com.example.contentstudio.media

import org.junit.Test
import org.junit.Assert.*

class PdfPagePlanTest {
    @Test fun rangesUseCurrentOrderWithoutAmbiguousDuplicatesOrSilentTruncation() {
        assertEquals(listOf(0, 2, 3, 4), PdfPagePlan.selection("1, 3-5", 7))
        assertEquals(listOf(4, 0), PdfPagePlan.selection("5,1", 5))
        assertEquals(listOf(0, 2, 3, 4), PdfPagePlan.selection("١،٣–٥", 7))
        assertEquals(listOf(0, 2, 3, 4), PdfPagePlan.selection("۱،۳-۵", 7))
        assertEquals(listOf(0, 2, 3, 4), PdfPagePlan.selection("१,३-५", 7))
        for (value in listOf("", "0", "8", "3-1", "1,1", "1-3,2", "1,", "a", "1--2", "999999999999"))
            assertNull(value, PdfPagePlan.selection(value, 7))
        assertNull(PdfPagePlan.selection("1", 2001))
    }
    @Test fun pageReferencesCannotPointOutsideSourceOrUseUnsupportedRotation() {
        PdfPagePlan.validate(listOf(PdfPageRef("a", 1, 2, 270)), listOf(1, 3))
        for (ref in listOf(PdfPageRef("a", -1, 0), PdfPageRef("a", 2, 0), PdfPageRef("a", 1, 3), PdfPageRef("a", 0, 0, 45)))
            assertThrows(IllegalArgumentException::class.java) { PdfPagePlan.validate(listOf(ref), listOf(1, 3)) }
        assertThrows(IllegalArgumentException::class.java) { PdfPagePlan.validate(listOf(PdfPageRef("a", 0, 0), PdfPageRef("a", 0, 0)), listOf(1)) }
    }
}
