package com.example.contentstudio

import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.example.contentstudio.data.AtomicStorageLocks
import com.example.contentstudio.media.AtomicMediaSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AtomicStorageDeviceTest {
    @Test fun metadataReaderCannotRecoverAnActiveAtomicWriteFromAnotherInstance() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "atomic-reader-qa").apply { mkdirs() }
        val file = File(folder, "session.json")
        val first = AtomicMediaSession(folder)
        val reader = AtomicMediaSession(folder)
        first.write("old")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val readerStarted = CountDownLatch(1)
        val readerFinished = CountDownLatch(1)
        val write = async(Dispatchers.IO) {
            AtomicStorageLocks.access(file) {
                val atomic = AtomicFile(file)
                val stream = atomic.startWrite()
                try {
                    stream.write("new".toByteArray())
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    atomic.finishWrite(stream)
                } catch (error: Throwable) { atomic.failWrite(stream); throw error }
            }
        }
        var read: Deferred<String?>? = null
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            read = async(Dispatchers.IO) {
                readerStarted.countDown()
                try { reader.read() } finally { readerFinished.countDown() }
            }
            assertTrue(readerStarted.await(10, TimeUnit.SECONDS))
            assertFalse(readerFinished.await(200, TimeUnit.MILLISECONDS))
            assertTrue(File(folder, "session.json.new").exists())
            release.countDown()
            write.await()
            assertEquals("new", read.await())
            assertEquals("new", first.read())
        } finally {
            release.countDown(); write.join(); read?.join()
            AtomicFile(file).delete(); folder.delete()
        }
    }
}
