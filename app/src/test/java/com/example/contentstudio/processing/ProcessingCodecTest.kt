package com.example.contentstudio.processing

import org.junit.Test
import org.junit.Assert.*

class ProcessingCodecTest {
    private fun record(status: ProcessingStatus) = ProcessingRecord("job-1", ProcessingKind.VIDEO,
        ProcessingCodec.fingerprint("immutable input/settings")!!, status,
        output = if (status == ProcessingStatus.SUCCEEDED) ProcessingOutput("output-valid.mp4", 100, elapsedMs = 200) else null)
    @Test fun activeJobsRecoverAsInterruptedWithoutAutomaticExecution() {
        for (status in listOf(ProcessingStatus.QUEUED,ProcessingStatus.RUNNING))
            assertEquals(ProcessingStatus.INTERRUPTED,ProcessingCodec.decode(ProcessingCodec.encode(listOf(record(status)))).getValue(ProcessingKind.VIDEO).status)
    }
    @Test fun terminalRecordsRoundTripWithoutProgressOrConsent() {
        for (status in ProcessingStatus.entries.filterNot { it.active }) {
            val original=record(status)
            assertEquals(original,ProcessingCodec.decode(ProcessingCodec.encode(listOf(original))).getValue(ProcessingKind.VIDEO))
        }
        assertFalse(ProcessingCodec.encode(listOf(record(ProcessingStatus.SUCCEEDED))).contains("approval"))
    }
    @Test fun invalidOutputPathsDuplicateKindsAndForeignVersionsAreRejected() {
        val original=record(ProcessingStatus.SUCCEEDED)
        for (bad in listOf("../output-escape.mp4","output-evil/path.mp4","source-original.mp4")) {
            assertThrows(IllegalArgumentException::class.java) { ProcessingCodec.decode(ProcessingCodec.encode(listOf(original.copy(output=ProcessingOutput(bad,100))))) }
        }
        assertThrows(IllegalArgumentException::class.java) { ProcessingCodec.decode(ProcessingCodec.encode(listOf(original,original))) }
        assertThrows(IllegalArgumentException::class.java) { ProcessingCodec.decode(ProcessingCodec.encode(listOf(original)).replace("\"version\":1","\"version\":99")) }
    }
    @Test fun fingerprintIncludesSettingsAndNeverStoresRawSourceUris() {
        val value="content://private-original/secret-name?quality=HIGH"
        val first=ProcessingCodec.fingerprint(value)!!
        assertEquals(64,first.length); assertEquals(first,ProcessingCodec.fingerprint(value))
        assertNotEquals(first,ProcessingCodec.fingerprint(value+"-changed"))
        assertNull(ProcessingCodec.fingerprint(null))
        assertFalse(ProcessingCodec.encode(listOf(record(ProcessingStatus.RUNNING).copy(request=first))).contains("private-original"))
    }
}
