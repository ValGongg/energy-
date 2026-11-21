package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ZtoeApplication

class ScheduleSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ZtoeApplication
        return runCatching {
            app.container.scheduleRepository.refreshAndSchedule(isBackgroundSync = true)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
