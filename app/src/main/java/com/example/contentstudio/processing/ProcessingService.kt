package com.example.contentstudio.processing

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.contentstudio.MainActivity
import com.example.contentstudio.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class ProcessingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs by lazy { ProcessingJobs.get(this) }
    private var wake: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.processing), NotificationManager.IMPORTANCE_LOW))
        try {
            val type = when { Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC; else -> 0 }
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(emptyList()), type)
            else startForeground(NOTIFICATION, notification(emptyList()))
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "Birlevo:processing").also { it.acquire(6 * 60 * 60 * 1000L) }
            jobs.serviceAttached()
            scope.launch {
                jobs.awaitReady()
                jobs.records.collect { records ->
                    val active = records.values.filter { it.status.active }
                    if (active.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                    else { delay(500); getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(active)) }
                }
            }
        } catch (failure: Exception) {
            android.util.Log.e("ProcessingService", "Could not promote processing service", failure)
            jobs.cancelAll(); stopSelf()
        }
    }
    private fun notification(active: List<ProcessingRecord>): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ProcessingService::class.java)
            .setAction(CANCEL).setData(("birlevo://processing/" + active.joinToString("/") { it.id }).toUri())
            .putExtra("ids", active.map { it.id }.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val running = active.firstOrNull { it.status == ProcessingStatus.RUNNING }
        val title = when (running?.kind ?: active.firstOrNull()?.kind) {
            ProcessingKind.SLIDESHOW -> R.string.slideshow_title; ProcessingKind.AUDIO -> R.string.audio_tool; ProcessingKind.VIDEO -> R.string.compress_video; ProcessingKind.PHOTO_PDF -> R.string.photos_to_pdf; else -> R.string.processing }
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_processing)
            .setContentTitle(getString(title)).setContentText(getString(R.string.processing_background))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, running?.progress ?: 0, running?.progress == null)
            .addAction(0, getString(R.string.cancel), cancel).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) jobs.cancelIds(intent.getStringArrayExtra("ids")?.toSet().orEmpty())
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { jobs.cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { jobs.serviceDetached(); scope.cancel(); wake?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
    companion object { const val CHANNEL = "media-processing"; const val NOTIFICATION = 3001; const val CANCEL = "studio.birlevo.android.CANCEL_PROCESSING" }
}
