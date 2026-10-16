package com.example.contentstudio.batch

import com.example.contentstudio.documents.*
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchDocumentPolicyTest {
    private fun row(meta: BatchDocument = BatchDocument(DocumentKind.TEXT)) = BatchItem(UUID.randomUUID().toString(),
        "source-123.bin", "content://synthetic/document", "a.txt", 100, 0, 0, document = meta)
    private fun queue(item: BatchItem = row(), options: BatchOptions = BatchOptions(kind = BatchKind.DOCUMENT)) = BatchQueue(options = options, items = listOf(item))
    @Test fun allTargetsEncodingsAndSeparatorsRoundTripAsVersionThree() {
        for(target in DocumentTarget.entries) for(encoding in DocumentEncoding.entries) for(separator in listOf(null, ',', ';', '\t')) {
            val batch = queue(options = BatchOptions(kind = BatchKind.DOCUMENT, documentTarget = target, documentEncoding = encoding, documentDelimiter = separator))
            val json = BatchPolicy.encode(batch)
            assertEquals(3, JSONObject(json).getInt("version")); assertEquals(batch, BatchPolicy.decode(json))
            assertFalse(json.contains("approved", ignoreCase = true))
        }
    }
    @Test fun officeLossApprovalAndActualLargerCopyApprovalAreIndependentAndExact() {
        val meta = BatchDocument(DocumentKind.TEXT, OfficeFormat.DOCX)
        val item = row(meta).copy(output = "output-123.pdf", outputBytes = 100)
        val batch = queue(item); val loss = setOf(meta.key(item, batch.options)); val copy = setOf(item.copyKey!!)
        assertThrows(Exception::class.java) { BatchPolicy.requireExportable(batch, copy) }
        assertThrows(Exception::class.java) { BatchPolicy.requireExportable(batch, emptySet(), loss) }
        assertEquals(listOf(item), BatchPolicy.requireExportable(batch, copy, loss))
        val changed = item.copy(output = "output-456.pdf")
        assertThrows(Exception::class.java) { BatchPolicy.requireExportable(batch.copy(items = listOf(changed)), copy, loss) }
    }
    @Test fun consentBindsWorkbookSelectionFormulaModeAndRecipeButNotPeerSelection() {
        val meta = BatchDocument(DocumentKind.CSV, OfficeFormat.XLSX, listOf("First","Second"), listOf(0,1), sheet = 0)
        val item = row(meta); val options = BatchOptions(kind = BatchKind.DOCUMENT)
        val key = meta.key(item, options)
        assertNotEquals(key, meta.copy(sheet = 1).key(item, options))
        assertNotEquals(key, meta.copy(mode = OfficeCellMode.FORMULAS).key(item, options))
        assertNotEquals(key, meta.key(item, options.copy(documentTarget = DocumentTarget.CSV)))
        assertNotEquals(key, meta.key(item, options.copy(documentEncoding = DocumentEncoding.UTF16LE)))
        assertNotEquals(key, meta.key(item, options.copy(documentDelimiter = ';')))
        assertEquals(key, meta.key(item.copy(included = false), options))
    }
    @Test fun sourceLimitsStayFourMiBWhilePdfOutputsCanBeLargerAndEmptyTextIsValid() {
        val large = row().copy(output = "output-123.pdf", outputBytes = 8L*1024*1024)
        val largeQueue = queue(large)
        assertEquals(largeQueue, BatchPolicy.decode(BatchPolicy.encode(largeQueue)))
        val empty = row().copy(output = "output-empty.txt", outputBytes = 0)
        val text = queue(empty, BatchOptions(kind = BatchKind.DOCUMENT, documentTarget = DocumentTarget.TXT))
        assertEquals(text, BatchPolicy.decode(BatchPolicy.encode(text)))
        assertEquals(1, BatchPolicy.requireExportable(text, emptySet()).size)
        assertThrows(Exception::class.java) { BatchPolicy.decode(BatchPolicy.encode(queue(row().copy(bytes = DocumentPolicy.MAX_BYTES+1)))) }
    }
    @Test fun invalidSheetChoicesMissingFactsAndIncompatibleCompletedTargetsCannotRestore() {
        val meta = BatchDocument(DocumentKind.CSV, OfficeFormat.XLSX, listOf("A","B"), listOf(0,1), sheet = -1)
        val encoded = BatchPolicy.encode(queue(row(meta)))
        val mutations: List<(JSONObject)->Unit> = listOf(
            {it.put("version",2)}, {it.getJSONArray("items").getJSONObject(0).remove("document")},
            {it.getJSONArray("items").getJSONObject(0).getJSONObject("document").put("sheet",2)},
            {it.getJSONArray("items").getJSONObject(0).getJSONObject("document").getJSONArray("missing").put(0,-1)},
            {it.getJSONArray("items").getJSONObject(0).put("output","output-1.pdf").put("outputBytes",10)},
        )
        mutations.forEach { change -> assertThrows(Exception::class.java) { BatchPolicy.decode(JSONObject(encoded).also(change).toString()) } }
        val wrong = queue(row().copy(output = "output-1.xlsx", outputBytes = 10), BatchOptions(kind = BatchKind.DOCUMENT, documentTarget = DocumentTarget.XLSX))
        assertThrows(Exception::class.java) { BatchPolicy.decode(BatchPolicy.encode(wrong)) }
    }
    @Test fun oldMediaSchemaRemainsUnchangedAndDocumentIssuesCannotLeakAcrossKinds() {
        val old = BatchQueue()
        assertEquals(1, JSONObject(BatchPolicy.encode(old)).getInt("version"))
        assertFalse(BatchPolicy.encode(old).contains("documentTarget"))
        assertEquals(old, BatchPolicy.decode(BatchPolicy.encode(old)))
        val doc = row().copy(issue = com.example.contentstudio.media.ImportIssue.INVALID_MEDIA, documentIssue = BatchDocumentIssue.ENCODING)
        val documentQueue = queue(doc)
        assertEquals(documentQueue, BatchPolicy.decode(BatchPolicy.encode(documentQueue)))
    }
}
