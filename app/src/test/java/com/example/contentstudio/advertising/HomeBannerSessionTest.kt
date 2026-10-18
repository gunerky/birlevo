package com.example.contentstudio.advertising

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HomeBannerSessionTest {
    private fun CoroutineScope.session(eligible: () -> Boolean, reserve: () -> Boolean, timeout: Long = 15_000) =
        HomeBannerSession(this, eligible, reserve, timeout, Dispatchers.Unconfined)
    private class Handle : BannerHandle {
        var loaded: () -> Unit = {}
        var failed: () -> Unit = {}
        var requests = 0
        var visible = false
        var destroyed = false
        override fun load(loaded: () -> Unit, failed: () -> Unit) {
            requests++; this.loaded = loaded; this.failed = failed
        }
        override fun reveal() { check(!destroyed); visible = true }
        override fun destroy() { destroyed = true; visible = false }
    }

    @Test fun unknownConsentCannotLoadOrConsumeAttempt() = runBlocking {
        val handle = Handle()
        val session = session({ false }, { fail("Reserved without consent"); true })
        session.start(handle)
        assertEquals(0, handle.requests); assertTrue(handle.destroyed)
    }

    @Test fun revokedConsentBetweenLoadAndCallbackCannotShow() = runBlocking {
        var allowed = true
        val session = session({ allowed }, { true })
        val handle = Handle(); session.start(handle)
        allowed = false; handle.loaded(); yield()
        assertTrue(handle.destroyed); assertFalse(handle.visible)
        assertEquals(BannerPhase.UNAVAILABLE, session.phase.value)
    }

    @Test fun navigationOrBackgroundDestroysBannerAndRejectsLateCompletion() = runBlocking {
        val session = session({ true }, { true })
        val handle = Handle(); session.start(handle); session.stop()
        handle.loaded(); yield()
        assertTrue(handle.destroyed); assertFalse(handle.visible)
        assertEquals(BannerPhase.IDLE, session.phase.value)
    }

    @Test fun oldFailureCannotRemoveNewAuthorizedBanner() = runBlocking {
        val session = session({ true }, { true })
        val old = Handle(); session.start(old)
        val current = Handle(); session.start(current)
        old.failed(); current.loaded(); yield()
        assertTrue(old.destroyed); assertTrue(current.visible)
        assertEquals(BannerPhase.VISIBLE, session.phase.value)
        session.stop()
    }

    @Test fun slowLoadExpiresWithoutAutomaticRetry() = runBlocking {
        val session = session({ true }, { true }, 20)
        val handle = Handle(); session.start(handle); delay(80)
        assertEquals(1, handle.requests); assertTrue(handle.destroyed)
        handle.loaded(); yield(); assertFalse(handle.visible)
        assertEquals(BannerPhase.UNAVAILABLE, session.phase.value)
    }

    @Test fun failedAttemptCannotBeRetriedByRecreatingSession() = runBlocking {
        var time = 1_000L
        var saved: Long? = null
        fun limiter() = BannerAttemptLimiter({ time }, { saved }, { saved = it })
        val first = Handle()
        session({ true }, limiter()::reserve).start(first)
        first.failed(); yield()
        val retry = Handle()
        session({ true }, limiter()::reserve).start(retry)
        assertEquals(0, retry.requests); assertTrue(retry.destroyed)
        time += 60_000
        val next = Handle()
        val session = session({ true }, limiter()::reserve)
        session.start(next); assertEquals(1, next.requests); session.stop()
    }

    @Test fun clockRollbackRebasesAndStillRequiresQuietInterval() {
        var time = 10_000L
        var saved: Long? = 20_000L
        val limiter = BannerAttemptLimiter({ time }, { saved }, { saved = it })
        assertFalse(limiter.reserve()); assertFalse(limiter.reserve())
        time += 59_999; assertFalse(limiter.reserve())
        time++; assertTrue(limiter.reserve())
    }

    @Test fun thrownLoadReleasesResourcesWithoutRetry() = runBlocking {
        val handle = object : BannerHandle {
            var destroyed = false
            override fun load(loaded: () -> Unit, failed: () -> Unit) { error("offline") }
            override fun reveal() { fail("Unexpected reveal") }
            override fun destroy() { destroyed = true }
        }
        val session = session({ true }, { true })
        session.start(handle)
        assertTrue(handle.destroyed); assertEquals(BannerPhase.UNAVAILABLE, session.phase.value)
    }

    @Test fun providerBackgroundCallbackUsesTheExplicitUiDispatcher() = runBlocking {
        java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { ui ->
            val scope = CoroutineScope(SupervisorJob() + ui)
            val displayed = CompletableDeferred<Thread>()
            var onLoaded: () -> Unit = {}
            val handle = object : BannerHandle {
                override fun load(loaded: () -> Unit, failed: () -> Unit) { onLoaded = loaded }
                override fun reveal() { displayed.complete(Thread.currentThread()) }
                override fun destroy() = Unit
            }
            val session = HomeBannerSession(scope, { true }, { true }, callbackDispatcher = ui)
            val owner = withContext(ui) { session.start(handle); Thread.currentThread() }
            withContext(Dispatchers.Default) { onLoaded() }
            assertSame(owner, withTimeout(2_000) { displayed.await() })
            withContext(ui) { session.stop() }
            scope.cancel()
        }
    }
}
