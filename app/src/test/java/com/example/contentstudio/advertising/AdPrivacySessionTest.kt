package com.example.contentstudio.advertising

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdPrivacySessionTest {
    private class Consent : AdConsentGateway<Unit> {
        var allowed = false
        var required = true
        var updates = 0
        var forms = 0
        var edits = 0
        var freshOptions = true
        var update: suspend () -> Unit = {}
        var collect: suspend () -> Unit = {}
        var edit: suspend () -> Unit = { allowed = false }
        override suspend fun refresh(host: Unit) { updates++; update() }
        override suspend fun collectIfRequired(host: Unit) { forms++; collect() }
        override suspend fun showOptions(host: Unit) { edits++; edit() }
        override fun canRequestAds() = allowed
        override fun optionsRequired() = required
        override fun canShowOptions() = freshOptions && required
    }

    @Test fun disabledBuildNeverContactsProvider() = runBlocking {
        val consent = Consent().apply { allowed = true }
        val session = AdPrivacySession(false, consent, { fail("SDK initialized") }, this)
        session.refresh(Unit); session.showOptions(Unit); yield()
        assertEquals(0, consent.updates); assertFalse(session.canRequestAds())
        assertEquals(AdPrivacyPhase.DISABLED, session.state.value.phase)
    }

    @Test fun unknownOrIneligibleConsentNeverInitializesSdk() = runBlocking {
        val consent = Consent()
        val session = AdPrivacySession(true, consent, { fail("SDK initialized") }, this)
        assertFalse(session.canRequestAds()); session.refresh(Unit); yield()
        assertEquals(1, consent.forms); assertFalse(session.canRequestAds())
    }

    @Test fun currentProviderConsentGatesInitializationAndEveryEligibilityCheck() = runBlocking {
        val consent = Consent()
        var initialized = 0
        consent.collect = { consent.allowed = true }
        val session = AdPrivacySession(true, consent, { initialized++ }, this)
        session.refresh(Unit); yield()
        assertEquals(1, initialized); assertTrue(session.canRequestAds())
        consent.allowed = false
        assertFalse(session.canRequestAds())
    }

    @Test fun updateFailureDoesNotTrustOldConsentAndRetryWorks() = runBlocking {
        val consent = Consent().apply { allowed = true; update = { error("offline") } }
        var initialized = 0
        val session = AdPrivacySession(true, consent, { initialized++ }, this)
        session.refresh(Unit); yield()
        assertEquals(AdPrivacyPhase.UNAVAILABLE, session.state.value.phase)
        assertEquals(0, initialized); assertFalse(session.canRequestAds())
        consent.update = {}; session.refresh(Unit); yield()
        assertTrue(session.canRequestAds()); assertEquals(1, initialized)
    }

    @Test fun slowProviderTimesOutAndRepeatedTapsDoNotDuplicateRequests() = runBlocking {
        val consent = Consent().apply { update = { delay(10_000) } }
        val session = AdPrivacySession(true, consent, { fail("SDK initialized") }, this, 30)
        session.refresh(Unit); session.refresh(Unit); delay(80)
        assertEquals(1, consent.updates)
        assertEquals(AdPrivacyPhase.UNAVAILABLE, session.state.value.phase)
        assertFalse(session.canRequestAds())
    }

    @Test fun closingActivityInvalidatesEvenNonCancellableOldCompletion() = runBlocking {
        val old = CompletableDeferred<Unit>()
        val consent = Consent().apply { update = { withContext(NonCancellable) { old.await() } } }
        val session = AdPrivacySession(true, consent, { fail("SDK initialized") }, this)
        session.refresh(Unit); yield(); session.detach()
        old.complete(Unit); yield(); yield()
        assertEquals(AdPrivacyPhase.IDLE, session.state.value.phase)
        assertFalse(session.canRequestAds())
    }

    @Test fun changingChoicesImmediatelySuspendsEligibilityAndKeepsEntryAfterFailure() = runBlocking {
        val consent = Consent().apply { allowed = true }
        val session = AdPrivacySession(true, consent, {}, this)
        session.refresh(Unit); yield(); assertTrue(session.canRequestAds())
        consent.edit = { error("form failed") }
        session.showOptions(Unit); assertFalse(session.canRequestAds()); yield()
        assertTrue(session.state.value.optionsRequired); assertFalse(session.canRequestAds())
        consent.edit = { consent.allowed = false }
        session.showOptions(Unit); yield()
        assertEquals(2, consent.edits); assertFalse(session.canRequestAds())
    }

    @Test fun formOrInitializationFailureDoesNotEnableAds() = runBlocking {
        val consent = Consent().apply { allowed = true; collect = { error("form") } }
        val session = AdPrivacySession(true, consent, { error("init") }, this)
        session.refresh(Unit); yield(); assertFalse(session.canRequestAds())
        consent.collect = {}; session.refresh(Unit); yield()
        assertEquals(AdPrivacyPhase.UNAVAILABLE, session.state.value.phase)
        assertFalse(session.canRequestAds())
    }

    @Test fun nonRequiredOptionsCannotBeOpened() = runBlocking {
        val consent = Consent().apply { required = false; allowed = true }
        val session = AdPrivacySession(true, consent, {}, this)
        session.refresh(Unit); yield(); session.showOptions(Unit); yield()
        assertEquals(0, consent.edits); assertTrue(session.canRequestAds())
    }

    @Test fun changedAgePolicyCannotOpenAnOldOptionsForm() = runBlocking {
        val consent = Consent().apply { allowed = true }
        val session = AdPrivacySession(true, consent, {}, this)
        session.refresh(Unit); yield()
        consent.freshOptions = false
        session.showOptions(Unit); yield()
        assertEquals(2, consent.updates)
        assertEquals(0, consent.edits)
    }

    @Test fun readingDisplayedFormHasNoNetworkDeadline() = runBlocking {
        val consent = Consent().apply { collect = { delay(100); allowed = true } }
        val session = AdPrivacySession(true, consent, {}, this, 30)
        session.refresh(Unit); delay(60)
        assertEquals(AdPrivacyPhase.CHECKING, session.state.value.phase)
        assertFalse(session.canRequestAds()); delay(80)
        assertTrue(session.canRequestAds())
    }
}
