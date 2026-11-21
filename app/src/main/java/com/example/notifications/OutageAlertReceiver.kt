package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ZtoeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OutageAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as ZtoeApplication
        val prefs = app.container.preferencesRepository

        val queueId = intent.getStringExtra(OutageAlertScheduler.EXTRA_QUEUE_ID) ?: "1.1"
        val startText = intent.getStringExtra(OutageAlertScheduler.EXTRA_START_TEXT) ?: ""
        val endText = intent.getStringExtra(OutageAlertScheduler.EXTRA_END_TEXT) ?: ""
        val durationText = intent.getStringExtra(OutageAlertScheduler.EXTRA_DURATION_TEXT) ?: ""
        val minutesLeft = intent.getLongExtra(OutageAlertScheduler.EXTRA_MINUTES_LEFT, 30L)
        val outageStartIso = intent.getStringExtra(OutageAlertScheduler.EXTRA_OUTAGE_START_ISO).orEmpty()
        val expectedKeyPrefix = "$queueId|"
        val normalizedAlertKey = if (outageStartIso.startsWith(expectedKeyPrefix)) {
            outageStartIso
        } else {
            "$queueId|$outageStartIso"
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = prefs.getSettings()
                val lastAlerted = prefs.getLastAlertedOutageStartIso()
                if (settings.notificationsEnabled &&
                    settings.selectedQueue == queueId &&
                    outageStartIso.isNotBlank() &&
                    lastAlerted != normalizedAlertKey
                ) {
                    NotificationHelper.showOutageWarning(
                        context = context,
                        queueId = queueId,
                        startTimeText = startText,
                        endTimeText = endText,
                        durationText = durationText,
                        minutesLeft = minutesLeft,
                        template = settings.customAlertMessage,
                    )
                    prefs.setLastAlertedOutageStartIso(normalizedAlertKey)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
