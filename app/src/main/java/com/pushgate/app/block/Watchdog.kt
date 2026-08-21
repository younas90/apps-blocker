package com.pushgate.app.block

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.repo.BlockRepository
import java.util.concurrent.TimeUnit

/**
 * Three independent revival paths, because any one of them can be defeated:
 *
 *  - [WatchdogWorker]     — WorkManager, survives process death, 15-minute floor.
 *  - [WatchdogAlarmReceiver] — AlarmManager, tighter interval, survives Doze on most OEMs.
 *  - [BootReceiver]       — brings everything back after a reboot or an app update.
 *
 * None of them can re-enable the accessibility service (only the user can, by design of the OS),
 * so when it is off the watchdog's job is to be loud about it rather than to fix it silently.
 */
object WatchdogScheduler {

    private const val WORK_NAME = "pushgate-watchdog"
    private const val ALARM_INTERVAL_MS = 5 * 60 * 1000L
    private const val ALARM_REQUEST = 7701

    fun schedule(context: Context) {
        scheduleWorker(context)
        scheduleAlarm(context)
    }

    private fun scheduleWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().build())
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }.onFailure {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        Intent(context, WatchdogAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** The single health check both the worker and the alarm run. */
    suspend fun runCheck(context: Context) {
        val repo = BlockRepository.get(context)
        val settings = repo.settingsStore.current()
        if (!settings.onboardingComplete) return

        val hasBlockedApps = repo.activePackages().isNotEmpty()
        if (!hasBlockedApps) return

        val a11yOn = BlockerAccessibilityService.isEnabled(context)

        if (a11yOn) {
            Notifications.clearServiceDown(context)
            if (settings.alwaysShowGuardNotification && !BlockerForegroundService.isRunning) {
                BlockerForegroundService.start(context)
            }
        } else {
            repo.logEvent(EventLog.SERVICE_DOWN, detail = "watchdog found service disabled")
            if (settings.strictMode) Notifications.warnServiceDown(context)
        }
    }
}

class WatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { WatchdogScheduler.runCheck(applicationContext) }
        return Result.success()
    }
}

class WatchdogAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                kotlinx.coroutines.runBlocking { WatchdogScheduler.runCheck(appContext) }
            } catch (_: Throwable) {
                // Never let a watchdog crash take the app down with it.
            } finally {
                // Re-arm: AlarmManager one-shots are used instead of setRepeating so Doze cannot
                // silently stretch the interval to an hour.
                runCatching { WatchdogScheduler.scheduleAlarm(appContext) }
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in BOOT_ACTIONS) return

        val appContext = context.applicationContext
        Notifications.ensureChannels(appContext)
        WatchdogScheduler.schedule(appContext)
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
