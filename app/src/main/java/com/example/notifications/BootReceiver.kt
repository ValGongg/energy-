package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ZtoeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as ZtoeApplication
        val container = app.container

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = container.preferencesRepository.getSettings()
                OutageAlertScheduler.schedulePeriodicSync(context, settings.refreshIntervalMinutes)
                container.scheduleRepository.rescheduleFromCache()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
