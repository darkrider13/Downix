package com.downly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks for a new Downly build twice a day, even when the app is closed, and posts one
 * "Update available" notification per version. Scheduled / cancelled by [sync] whenever
 * the "Check for updates automatically" setting changes.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!SettingsRepository.get(applicationContext).current.autoUpdateCheck) return Result.success()

        val manager = UpdateManager.get(applicationContext)
        manager.check(manual = false)

        val state = manager.state.value
        val info = when (state) {
            is UpdateState.Available -> state.info
            is UpdateState.ReadyToInstall -> state.info
            else -> null
        }
        if (info != null && manager.shouldNotify(info.versionName)) {
            if (notifyUpdate(applicationContext, info)) manager.markNotified(info.versionName)
        }
        return Result.success()
    }

    private fun notifyUpdate(context: Context, info: UpdateInfo): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new version of Downly is available"
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(0xFF2F8BFF.toInt())
            .setContentTitle("Downly update available")
            .setContentText("Version ${info.versionName} is ready. Tap to update.")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        private const val WORK_NAME = "downly_update_check"
        private const val CHANNEL_UPDATES = "updates"
        private const val NOTIFICATION_ID = 2

        /** Turns the twice-a-day background check on or off. Safe to call repeatedly. */
        fun sync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled || BuildConfig.UPDATE_REPO.isBlank()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
