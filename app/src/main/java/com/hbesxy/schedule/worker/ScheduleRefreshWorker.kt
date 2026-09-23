package com.hbesxy.schedule.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hbesxy.schedule.data.ScheduleRepository
import com.hbesxy.schedule.model.ScheduleSnapshot
import com.hbesxy.schedule.model.TermMap
import com.hbesxy.schedule.net.ZhengFangClient
import com.hbesxy.schedule.notify.ChangeNotifier
import com.hbesxy.schedule.parser.ScheduleParser
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ScheduleRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ScheduleRepository(applicationContext)
        val credentials = repo.getCredentials() ?: return Result.failure()
        val (baseUrl, xnm, term) = repo.getConfig()
        if (xnm.isBlank()) return Result.failure()

        val client = ZhengFangClient(baseUrl)
        val login = client.login(credentials.first, credentials.second)
        if (!login.ok) return Result.retry()

        val raw = client.fetchSchedule(xnm, TermMap.toCode(term)) ?: return Result.retry()
        val arr = JSONArray()
        raw.kbList.forEach { arr.put(it) }
        val courses = ScheduleParser.parseKbList(arr)

        val snapshot = ScheduleSnapshot(xnm, TermMap.toCode(term), courses, System.currentTimeMillis())
        val changed = repo.saveSnapshot(snapshot)

        if (changed) {
            ChangeNotifier.notifyChange(
                applicationContext,
                "检测到课表发生变化，共 ${courses.size} 门课程，请打开 App 查看最新安排。"
            )
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "daily_schedule_refresh"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleRefreshWorker>(1, TimeUnit.DAYS)
                .addTag(UNIQUE_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
