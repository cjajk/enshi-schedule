package com.hbesxy.schedule.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ChangeNotifier {

    private const val CHANNEL_ID = "schedule_change"
    private const val CHANNEL_NAME = "课表变动提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "检测到课表变动或每日提醒" }
            nm.createNotificationChannel(channel)
        }
    }

    fun notifyChange(context: Context, message: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("恩施学院课表有更新")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        try { nm.notify(1001, notification) } catch (e: Exception) { }
    }
}
