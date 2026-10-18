package com.example.contentstudio

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** No-op in normal tests. The explicit network profile initializes real sample ads first. */
class ResidentAdvertisingRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("resident-ad-network") != "true") return base
        val account = AdAccountPrivacyDeviceTest()
        return account.compose.apply(object : Statement() {
            override fun evaluate() {
                check(BuildConfig.DEBUG && BuildConfig.AD_HOME_PREVIEW && BuildConfig.AD_ACCOUNT_TESTING)
                val age = args.getString("resident-ad-age")
                check(age in setOf("adult", "protected"))
                val pid = Process.myPid()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val folder = File(context.getExternalFilesDir(null), "qa/resident-ad-media").apply { mkdirs() }
                val name = "${description.testClass.simpleName}-${description.methodName}"
                val rows = StringBuffer("stage,elapsedMs,pid,sdkInitialized,javaBytes,nativeBytes,pssKiB\n")
                val started = SystemClock.elapsedRealtime()
                fun sample(stage: String) {
                    val runtime = Runtime.getRuntime()
                    rows.append("$stage,${SystemClock.elapsedRealtime()-started},${Process.myPid()},${MobileAds.isInitialized}," +
                        "${runtime.totalMemory()-runtime.freeMemory()},${Debug.getNativeHeapAllocatedSize()},${Debug.getPss()}\n")
                }
                val report = JSONObject().put("case", name).put("ageTreatment", age).put("pid", pid)
                    .put("passed", false)
                var sampler: java.util.concurrent.ScheduledExecutorService? = null
                try {
                    sample("before-consent")
                    if (description.testClass == AdResidentBasicMediaDeviceTest::class.java) {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val engine = com.example.contentstudio.audio.AudioEngine(context)
                            val source = engine.importAudio(android.net.Uri.parse("content://com.example.contentstudio.test.audio/tone.wav"))
                            try {
                                val issue = com.example.contentstudio.audio.AudioOutputSupport().issue(source.info,
                                    com.example.contentstudio.audio.AudioTarget.FLAC, 128_000)
                                File(folder, "baseline-flac.txt").writeText(issue?.name ?: "available")
                            } finally { source.file.delete() }
                        }
                    }
                    account.prepareResidentAdvertising(age == "protected")
                    sample("resident-sdk")
                    assertEquals(pid, Process.myPid())
                    assertTrue(MobileAds.isInitialized)
                    sampler = Executors.newSingleThreadScheduledExecutor()
                    sampler.scheduleWithFixedDelay({ sample("workload") }, 0, 250, TimeUnit.MILLISECONDS)
                    base.evaluate()
                    assertEquals("Workload must use the initialized application process", pid, Process.myPid())
                    assertTrue("SDK must remain initialized throughout the workload", MobileAds.isInitialized)
                    sample("completed")
                    report.put("passed", true).put("elapsedMs", SystemClock.elapsedRealtime()-started)
                } finally {
                    sampler?.shutdownNow()
                    sampler?.awaitTermination(5, TimeUnit.SECONDS)
                    account.close()
                    File(folder, "memory.csv").writeText(rows.toString())
                    File(folder, "proof.json").writeText(report.toString(2))
                }
            }
        }, description)
    }
}
