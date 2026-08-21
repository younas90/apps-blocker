package com.pushgate.app.block

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps the app's process warm and gives the user a permanent, honest status line.
 *
 * The accessibility service does the actual work; this exists so the OS treats the process as
 * user-visible and stops reclaiming it under memory pressure, which is the single most common
 * reason home-grown blockers silently stop blocking.
 */
class BlockerForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        instance = this
        pushNotification(currentTitle, currentText)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
        intent?.getStringExtra(EXTRA_TEXT)?.let { currentText = it }
        pushNotification(currentTitle, currentText)
        return START_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app out of Recents must not take protection down with it.
        start(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    private fun pushNotification(title: String, text: String) {
        val n = Notifications.guardNotification(this, title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_GUARD, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_GUARD, n)
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        @Volatile private var instance: BlockerForegroundService? = null
        @Volatile private var currentTitle: String = "PushGate is on guard"
        @Volatile private var currentText: String = "Watching your blocked apps."

        val isRunning: Boolean get() = instance != null

        fun start(context: Context) {
            val intent = Intent(context, BlockerForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockerForegroundService::class.java))
        }

        /** Cheap in-place notification update; avoids the churn of restarting the service. */
        fun updateStatus(context: Context, title: String, text: String) {
            currentTitle = title
            currentText = text
            val svc = instance
            if (svc != null) {
                svc.pushNotification(title, text)
            } else {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BlockerForegroundService::class.java)
                        .putExtra(EXTRA_TITLE, title)
                        .putExtra(EXTRA_TEXT, text)
                )
            }
        }
    }
}
