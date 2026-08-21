package com.pushgate.app.block

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pushgate.app.MainActivity
import com.pushgate.app.R

object Notifications {

    const val CHANNEL_GUARD = "guard_min"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_GUARD = 1001
    const val ID_SERVICE_DOWN = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GUARD,
                context.getString(R.string.notif_channel_guard),
                // MIN keeps the icon out of the status bar on most launchers. Android will not
                // let a foreground service run with no notification at all, so the next best
                // thing is one that stays out of the way.
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Only appears while you are inside a blocked app, showing the countdown."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns you when protection has been switched off."
                setShowBadge(true)
            }
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun guardNotification(
        context: Context,
        title: String,
        text: String
    ) = NotificationCompat.Builder(context, CHANNEL_GUARD)
        .setSmallIcon(R.drawable.ic_stat_shield)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openAppIntent(context))
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun warnServiceDown(context: Context) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("PushGate is not protecting you")
            .setContentText("The accessibility service is off. Blocked apps are open right now. Tap to turn it back on.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The accessibility service is off, so blocked apps are wide open. Tap to turn it back on."
                )
            )
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(ID_SERVICE_DOWN, n)
    }

    fun clearServiceDown(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_SERVICE_DOWN)
    }
}
