package com.downly.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ */
/*  Notifications                                                      */
/* ------------------------------------------------------------------ */

/**
 * Builds and posts every Downly notification:
 *  - one ongoing "Downloading N files" notification (owned by [DownloadService])
 *  - one normal notification per finished / failed download (posted by DownloadRepository)
 */
object DownloadNotifier {
    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_DONE = "downloads_done"
    const val ONGOING_ID = 1

    private const val FINISHED_BASE_ID = 1000
    private const val ACCENT = 0xFF2F8BFF.toInt()
    private const val MAX_LINES = 4

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            "Downloads in progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live progress of your active downloads"
            setShowBadge(false)
            setSound(null, null)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "Finished downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tells you when a download is complete or has failed"
        }
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(done)
    }

    /* --------------------------- ongoing --------------------------- */

    /** [active] empty = the very first "Starting" notification shown right after the service starts. */
    fun buildOngoing(context: Context, active: List<DownloadItem>): Notification {
        val count = active.size
        val known = active.filter { it.hasKnownSize }
        val total = known.sumOf { it.totalBytes }
        val done = known.sumOf { it.downloadedBytes }
        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
        val speed = active.sumOf { it.speedBytesPerSec }

        val allWaiting = count > 0 && active.all { it.state == DownloadState.WAITING }

        val title = when {
            count == 0 -> "Starting downloads"
            allWaiting -> "Waiting to download"
            count == 1 -> "Downloading 1 file"
            else -> "Downloading $count files"
        }
        val text = when {
            count == 0 -> "Getting ready"
            allWaiting -> active.first().error?.takeIf { it.isNotBlank() } ?: "Waiting for network"
            percent >= 0 && speed > 0 -> "$percent%, ${formatSpeed(speed)}"
            percent >= 0 -> "$percent%"
            speed > 0 -> formatSpeed(speed)
            else -> "Connecting"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)

        if (count > 1) {
            val style = NotificationCompat.InboxStyle()
            active.take(MAX_LINES).forEach { item ->
                val name = item.fileName.ifBlank { "Getting file info" }
                style.addLine(if (item.hasKnownSize) "$name - ${item.percent}%" else name)
            }
            if (count > MAX_LINES) style.setSummaryText("+${count - MAX_LINES} more")
            builder.setStyle(style)
        }

        if (count > 0) {
            builder.addAction(0, "Pause all", pauseAllIntent(context))
        }
        return builder.build()
    }

    fun updateOngoing(context: Context, active: List<DownloadItem>) {
        post(context, ONGOING_ID, buildOngoing(context, active))
    }

    /* --------------------------- finished --------------------------- */

    fun notifyCompleted(context: Context, id: Long, fileName: String, savedUri: String?, sizeBytes: Long) {
        if (!SettingsRepository.get(context).current.notifications) return
        createChannels(context)
        val name = fileName.ifBlank { "Your file" }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(ACCENT)
            .setContentTitle("Download complete")
            .setContentText(name)
            .setSubText(if (sizeBytes > 0) formatBytes(sizeBytes) else null)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(viewFileIntent(context, id, savedUri))
            .build()
        post(context, finishedId(id), notification)
    }

    fun notifyFailed(context: Context, id: Long, fileName: String, error: String?) {
        if (!SettingsRepository.get(context).current.notifications) return
        createChannels(context)
        val name = fileName.ifBlank { "Download" }
        val reason = error?.takeIf { it.isNotBlank() } ?: "Download failed"
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(ACCENT)
            .setContentTitle("Download failed")
            .setContentText("$name: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name\n$reason"))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Retry", retryIntent(context, id))
            .build()
        post(context, finishedId(id), notification)
    }

    fun cancelFinished(context: Context, id: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(finishedId(id)) }
    }

    /* --------------------------- helpers --------------------------- */

    private fun finishedId(id: Long): Int = FINISHED_BASE_ID + (id and 0xFFFFFF).toInt()

    private fun post(context: Context, notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission denied (Android 13+). Downloads still work, only the notification is hidden.
        }
    }

    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, IMMUTABLE_UPDATE)
    }

    private fun pauseAllIntent(context: Context): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_PAUSE_ALL)
        return PendingIntent.getService(context, 1, intent, IMMUTABLE_UPDATE)
    }

    private fun retryIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, DownloadService::class.java)
            .setAction(DownloadService.ACTION_RETRY)
            .putExtra(DownloadService.EXTRA_ID, id)
        val requestCode = finishedId(id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, IMMUTABLE_UPDATE)
        } else {
            PendingIntent.getService(context, requestCode, intent, IMMUTABLE_UPDATE)
        }
    }

    /** Tap on "Download complete": open the file. Android 9 and below (plain file path): open the app instead. */
    private fun viewFileIntent(context: Context, id: Long, savedUri: String?): PendingIntent {
        val uri = savedUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null || uri.scheme != "content") return openAppIntent(context)

        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, finishedId(id), view, IMMUTABLE_UPDATE)
    }
}

/* ------------------------------------------------------------------ */
/*  CPU / Wi-Fi locks                                                  */
/* ------------------------------------------------------------------ */

/**
 * Keeps the CPU and Wi-Fi awake while a file is really downloading (screen off, phone in the pocket).
 * Held only while at least one item is DOWNLOADING, so waiting for network costs no battery.
 * The wake lock has a timeout that is renewed by every notification refresh, so a crash can never leave it held.
 */
private class DownloadLocks(context: Context) {
    private val wake: PowerManager.WakeLock? = runCatching {
        context.applicationContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Downly:download")
            ?.apply { setReferenceCounted(false) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private val wifi: WifiManager.WifiLock? = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Downly:wifi")
            ?.apply { setReferenceCounted(false) }
    }.getOrNull()

    fun acquire() {
        runCatching { wake?.acquire(WAKE_TIMEOUT_MS) }
        runCatching { wifi?.let { if (!it.isHeld) it.acquire() } }
    }

    fun release() {
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        runCatching { wifi?.let { if (it.isHeld) it.release() } }
    }

    private companion object {
        const val WAKE_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

/* ------------------------------------------------------------------ */
/*  Foreground service                                                 */
/* ------------------------------------------------------------------ */

/**
 * Keeps the process alive while downloads are running and shows the ongoing notification.
 *
 * - UI calls [DownloadService.start] after add / resume
 * - runs as a foreground service while there is at least one active (downloading / queued) item
 * - stops itself a few seconds after the last active download ends (grace time: a resume writes its
 *   new state to the database a moment after it was requested)
 * - notification is refreshed about once per second
 * - holds a CPU + Wi-Fi lock while a file is really downloading ([DownloadLocks])
 * - "Pause all" and "Retry" notification actions come back here as intents
 *
 * The engine itself lives in DownloadRepository (singleton), the service only watches and shows it.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: DownloadRepository
    private lateinit var locks: DownloadLocks

    private var foregroundStarted = false
    private var activeNow = -1 // -1 = not known yet
    private var lastStartId = 0
    private var watchJob: Job? = null
    private var renderJob: Job? = null
    private var stopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repo = DownloadRepository.get(this)
        locks = DownloadLocks(this)
        DownloadNotifier.createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                scope.launch {
                    repo.pauseAll()
                    // Started only for this action (service was not running): nothing left to do.
                    if (!foregroundStarted) stopSelf(startId)
                }
                return START_NOT_STICKY
            }

            ACTION_RETRY -> {
                if (!ensureForeground()) return START_NOT_STICKY
                val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
                if (id >= 0) {
                    DownloadNotifier.cancelFinished(this, id)
                    scope.launch { repo.resume(id) }
                }
            }

            else -> if (!ensureForeground()) return START_NOT_STICKY
        }
        // A new start request while idle: give the download time to show up as active.
        if (activeNow == 0) scheduleStop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locks.release()
        scope.cancel()
        super.onDestroy()
    }

    /* ---------------------------- foreground ---------------------------- */

    /** startForeground must run within 5 seconds of startForegroundService. @return false if it failed. */
    private fun ensureForeground(): Boolean {
        if (foregroundStarted) return true
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
            ServiceCompat.startForeground(
                this,
                DownloadNotifier.ONGOING_ID,
                DownloadNotifier.buildOngoing(this, emptyList()),
                type
            )
        } catch (e: Exception) {
            // The system did not allow a foreground service right now (for example app in background).
            stopSelf()
            return false
        }
        foregroundStarted = true
        watch()
        return true
    }

    private fun watch() {
        watchJob?.cancel()
        renderJob?.cancel()

        // 1) when to stop
        watchJob = scope.launch {
            repo.activeCount.collect { count ->
                activeNow = count
                if (count > 0) stopJob?.cancel() else scheduleStop()
            }
        }

        // 2) what to show, throttled to about one update per second
        renderJob = scope.launch {
            repo.downloads.conflate().collect { list ->
                if (list.any { it.state == DownloadState.DOWNLOADING }) locks.acquire() else locks.release()
                val active = list.filter { it.isActive }
                if (active.isNotEmpty()) DownloadNotifier.updateOngoing(this@DownloadService, active)
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun scheduleStop() {
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(IDLE_GRACE_MS)
            if (activeNow <= 0) shutDown()
        }
    }

    private fun shutDown() {
        locks.release()
        watchJob?.cancel()
        renderJob?.cancel()
        foregroundStarted = false
        activeNow = -1
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.downly.app.action.PAUSE_ALL"
        const val ACTION_RETRY = "com.downly.app.action.RETRY"
        const val EXTRA_ID = "download_id"

        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val IDLE_GRACE_MS = 3_000L

        /** Call after a download was added or resumed. Safe to call many times. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, DownloadService::class.java)
                )
            } catch (e: Exception) {
                // Not allowed to start a foreground service right now. The download still runs while the app is open.
            }
        }
    }
}
