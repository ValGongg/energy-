package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode
import com.example.data.network.ZtoeRemoteDataSource
import com.example.domain.ScheduleCalculator
import com.example.notifications.NotificationHelper
import com.example.ui.components.QueuePickerSheet
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onSelectQueue: (String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSelectLeadTime: (Int) -> Unit,
    onUpdateAlertMessage: (String) -> Unit,
    onToggleScheduleChangeAlert: (Boolean) -> Unit,
    onSelectRefreshInterval: (Int) -> Unit,
    onToggleKyivTimezone: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = uiState.settings
    var showQueueSheet by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            onToggleNotifications(true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "settings_title") {
                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Очередь, предупреждения и фоновая проверка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. Queue selection
            item(key = "section_queue") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Tune,
                    title = "Моя очередь",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showQueueSheet = true }
                            .padding(vertical = 6.dp)
                            .testTag("settings_queue_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Выбранная подчерга",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Используется для расчётов и уведомлений",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = settings.selectedQueue,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 2. Notifications
            item(key = "section_notifications") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Notifications,
                    title = "Предупреждения об отключении",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Напоминать заранее",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Уведомление перед плановым отключением",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { checked ->
                                if (checked &&
                                    !hasNotificationPermission &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleNotifications(checked)
                                }
                            },
                            modifier = Modifier.testTag("notifications_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "За сколько минут предупреждать",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val leadOptions = listOf(10, 15, 30, 45, 60)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        leadOptions.forEach { minutes ->
                            val selected = settings.leadTimeMinutes == minutes
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectLeadTime(minutes) },
                                label = { Text("$minutes мин") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.testTag("lead_time_chip_$minutes"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Текст напоминания",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = settings.customAlertMessage,
                        onValueChange = onUpdateAlertMessage,
                        singleLine = false,
                        maxLines = 3,
                        supportingText = {
                            Text("Подстановки: {minutes} — мин, {time} — время начала")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_message_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    val presets = listOf(
                        "Заряди телефон" to "Через {minutes} мин выключат свет (в {time}), заряди телефон!",
                        "Сохрани работу" to "В {time} отключение (осталось {minutes} мин), сохрани всё важное.",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEachIndexed { idx, (label, template) ->
                            FilterChip(
                                selected = settings.customAlertMessage == template,
                                onClick = { onUpdateAlertMessage(template) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("preset_chip_$idx"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Изменения графика на сайте",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Уведомлять, если ZTOE обновит часы для очереди ${settings.selectedQueue}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.notifyOnScheduleChange,
                            onCheckedChange = onToggleScheduleChangeAlert,
                            modifier = Modifier.testTag("schedule_change_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSendTestNotification()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("test_notification_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправить тестовое уведомление")
                    }
                }
            }

            // 3. Background sync & timezone
            item(key = "section_sync") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Sync,
                    title = "Синхронизация и время",
                ) {
                    Text(
                        text = "Частота фоновой проверки сайта",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val intervals = listOf(15 to "15 мин", 30 to "30 мин", 60 to "1 час", 120 to "2 часа")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        intervals.forEach { (minutes, label) ->
                            FilterChip(
                                selected = settings.refreshIntervalMinutes == minutes,
                                onClick = { onSelectRefreshInterval(minutes) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.testTag("refresh_interval_chip_$minutes"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Время Украины (Europe/Kyiv)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Если выключено, используется часовой пояс телефона",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.useKyivTimezone,
                            onCheckedChange = onToggleKyivTimezone,
                            modifier = Modifier.testTag("kyiv_timezone_switch"),
                        )
                    }
                }
            }

            // 4. Theme
            item(key = "section_theme") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Palette,
                    title = "Оформление",
                ) {
                    val modes = listOf(
                        ThemeMode.DARK to "Тёмная",
                        ThemeMode.LIGHT to "Светлая",
                        ThemeMode.SYSTEM to "Системная",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        modes.forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.testTag("theme_option_${mode.name}"),
                            )
                        }
                    }
                }
            }

            // 5. Source
            item(key = "section_source") {
                SettingsSectionCard(
                    icon = Icons.Rounded.Public,
                    title = "Источник данных",
                ) {
                    Text(
                        text = "Официальная страница графиков АТ «Житомиробленерго»:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ZtoeRemoteDataSource.SCHEDULE_URL))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_ztoe_website_button"),
                    ) {
                        Text("Открыть ztoe.com.ua в браузере")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            item(key = "settings_bottom_space") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showQueueSheet) {
        val analysis = uiState.analysis
        QueuePickerSheet(
            availableQueues = analysis?.availableQueues ?: ScheduleCalculator.DEFAULT_QUEUES,
            selectedQueue = settings.selectedQueue,
            todaySchedule = analysis?.daySchedules?.firstOrNull(),
            now = analysis?.now ?: ScheduleCalculator.currentDateTime(settings.useKyivTimezone),
            onSelectQueue = onSelectQueue,
            onDismiss = { showQueueSheet = false },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
