package com.example.data.model

import com.squareup.moshi.JsonClass
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

@JsonClass(generateAdapter = true)
data class ParsedSchedulePage(
    val fetchedAtEpochMillis: Long,
    val siteUpdatedText: String,
    val importantNotice: String,
    val days: List<DaySchedule>,
)

@JsonClass(generateAdapter = true)
data class DaySchedule(
    val dateIso: String,
    val displayDate: String,
    val queues: List<QueueDaySchedule>,
) {
    fun toLocalDate(): LocalDate = LocalDate.parse(dateIso)
}

@JsonClass(generateAdapter = true)
data class QueueDaySchedule(
    val queueId: String,
    val pidchergaId: String? = null,
    /**
     * 48 half-hour slots (00:00-00:30 .. 23:30-24:00).
     * true = outage (red cell), false = power available (white cell).
     */
    val slots: List<Boolean>,
)

data class OutageInterval(
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val duration: Duration
        get() = Duration.between(start, end)

    fun isOngoing(now: LocalDateTime): Boolean =
        !now.isBefore(start) && now.isBefore(end)

    fun isPast(now: LocalDateTime): Boolean =
        !now.isBefore(end)

    fun isUpcoming(now: LocalDateTime): Boolean =
        now.isBefore(start)
}

data class ScheduleAnalysis(
    val queueId: String,
    val now: LocalDateTime,
    val isCurrentlyOutage: Boolean,
    val currentOutage: OutageInterval?,
    val timeUntilPowerRestored: Duration?,
    val nextOutage: OutageInterval?,
    val timeUntilNextOutage: Duration?,
    val nextOutageDuration: Duration?,
    val plannedAlertTime: LocalDateTime?,
    val daySchedules: List<DaySchedule>,
    val dayOutagesByDate: Map<String, List<OutageInterval>>,
    val availableQueues: List<String>,
)

data class UserSettings(
    val selectedQueue: String = "1.1",
    val notificationsEnabled: Boolean = true,
    val leadTimeMinutes: Int = 30,
    val customAlertMessage: String = "Через {minutes} мин выключат свет (в {time}), заряди телефон!",
    val notifyOnScheduleChange: Boolean = true,
    val refreshIntervalMinutes: Int = 30,
    val useKyivTimezone: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
)
