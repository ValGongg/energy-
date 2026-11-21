package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ParsedSchedulePage
import com.example.data.model.ThemeMode
import com.example.data.model.UserSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ztoe_settings")

class PreferencesRepository(private val context: Context) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val scheduleAdapter = moshi.adapter(ParsedSchedulePage::class.java)

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            selectedQueue = prefs[KEY_SELECTED_QUEUE] ?: "1.1",
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            leadTimeMinutes = prefs[KEY_LEAD_TIME_MINUTES] ?: 30,
            customAlertMessage = prefs[KEY_CUSTOM_ALERT_MESSAGE]
                ?: "Через {minutes} мин выключат свет (в {time}), заряди телефон!",
            notifyOnScheduleChange = prefs[KEY_NOTIFY_ON_CHANGE] ?: true,
            refreshIntervalMinutes = prefs[KEY_REFRESH_INTERVAL_MINUTES] ?: 30,
            useKyivTimezone = prefs[KEY_USE_KYIV_TIMEZONE] ?: true,
            themeMode = prefs[KEY_THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.DARK,
        )
    }

    val cachedScheduleFlow: Flow<ParsedSchedulePage?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_CACHED_SCHEDULE_JSON] ?: return@map null
        runCatching { scheduleAdapter.fromJson(json) }.getOrNull()
    }

    suspend fun getSettings(): UserSettings = settingsFlow.first()

    suspend fun getCachedSchedule(): ParsedSchedulePage? = cachedScheduleFlow.first()

    suspend fun saveCachedSchedule(page: ParsedSchedulePage) {
        val json = scheduleAdapter.toJson(page)
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHED_SCHEDULE_JSON] = json
        }
    }

    suspend fun setSelectedQueue(queueId: String) {
        context.dataStore.edit { it[KEY_SELECTED_QUEUE] = queueId }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLeadTimeMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_LEAD_TIME_MINUTES] = minutes.coerceIn(5, 180) }
    }

    suspend fun setCustomAlertMessage(message: String) {
        context.dataStore.edit { it[KEY_CUSTOM_ALERT_MESSAGE] = message }
    }

    suspend fun setNotifyOnScheduleChange(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFY_ON_CHANGE] = enabled }
    }

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_REFRESH_INTERVAL_MINUTES] = minutes.coerceAtLeast(15) }
    }

    suspend fun setUseKyivTimezone(useKyiv: Boolean) {
        context.dataStore.edit { it[KEY_USE_KYIV_TIMEZONE] = useKyiv }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun getLastAlertedOutageStartIso(): String? =
        context.dataStore.data.first()[KEY_LAST_ALERTED_OUTAGE_ISO]

    suspend fun setLastAlertedOutageStartIso(iso: String) {
        context.dataStore.edit { it[KEY_LAST_ALERTED_OUTAGE_ISO] = iso }
    }

    suspend fun getLastKnownSignature(): String? =
        context.dataStore.data.first()[KEY_LAST_SIGNATURE]

    suspend fun setLastKnownSignature(signature: String) {
        context.dataStore.edit { it[KEY_LAST_SIGNATURE] = signature }
    }

    companion object {
        private val KEY_SELECTED_QUEUE = stringPreferencesKey("selected_queue")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_LEAD_TIME_MINUTES = intPreferencesKey("lead_time_minutes")
        private val KEY_CUSTOM_ALERT_MESSAGE = stringPreferencesKey("custom_alert_message")
        private val KEY_NOTIFY_ON_CHANGE = booleanPreferencesKey("notify_on_change")
        private val KEY_REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        private val KEY_USE_KYIV_TIMEZONE = booleanPreferencesKey("use_kyiv_timezone")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_CACHED_SCHEDULE_JSON = stringPreferencesKey("cached_schedule_json")
        private val KEY_LAST_ALERTED_OUTAGE_ISO = stringPreferencesKey("last_alerted_outage_iso")
        private val KEY_LAST_SIGNATURE = stringPreferencesKey("last_known_signature")
    }
}
