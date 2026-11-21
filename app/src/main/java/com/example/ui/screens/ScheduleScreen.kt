package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DaySchedule
import com.example.data.model.OutageInterval
import com.example.data.model.ScheduleAnalysis
import com.example.domain.ScheduleCalculator
import com.example.notifications.NotificationHelper
import com.example.ui.components.DayTimelineBar
import com.example.ui.components.QueuePickerSheet
import com.example.ui.theme.OutageRed
import com.example.ui.theme.PowerAmber
import com.example.ui.theme.PowerOnGreen
import com.example.ui.viewmodel.MainUiState
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onSelectQueue: (String) -> Unit,
    onSelectDayIndex: (Int) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showQueuePicker by remember { mutableStateOf(false) }
    var noticeExpanded by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
    }

    val analysis = uiState.analysis
    val parsedPage = uiState.parsedPage

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
        ) {
            DashboardHeader(
                selectedQueue = uiState.settings.selectedQueue,
                lastFetchedDisplay = uiState.lastFetchedDisplay,
                isRefreshing = uiState.isRefreshing,
                onOpenQueuePicker = { showQueuePicker = true },
                onRefresh = onRefresh,
            )

            if (uiState.isInitialLoading) {
                InitialLoadingState()
            } else if (analysis == null || parsedPage == null) {
                EmptyErrorState(
                    errorMessage = uiState.errorMessage ?: "Не удалось загрузить график",
                    onRetry = onRefresh,
                )
            } else {
                val selectedDay = analysis.daySchedules.getOrNull(uiState.selectedDayIndex)
                    ?: analysis.daySchedules.firstOrNull()
                val dayIntervals = selectedDay?.let { analysis.dayOutagesByDate[it.dateIso] }.orEmpty()
                val daySlots = selectedDay?.queues
                    ?.firstOrNull { it.queueId == analysis.queueId }
                    ?.slots
                    .orEmpty()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (uiState.errorMessage != null) {
                        item(key = "error_banner") {
                            ErrorBanner(
                                message = uiState.errorMessage,
                                onRetry = onRefresh,
                                onDismiss = onDismissError,
                            )
                        }
                    }

                    if (uiState.settings.notificationsEnabled &&
                        !hasNotificationPermission &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        item(key = "permission_banner") {
                            NotificationPermissionBanner(
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            )
                        }
                    }

                    item(key = "hero_card") {
                        HeroStatusCard(
                            analysis = analysis,
                            notificationsEnabled = uiState.settings.notificationsEnabled,
                            leadTimeMinutes = uiState.settings.leadTimeMinutes,
                        )
                    }

                    item(key = "next_outage_metrics") {
                        NextOutageMetricsRow(analysis = analysis)
                    }

                    if (selectedDay != null) {
                        item(key = "timeline_card") {
                            TimelineOverviewCard(
                                daySchedules = analysis.daySchedules,
                                selectedDayIndex = uiState.selectedDayIndex,
                                selectedDay = selectedDay,
                                daySlots = daySlots,
                                dayIntervals = dayIntervals,
                                now = analysis.now,
                                onSelectDayIndex = onSelectDayIndex,
                            )
                        }

                        item(key = "outages_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Все отключения за ${selectedDay.displayDate}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = if (dayIntervals.isEmpty()) "0" else "${dayIntervals.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (dayIntervals.isEmpty()) {
                            item(key = "no_outages_day") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = PowerOnGreen,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "На этот день отключений для очереди ${analysis.queueId} нет.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = dayIntervals,
                                key = { "${it.start}_${it.end}" },
                            ) { interval ->
                                OutageIntervalRow(
                                    interval = interval,
                                    isNextOutage = analysis.nextOutage?.start == interval.start,
                                    now = analysis.now,
                                )
                            }
                        }
                    }

                    if (parsedPage.siteUpdatedText.isNotBlank() || parsedPage.importantNotice.isNotBlank()) {
                        item(key = "official_notice") {
                            OfficialNoticeCard(
                                siteUpdatedText = parsedPage.siteUpdatedText,
                                importantNotice = parsedPage.importantNotice,
                                expanded = noticeExpanded,
                                onToggleExpand = { noticeExpanded = !noticeExpanded },
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showQueuePicker) {
        QueuePickerSheet(
            availableQueues = analysis?.availableQueues ?: ScheduleCalculator.DEFAULT_QUEUES,
            selectedQueue = uiState.settings.selectedQueue,
            todaySchedule = analysis?.daySchedules?.firstOrNull(),
            now = analysis?.now ?: ScheduleCalculator.currentDateTime(uiState.settings.useKyivTimezone),
            onSelectQueue = onSelectQueue,
            onDismiss = { showQueuePicker = false },
        )
    }
}

@Composable
private fun DashboardHeader(
    selectedQueue: String,
    lastFetchedDisplay: String?,
    isRefreshing: Boolean,
    onOpenQueuePicker: () -> Unit,
    onRefresh: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refresh_angle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Свет ZTOE",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (lastFetchedDisplay != null) {
                Text(
                    text = "Обновлено: $lastFetchedDisplay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onOpenQueuePicker)
                    .testTag("queue_selector_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Очередь $selectedQueue",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Выбрать очередь",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Обновить график",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = if (isRefreshing) Modifier.rotate(spinAngle) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    analysis: ScheduleAnalysis,
    notificationsEnabled: Boolean,
    leadTimeMinutes: Int,
) {
    val isOutage = analysis.isCurrentlyOutage
    val statusAccent = if (isOutage) OutageRed else PowerOnGreen
    val gradientStart = if (isOutage) {
        OutageRed.copy(alpha = 0.22f)
    } else {
        PowerOnGreen.copy(alpha = 0.18f)
    }
    val surfaceColor = MaterialTheme.colorScheme.surface

    val pulseTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_status_card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(gradientStart, surfaceColor),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusAccent.copy(alpha = 0.16f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(statusAccent.copy(alpha = pulseAlpha)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOutage) "Сейчас света нет" else "Сейчас свет есть",
                                style = MaterialTheme.typography.labelLarge,
                                color = statusAccent,
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isOutage) Icons.Rounded.PowerOff else Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = statusAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (isOutage && analysis.currentOutage != null && analysis.timeUntilPowerRestored != null) {
                    val endText = formatTargetMoment(analysis.currentOutage.end, analysis.now)
                    Text(
                        text = "Включение света через",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ScheduleCalculator.formatDigitalCountdown(analysis.timeUntilPowerRestored),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("power_restore_countdown"),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "По графику включение $endText (${ScheduleCalculator.formatIntervalRange(analysis.currentOutage)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val totalMillis = analysis.currentOutage.duration.toMillis().coerceAtLeast(1L)
                    val elapsedMillis = Duration.between(analysis.currentOutage.start, analysis.now)
                        .toMillis()
                        .coerceIn(0L, totalMillis)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { elapsedMillis.toFloat() / totalMillis.toFloat() },
                        color = OutageRed,
                        trackColor = OutageRed.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                } else if (analysis.nextOutage != null && analysis.timeUntilNextOutage != null) {
                    val startText = formatTargetMoment(analysis.nextOutage.start, analysis.now)
                    Text(
                        text = "До следующего отключения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ScheduleCalculator.formatDigitalCountdown(analysis.timeUntilNextOutage),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("next_outage_countdown"),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Начало $startText • ${ScheduleCalculator.formatDurationHuman(analysis.timeUntilNextOutage)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Отключений не ожидается",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "По текущему опубликованному графику для очереди ${analysis.queueId} отключений больше нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (notificationsEnabled && analysis.plannedAlertTime != null && analysis.nextOutage != null) {
                    val alertTime = analysis.plannedAlertTime
                    if (alertTime.isAfter(analysis.now)) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = PowerAmber,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Напомню ${formatTargetMoment(alertTime, analysis.now)} (за $leadTimeMinutes мин)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextOutageMetricsRow(analysis: ScheduleAnalysis) {
    val nextOutage = analysis.nextOutage
    val nextTimeUntil = analysis.timeUntilNextOutage
    val nextDuration = analysis.nextOutageDuration

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card 1: Время до следующего отключения (всегда видно, даже если сейчас уже отключение)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f)
                .testTag("metric_next_outage_time"),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = PowerAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Следующее через",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (nextTimeUntil != null) {
                        ScheduleCalculator.formatDurationHuman(nextTimeUntil)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (nextOutage != null) {
                        formatTargetMoment(nextOutage.start, analysis.now)
                    } else {
                        "Нет в графике"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Card 2: Длительность следующего отключения
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .weight(1f)
                .testTag("metric_next_outage_duration"),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = OutageRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Длительность",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (nextDuration != null) {
                        ScheduleCalculator.formatDurationHuman(nextDuration)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (nextOutage != null) {
                        ScheduleCalculator.formatIntervalRange(nextOutage)
                    } else {
                        "Нет в графике"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineOverviewCard(
    daySchedules: List<DaySchedule>,
    selectedDayIndex: Int,
    selectedDay: DaySchedule,
    daySlots: List<Boolean>,
    dayIntervals: List<OutageInterval>,
    now: LocalDateTime,
    onSelectDayIndex: (Int) -> Unit,
) {
    val totalOutageMinutes = dayIntervals.sumOf { it.duration.toMinutes() }
    val totalPowerMinutes = (24L * 60L - totalOutageMinutes).coerceAtLeast(0L)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (daySchedules.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    daySchedules.forEachIndexed { index, day ->
                        val isSelected = index == selectedDayIndex
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onSelectDayIndex(index) }
                                .testTag("day_tab_$index"),
                        ) {
                            Text(
                                text = dayLabel(day, now) + " (${day.displayDate.take(5)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            DayTimelineBar(
                slots = daySlots,
                dayDate = selectedDay.toLocalDate(),
                now = now,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Без света: ${ScheduleCalculator.formatDurationHuman(Duration.ofMinutes(totalOutageMinutes))}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (totalOutageMinutes > 0) OutageRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Со светом: ${ScheduleCalculator.formatDurationHuman(Duration.ofMinutes(totalPowerMinutes))}",
                    style = MaterialTheme.typography.labelLarge,
                    color = PowerOnGreen,
                )
            }
        }
    }
}

@Composable
private fun OutageIntervalRow(
    interval: OutageInterval,
    isNextOutage: Boolean,
    now: LocalDateTime,
) {
    val isOngoing = interval.isOngoing(now)
    val isPast = interval.isPast(now)

    val borderColor by animateColorAsState(
        targetValue = when {
            isOngoing -> OutageRed.copy(alpha = 0.7f)
            isNextOutage -> PowerAmber.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        },
        label = "interval_border",
    )

    val badgeText = when {
        isOngoing -> "Идёт сейчас"
        isNextOutage -> "Следующее"
        isPast -> "Завершено"
        else -> "По графику"
    }

    val badgeColor = when {
        isOngoing -> OutageRed
        isNextOutage -> PowerAmber
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isPast) 0.65f else 1f),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 34.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isOngoing -> OutageRed
                                isNextOutage -> PowerAmber
                                isPast -> MaterialTheme.colorScheme.outline
                                else -> OutageRed.copy(alpha = 0.65f)
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = ScheduleCalculator.formatIntervalRange(interval),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (isPast) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Длительность: ${ScheduleCalculator.formatDurationHuman(interval.duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.14f),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun OfficialNoticeCard(
    siteUpdatedText: String,
    importantNotice: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggleExpand),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = PowerAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Информация Житомироблэнерго",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (siteUpdatedText.isNotBlank()) {
                            Text(
                                text = "На сайте от: $siteUpdatedText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (importantNotice.isNotBlank()) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded && importantNotice.isNotBlank()) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = importantNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onRequestPermission: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Разрешите уведомления",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Чтобы заранее предупреждать об отключении света.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag("grant_notification_button"),
            ) {
                Text("Разрешить")
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("Повтор", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onDismiss) {
                Text("Скрыть", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun InitialLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Загружаем график с ztoe.com.ua...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(54.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Не удалось получить график",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(18.dp))
            FilledTonalButton(onClick = onRetry) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Попробовать снова")
            }
        }
    }
}

private fun formatTargetMoment(target: LocalDateTime, now: LocalDateTime): String {
    val timeText = target.format(ScheduleCalculator.TIME_FORMATTER)
    return when (target.toLocalDate()) {
        now.toLocalDate() -> "сегодня в $timeText"
        now.toLocalDate().plusDays(1) -> "завтра в $timeText"
        else -> "${target.format(DateTimeFormatter.ofPattern("dd.MM", Locale.US))} в $timeText"
    }
}

private fun dayLabel(day: DaySchedule, now: LocalDateTime): String {
    val date = day.toLocalDate()
    return when (date) {
        now.toLocalDate() -> "Сегодня"
        now.toLocalDate().plusDays(1) -> "Завтра"
        else -> day.displayDate
    }
}
