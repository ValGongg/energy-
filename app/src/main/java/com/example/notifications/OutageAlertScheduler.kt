package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.ScheduleAnalysis
import com.example.data.model.UserSettings
import com.example.domain.ScheduleCalculator
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object OutageAlertScheduler {

    private const val ALARM_REQUEST_CODE = 4201
    private const val SYNC_WORK_NAME = "ztoe_schedule_periodic_sync"

    const val EXTRA_QUEUE_ID = "extra_queue_id"
    const val EXTRA_START_TEXT = "extra_start_text"
    const val EXTRA_END_TEXT = "extra_end_text"
    const val EXTRA_DURATION_TEXT = "extra_duration_text"
    const val EXTRA_MINUTES_LEFT = "extra_minutes_left"
    const val EXTRA_OUTAGE_START_ISO = "extra_outage_start_iso"

    fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
        val clampedMinutes = intervalMinutes.coerceAtLeast(15).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            clampedMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun scheduleNextWarning(
        context: Context,
        analysis: ScheduleAnalysis,
        settings: UserSettings,
        lastAlertedOutageIso: String?,
        markAlerted: suspend (String) -> Unit,
    ) {
        cancelScheduledWarning(context)

        if (!settings.notificationsEnabled) return
        val nextOutage = analysis.nextOutage ?: return
        val outageStartIso = "${analysis.queueId}|${nextOutage.start}"
        if (lastAlertedOutageIso == outageStartIso) return

        val zone = if (settings.useKyivTimezone) ScheduleCalculator.KYIV_ZONE else ZoneId.systemDefault()
        val now = analysis.now
        val alertTime = nextOutage.start.minusMinutes(settings.leadTimeMinutes.toLong())

        val startText = nextOutage.start.format(ScheduleCalculator.TIME_FORMATTER)
        val endText = if (nextOutage.end.toLocalTime() == LocalTime.MIDNIGHT &&
            nextOutage.end.toLocalDate() == nextOutage.start.toLocalDate().plusDays(1)
        ) {
            "24:00"
        } else {
            nextOutage.end.format(ScheduleCalculator.TIME_FORMATTER)
        }
        val durationText = ScheduleCalculator.formatDurationHuman(nextOutage.duration)

        // If we are already inside the warning window before outage starts, trigger immediately once
        if (!now.isBefore(alertTime) && now.isBefore(nextOutage.start)) {
            val minutesLeft = Duration.between(now, nextOutage.start).toMinutes().coerceAtLeast(1L)
            NotificationHelper.showOutageWarning(
                context = context,
                queueId = analysis.queueId,
                startTimeText = startText,
                endTimeText = endText,
                durationText = durationText,
                minutesLeft = minutesLeft,
                template = settings.customAlertMessage,
            )
            markAlerted(outageStartIso)
            return
        }

        if (alertTime.isAfter(now)) {
            val triggerMillis = alertTime.atZone(zone).toInstant().toEpochMilli()
            val pendingIntent = buildAlarmPendingIntent(
                context = context,
                queueId = analysis.queueId,
                startText = startText,
                endText = endText,
                durationText = durationText,
                minutesLeft = settings.leadTimeMinutes.toLong(),
                outageStartIso = outageStartIso,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent,
            )
        }
    }

    fun cancelScheduledWarning(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OutageAlertReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        queueId: String,
        startText: String,
        endText: String,
        durationText: String,
        minutesLeft: Long,
        outageStartIso: String,
    ): PendingIntent {
        val intent = Intent(context, OutageAlertReceiver::class.java).apply {
            putExtra(EXTRA_QUEUE_ID, queueId)
            putExtra(EXTRA_START_TEXT, startText)
            putExtra(EXTRA_END_TEXT, endText)
            putExtra(EXTRA_DURATION_TEXT, durationText)
            putExtra(EXTRA_MINUTES_LEFT, minutesLeft)
            putExtra(EXTRA_OUTAGE_START_ISO, outageStartIso)
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
