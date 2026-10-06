package com.example.contentstudio.media

import org.junit.Assert.*
import org.junit.Test

class PdfReaderPolicyTest {
    @Test fun extremePdfGeometryCannotOverflowRasterAllocation() {
        assertEquals(4096 to 4096, PdfReaderPolicy.rasterSize(Int.MAX_VALUE, Int.MAX_VALUE, 4096))
        assertEquals(1 to 4096, PdfReaderPolicy.rasterSize(1, Int.MAX_VALUE, 4096))
        assertEquals(4096 to 1, PdfReaderPolicy.rasterSize(Int.MAX_VALUE, 1, 4096))
        assertEquals(1280 to 640, PdfReaderPolicy.rasterSize(400, 200, 1280))
        assertEquals(PdfReadIssue.INVALID, assertThrows(PdfReadException::class.java) { PdfReaderPolicy.rasterSize(0, 200, 1280) }.issue)
    }
    @Test fun emptyAndOverLimitDocumentsAreNotAdvertisedAsLoaded() {
        assertEquals(PdfReadIssue.INVALID, assertThrows(PdfReadException::class.java) { PdfReaderPolicy.validatePageCount(0) }.issue)
        assertEquals(PdfReadIssue.LIMIT, assertThrows(PdfReadException::class.java) { PdfReaderPolicy.validatePageCount(2001) }.issue)
        PdfReaderPolicy.validatePageCount(2000)
    }
}
