package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_WARNING_ID = "outage_warning_channel"
    const val CHANNEL_UPDATE_ID = "schedule_change_channel"

    private const val NOTIFICATION_ID_WARNING = 1001
    private const val NOTIFICATION_ID_UPDATE = 1002
    private const val NOTIFICATION_ID_TEST = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val warningChannel = NotificationChannel(
            CHANNEL_WARNING_ID,
            context.getString(R.string.channel_warning_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_warning_desc)
            enableVibration(true)
        }

        val updateChannel = NotificationChannel(
            CHANNEL_UPDATE_ID,
            context.getString(R.string.channel_update_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_update_desc)
        }

        manager.createNotificationChannel(warningChannel)
        manager.createNotificationChannel(updateChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun formatAlertBody(
        template: String,
        minutesLeft: Long,
        startTimeText: String,
        durationText: String,
        queueId: String,
    ): String {
        val formatted = template
            .replace("{minutes}", minutesLeft.toString())
            .replace("{time}", startTimeText)
            .replace("{duration}", durationText)
            .replace("{queue}", queueId)
        return "$formatted (Очередь $queueId • длительность: $durationText)"
    }

    fun showOutageWarning(
        context: Context,
        queueId: String,
        startTimeText: String,
        endTimeText: String,
        durationText: String,
        minutesLeft: Long,
        template: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = "Скоро отключение света ($startTimeText – $endTimeText)"
        val body = formatAlertBody(
            template = template,
            minutesLeft = minutesLeft,
            startTimeText = startTimeText,
            durationText = durationText,
            queueId = queueId,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WARNING, notification)
    }

    fun showScheduleUpdated(
        context: Context,
        queueId: String,
        summary: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val title = "График очереди $queueId обновился"
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
    }

    fun showTestNotification(
        context: Context,
        queueId: String,
        leadMinutes: Int,
        template: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val body = formatAlertBody(
            template = template,
            minutesLeft = leadMinutes.toLong(),
            startTimeText = "22:30",
            durationText = "1 ч 30 мин",
            queueId = queueId,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_WARNING_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Тестовое предупреждение • Очередь $queueId")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, notification)
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
