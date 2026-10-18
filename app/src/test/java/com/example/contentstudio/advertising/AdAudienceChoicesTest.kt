package com.example.contentstudio.advertising

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdAudienceChoicesTest {
    @Test fun missingUnknownAndExcludedAgesFailClosed() {
        for (stored in listOf(null, "", "obsolete", "UNDER_13", "UNDISCLOSED"))
            assertEquals(AdAgeTreatment.BLOCKED, AdAudienceGroup.restore(stored).treatment)
        assertEquals(AdAgeTreatment.PROTECTED, AdAudienceGroup.AGE_13_15.treatment)
        assertEquals(AdAgeTreatment.PROTECTED, AdAudienceGroup.AGE_16_17.treatment)
        assertEquals(AdAgeTreatment.ADULT, AdAudienceGroup.ADULT.treatment)
    }

    @Test fun changingAnAdultChoiceRestrictsBeforeDurableWriteCompletes() = runBlocking {
        val write = CompletableDeferred<Boolean>()
        var restrictions = 0
        val choices = AdAudienceChoices("ADULT", this, { write.await() }, { restrictions++ })
        choices.choose(AdAudienceGroup.AGE_13_15)
        assertEquals(1, restrictions)
        assertEquals(AdAgeTreatment.BLOCKED, choices.state.value.group.treatment)
        assertTrue(choices.state.value.saving)
        yield(); write.complete(true); yield()
        assertEquals(AdAudienceGroup.AGE_13_15, choices.state.value.group)
        assertFalse(choices.state.value.saving)
    }

    @Test fun failedPersistenceCannotRestoreAnOlderAdultChoice() = runBlocking {
        val choices = AdAudienceChoices("ADULT", this, { false }, {})
        choices.choose(AdAudienceGroup.UNDISCLOSED); yield()
        assertEquals(AdAgeTreatment.BLOCKED, choices.state.value.group.treatment)
        assertTrue(choices.state.value.saveFailed)
    }

    @Test fun rapidChangesHaveOneWriterAndNeverPublishAnObsoleteAdultResult() = runBlocking {
        val firstWrite = CompletableDeferred<Boolean>()
        val writes = mutableListOf<AdAudienceGroup>()
        val choices = AdAudienceChoices(null, this, {
            writes += it
            if (writes.size == 1) firstWrite.await() else true
        }, {})
        choices.choose(AdAudienceGroup.ADULT); yield()
        choices.choose(AdAudienceGroup.AGE_16_17); yield()
        assertEquals(listOf(AdAudienceGroup.ADULT), writes)
        firstWrite.complete(true); yield(); yield()
        assertEquals(listOf(AdAudienceGroup.ADULT, AdAudienceGroup.AGE_16_17), writes)
        assertEquals(AdAudienceGroup.AGE_16_17, choices.state.value.group)
    }

    @Test fun supersededUnstartedWritesAreSkippedAndFailuresCanBeRetried() = runBlocking {
        val writes = mutableListOf<AdAudienceGroup>()
        var healthy = false
        val choices = AdAudienceChoices(null, this, { writes += it; healthy }, {})
        choices.choose(AdAudienceGroup.ADULT)
        choices.choose(AdAudienceGroup.UNDER_13); yield()
        assertEquals(listOf(AdAudienceGroup.UNDER_13), writes)
        assertTrue(choices.state.value.saveFailed)
        healthy = true; choices.choose(AdAudienceGroup.AGE_13_15); yield()
        assertEquals(AdAudienceGroup.AGE_13_15, choices.state.value.group)
        assertFalse(choices.state.value.saveFailed)
    }
}
