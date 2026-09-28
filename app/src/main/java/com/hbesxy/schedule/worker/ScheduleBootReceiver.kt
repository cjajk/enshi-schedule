package com.hbesxy.schedule.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hbesxy.schedule.widget.ScheduleWidgetProvider

class ScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleRefreshWorker.scheduleDaily(context)
            ScheduleWidgetProvider.refresh(context)
        }
    }
}
