package com.hbesxy.schedule.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleRefreshWorker.scheduleDaily(context)
        }
    }
}
