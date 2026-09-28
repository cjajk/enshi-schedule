package com.hbesxy.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hbesxy.schedule.R
import com.hbesxy.schedule.data.ScheduleRepository
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件「今日课表」：
 *   - 显示当天课程（节次 + 课程名 + 地点），最多 4 门；
 *   - 点击小组件打开 App；
 *   - 由登录/手动刷新/每日 Worker 通过 ACTION_REFRESH 广播驱动更新，不消耗系统轮询电量。
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScheduleWidgetProvider::class.java)
            )
            for (id in ids) updateWidget(context, manager, id)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule)

        // 点击打开 App
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        // 标题日期（今天）+ 当前教学周
        val now = Calendar.getInstance()
        val todayIndex =
            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else now.get(Calendar.DAY_OF_WEEK) - 1
        val currentWeek = runBlocking(Dispatchers.IO) {
            try {
                ScheduleRepository(context).getCurrentWeek()
            } catch (e: Exception) {
                1
            }
        }
        views.setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("M月d日 EEE", Locale.CHINA).format(Date()) + " · 第${currentWeek}周"
        )

        // 本地已保存课表 → 今天的课程（只显示当前教学周）
        val saved = runBlocking(Dispatchers.IO) {
            try {
                ScheduleRepository(context).getSavedCourses()
            } catch (e: Exception) {
                null
            }
        }
        val todayCourses = saved
            ?.filter { it.day == todayIndex && (it.weeks.isEmpty() || it.weeks.contains(currentWeek)) }
            ?.sortedBy { it.sections.minOrNull() ?: 0 }
            ?: emptyList()

        val maxRows = 4
        for (i in 1..maxRows) {
            val rowViews = if (i <= todayCourses.size) View.VISIBLE else View.GONE
            views.setViewVisibility(rowIds[i - 1], rowViews)
            if (i <= todayCourses.size) {
                val c = todayCourses[i - 1]
                views.setTextViewText(sectionIds[i - 1], sectionLabel(c))
                views.setTextViewText(nameIds[i - 1], c.name)
                views.setTextViewText(placeIds[i - 1], c.position.ifBlank { "地点待定" })
            }
        }

        // 空状态与底部提示
        val emptyText = when {
            saved == null -> "打开 App 登录后自动显示今日课表"
            todayCourses.isEmpty() -> "今天没有课，好好休息"
            else -> "今日 ${todayCourses.size} 节课"
        }
        views.setTextViewText(R.id.widget_empty, emptyText)
        views.setViewVisibility(
            R.id.widget_empty,
            if (todayCourses.isEmpty() || saved == null) View.VISIBLE else View.GONE
        )
        val more = todayCourses.size - maxRows
        views.setTextViewText(
            R.id.widget_hint,
            if (more > 0) "还有 $more 节课 · 点击查看全部" else "点击查看全部课表"
        )

        manager.updateAppWidget(id, views)
    }

    private fun sectionLabel(c: Course): String {
        val min = c.sections.minOrNull() ?: 0
        val max = c.sections.maxOrNull() ?: min
        return if (max > min) "${min}-${max}节" else "${min}节"
    }

    companion object {
        const val ACTION_REFRESH = "com.hbesxy.schedule.action.REFRESH_WIDGET"

        private val rowIds = intArrayOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4
        )
        private val sectionIds = intArrayOf(
            R.id.widget_section_1, R.id.widget_section_2, R.id.widget_section_3, R.id.widget_section_4
        )
        private val nameIds = intArrayOf(
            R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4
        )
        private val placeIds = intArrayOf(
            R.id.widget_place_1, R.id.widget_place_2, R.id.widget_place_3, R.id.widget_place_4
        )

        /** 课表刷新成功后调用，通知所有小组件实例重新渲染 */
        fun refresh(context: Context) {
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        }
    }
}
