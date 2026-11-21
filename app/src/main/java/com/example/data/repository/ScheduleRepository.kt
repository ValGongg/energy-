package com.example.data.repository

import android.content.Context
import com.example.data.model.ParsedSchedulePage
import com.example.data.model.ScheduleAnalysis
import com.example.data.model.UserSettings
import com.example.data.network.ZtoeRemoteDataSource
import com.example.data.preferences.PreferencesRepository
import com.example.domain.ScheduleCalculator
import com.example.notifications.NotificationHelper
import com.example.notifications.OutageAlertScheduler
import java.time.LocalDateTime

class ScheduleRepository(
    private val context: Context,
    private val remoteDataSource: ZtoeRemoteDataSource,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend fun refreshAndSchedule(isBackgroundSync: Boolean = false): ParsedSchedulePage {
        val settings = preferencesRepository.getSettings()
        val page = remoteDataSource.fetchSchedule()
        val previousSignature = preferencesRepository.getLastKnownSignature()
        val newSignature = ScheduleCalculator.buildQueueSignature(page, settings.selectedQueue)

        preferencesRepository.saveCachedSchedule(page)
        preferencesRepository.setLastKnownSignature(newSignature)

        val now = ScheduleCalculator.currentDateTime(settings.useKyivTimezone)
        val analysis = ScheduleCalculator.analyze(
            page = page,
            queueId = settings.selectedQueue,
            leadTimeMinutes = settings.leadTimeMinutes,
            now = now,
        )

        if (isBackgroundSync &&
            settings.notificationsEnabled &&
            settings.notifyOnScheduleChange &&
            previousSignature != null &&
            previousSignature != newSignature
        ) {
            val todayIntervals = analysis.dayOutagesByDate[
                page.days.firstOrNull()?.dateIso.orEmpty()
            ].orEmpty()
            val summary = if (todayIntervals.isEmpty()) {
                "Отключений по графику не найдено."
            } else {
                todayIntervals.joinToString(", ") { ScheduleCalculator.formatIntervalRange(it) }
            }
            NotificationHelper.showScheduleUpdated(
                context = context,
                queueId = analysis.queueId,
                summary = summary,
            )
        }

        val lastAlerted = preferencesRepository.getLastAlertedOutageStartIso()
        OutageAlertScheduler.scheduleNextWarning(
            context = context,
            analysis = analysis,
            settings = settings,
            lastAlertedOutageIso = lastAlerted,
            markAlerted = { iso -> preferencesRepository.setLastAlertedOutageStartIso(iso) },
        )

        return page
    }

    suspend fun rescheduleFromCache(customNow: LocalDateTime? = null) {
        val settings = preferencesRepository.getSettings()
        val cached = preferencesRepository.getCachedSchedule() ?: return
        val now = customNow ?: ScheduleCalculator.currentDateTime(settings.useKyivTimezone)
        val analysis = ScheduleCalculator.analyze(
            page = cached,
            queueId = settings.selectedQueue,
            leadTimeMinutes = settings.leadTimeMinutes,
            now = now,
        )
        val lastAlerted = preferencesRepository.getLastAlertedOutageStartIso()
        OutageAlertScheduler.scheduleNextWarning(
            context = context,
            analysis = analysis,
            settings = settings,
            lastAlertedOutageIso = lastAlerted,
            markAlerted = { iso -> preferencesRepository.setLastAlertedOutageStartIso(iso) },
        )
    }

    fun analyzePage(
        page: ParsedSchedulePage,
        settings: UserSettings,
        now: LocalDateTime,
    ): ScheduleAnalysis {
        return ScheduleCalculator.analyze(
            page = page,
            queueId = settings.selectedQueue,
            leadTimeMinutes = settings.leadTimeMinutes,
            now = now,
        )
    }
}
