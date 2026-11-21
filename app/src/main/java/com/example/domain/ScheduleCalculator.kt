package com.example.domain

import com.example.data.model.DaySchedule
import com.example.data.model.OutageInterval
import com.example.data.model.ParsedSchedulePage
import com.example.data.model.ScheduleAnalysis
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleCalculator {

    val KYIV_ZONE: ZoneId = ZoneId.of("Europe/Kyiv")
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun currentDateTime(useKyivTimezone: Boolean): LocalDateTime {
        val zone = if (useKyivTimezone) KYIV_ZONE else ZoneId.systemDefault()
        return LocalDateTime.now(zone)
    }

    fun analyze(
        page: ParsedSchedulePage,
        queueId: String,
        leadTimeMinutes: Int,
        now: LocalDateTime,
    ): ScheduleAnalysis {
        val allQueues = page.days
            .flatMap { day -> day.queues.map { it.queueId } }
            .distinct()
            .ifEmpty { DEFAULT_QUEUES }

        val effectiveQueue = if (queueId in allQueues) queueId else allQueues.first()

        val sortedDays = page.days.sortedBy { it.dateIso }
        val dayOutagesByDate = sortedDays.associate { day ->
            val slots = day.queues.firstOrNull { it.queueId == effectiveQueue }?.slots.orEmpty()
            day.dateIso to mergeSlotsIntoDayIntervals(day.toLocalDate(), slots)
        }

        // Merge consecutive intervals across midnight for accurate current/next duration
        val continuousIntervals = mergeContinuousAcrossDays(
            sortedDays.flatMap { day -> dayOutagesByDate[day.dateIso].orEmpty() }
        )

        val currentOutage = continuousIntervals.firstOrNull { it.isOngoing(now) }
        val isCurrentlyOutage = currentOutage != null
        val timeUntilPowerRestored = currentOutage?.let {
            Duration.between(now, it.end).coerceAtLeast(Duration.ZERO)
        }

        val nextOutage = continuousIntervals.firstOrNull { it.isUpcoming(now) }
        val timeUntilNextOutage = nextOutage?.let {
            Duration.between(now, it.start).coerceAtLeast(Duration.ZERO)
        }
        val nextOutageDuration = nextOutage?.duration

        val plannedAlertTime = nextOutage?.start?.minusMinutes(leadTimeMinutes.toLong())

        return ScheduleAnalysis(
            queueId = effectiveQueue,
            now = now,
            isCurrentlyOutage = isCurrentlyOutage,
            currentOutage = currentOutage,
            timeUntilPowerRestored = timeUntilPowerRestored,
            nextOutage = nextOutage,
            timeUntilNextOutage = timeUntilNextOutage,
            nextOutageDuration = nextOutageDuration,
            plannedAlertTime = plannedAlertTime,
            daySchedules = sortedDays,
            dayOutagesByDate = dayOutagesByDate,
            availableQueues = allQueues,
        )
    }

    fun mergeSlotsIntoDayIntervals(date: LocalDate, slots: List<Boolean>): List<OutageInterval> {
        if (slots.isEmpty()) return emptyList()
        val result = mutableListOf<OutageInterval>()
        var runStartIndex: Int? = null

        for (i in 0 until minOf(slots.size, 48)) {
            if (slots[i]) {
                if (runStartIndex == null) runStartIndex = i
            } else {
                if (runStartIndex != null) {
                    result.add(slotRangeToInterval(date, runStartIndex, i))
                    runStartIndex = null
                }
            }
        }
        if (runStartIndex != null) {
            result.add(slotRangeToInterval(date, runStartIndex, minOf(slots.size, 48)))
        }
        return result
    }

    private fun slotRangeToInterval(date: LocalDate, startSlot: Int, endSlotExclusive: Int): OutageInterval {
        val start = date.atTime(LocalTime.MIDNIGHT).plusMinutes(startSlot * 30L)
        val end = date.atTime(LocalTime.MIDNIGHT).plusMinutes(endSlotExclusive * 30L)
        return OutageInterval(start = start, end = end)
    }

    private fun mergeContinuousAcrossDays(intervals: List<OutageInterval>): List<OutageInterval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.start }
        val merged = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val prev = merged.last()
            val curr = sorted[i]
            if (prev.end == curr.start) {
                merged[merged.lastIndex] = OutageInterval(prev.start, curr.end)
            } else {
                merged.add(curr)
            }
        }
        return merged
    }

    fun buildQueueSignature(page: ParsedSchedulePage, queueId: String): String {
        return page.days.sortedBy { it.dateIso }.joinToString("|") { day ->
            val bits = day.queues.firstOrNull { it.queueId == queueId }?.slots
                ?.joinToString("") { if (it) "1" else "0" }
                .orEmpty()
            "${day.dateIso}:$bits"
        }
    }

    fun formatDigitalCountdown(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationHuman(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
            hours > 0 -> "$hours ч"
            else -> "$minutes мин"
        }
    }

    fun formatIntervalRange(interval: OutageInterval): String {
        val startText = interval.start.format(TIME_FORMATTER)
        val endText = if (interval.end.toLocalTime() == LocalTime.MIDNIGHT &&
            interval.end.toLocalDate() == interval.start.toLocalDate().plusDays(1)
        ) {
            "24:00"
        } else {
            interval.end.format(TIME_FORMATTER)
        }
        return "$startText – $endText"
    }

    val DEFAULT_QUEUES = listOf(
        "1.1", "1.2",
        "2.1", "2.2",
        "3.1", "3.2",
        "4.1", "4.2",
        "5.1", "5.2",
        "6.1", "6.2",
    )
}
