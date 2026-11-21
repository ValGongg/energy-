package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ZtoeApplication
import com.example.data.model.ParsedSchedulePage
import com.example.data.model.ScheduleAnalysis
import com.example.data.model.ThemeMode
import com.example.data.model.UserSettings
import com.example.domain.ScheduleCalculator
import com.example.notifications.NotificationHelper
import com.example.notifications.OutageAlertScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MainUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val settings: UserSettings = UserSettings(),
    val parsedPage: ParsedSchedulePage? = null,
    val analysis: ScheduleAnalysis? = null,
    val selectedDayIndex: Int = 0,
    val lastFetchedDisplay: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZtoeApplication
    private val preferences = app.container.preferencesRepository
    private val repository = app.container.scheduleRepository

    private val loadingState = MutableStateFlow(LoadingMeta(isInitialLoading = true))
    private val selectedDayIndexFlow = MutableStateFlow(0)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferences.settingsFlow,
        preferences.cachedScheduleFlow,
        loadingState,
        selectedDayIndexFlow,
        tickerFlow,
    ) { settings, cachedPage, meta, dayIndex, _ ->
        val now = ScheduleCalculator.currentDateTime(settings.useKyivTimezone)
        val analysis = cachedPage?.let { page ->
            repository.analyzePage(page = page, settings = settings, now = now)
        }
        val clampedDayIndex = if (analysis != null && analysis.daySchedules.isNotEmpty()) {
            dayIndex.coerceIn(0, analysis.daySchedules.lastIndex)
        } else {
            0
        }
        val lastFetchedDisplay = cachedPage?.let { page ->
            val zone = if (settings.useKyivTimezone) ScheduleCalculator.KYIV_ZONE else ZoneId.systemDefault()
            Instant.ofEpochMilli(page.fetchedAtEpochMillis)
                .atZone(zone)
                .format(SYNC_TIME_FORMATTER)
        }

        MainUiState(
            isInitialLoading = meta.isInitialLoading && cachedPage == null,
            isRefreshing = meta.isRefreshing,
            errorMessage = meta.errorMessage,
            settings = settings,
            parsedPage = cachedPage,
            analysis = analysis,
            selectedDayIndex = clampedDayIndex,
            lastFetchedDisplay = lastFetchedDisplay,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(),
    )

    init {
        refreshSchedule()
    }

    fun refreshSchedule() {
        if (loadingState.value.isRefreshing) return
        viewModelScope.launch {
            loadingState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                repository.refreshAndSchedule(isBackgroundSync = false)
            }.onSuccess {
                loadingState.update {
                    it.copy(isInitialLoading = false, isRefreshing = false, errorMessage = null)
                }
            }.onFailure { throwable ->
                loadingState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        errorMessage = throwable.message ?: "Не удалось загрузить график с сайта ZTOE",
                    )
                }
            }
        }
    }

    fun selectDayIndex(index: Int) {
        selectedDayIndexFlow.value = index
    }

    fun selectQueue(queueId: String) {
        viewModelScope.launch {
            preferences.setSelectedQueue(queueId)
            val cached = preferences.getCachedSchedule()
            if (cached != null) {
                val signature = ScheduleCalculator.buildQueueSignature(cached, queueId)
                preferences.setLastKnownSignature(signature)
                repository.rescheduleFromCache()
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
            if (enabled) {
                repository.rescheduleFromCache()
            } else {
                OutageAlertScheduler.cancelScheduledWarning(app)
            }
        }
    }

    fun setLeadTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setLeadTimeMinutes(minutes)
            repository.rescheduleFromCache()
        }
    }

    fun setCustomAlertMessage(message: String) {
        viewModelScope.launch {
            preferences.setCustomAlertMessage(message)
            repository.rescheduleFromCache()
        }
    }

    fun setNotifyOnScheduleChange(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotifyOnScheduleChange(enabled)
        }
    }

    fun setRefreshIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setRefreshIntervalMinutes(minutes)
            OutageAlertScheduler.schedulePeriodicSync(app, minutes)
        }
    }

    fun setUseKyivTimezone(useKyiv: Boolean) {
        viewModelScope.launch {
            preferences.setUseKyivTimezone(useKyiv)
            repository.rescheduleFromCache()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun sendTestNotification() {
        val current = uiState.value.settings
        NotificationHelper.showTestNotification(
            context = app,
            queueId = current.selectedQueue,
            leadMinutes = current.leadTimeMinutes,
            template = current.customAlertMessage,
        )
    }

    fun clearError() {
        loadingState.update { it.copy(errorMessage = null) }
    }

    private data class LoadingMeta(
        val isInitialLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
    )

    companion object {
        private val SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM в HH:mm", Locale.US)

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return MainViewModel(app) as T
            }
        }
    }
}
