package com.example

import android.app.Application
import com.example.data.network.ZtoeRemoteDataSource
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.ScheduleRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.OutageAlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
    val remoteDataSource: ZtoeRemoteDataSource = ZtoeRemoteDataSource()
    val scheduleRepository: ScheduleRepository = ScheduleRepository(
        context = application,
        remoteDataSource = remoteDataSource,
        preferencesRepository = preferencesRepository,
    )
}

class ZtoeApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)

        appScope.launch {
            val settings = container.preferencesRepository.getSettings()
            OutageAlertScheduler.schedulePeriodicSync(
                context = this@ZtoeApplication,
                intervalMinutes = settings.refreshIntervalMinutes,
            )
        }
    }
}
