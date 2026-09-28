package com.hbesxy.schedule.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.model.ScheduleSnapshot
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "schedule_prefs")

class ScheduleRepository(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val BASE_URL = stringPreferencesKey("base_url")
        val XNM = stringPreferencesKey("xnm")
        val TERM = stringPreferencesKey("term")
        val SNAPSHOT_JSON = stringPreferencesKey("snapshot_json")
        val FETCHED_AT = longPreferencesKey("fetched_at")
        val CURRENT_WEEK = intPreferencesKey("current_week")
        val TERM_START = stringPreferencesKey("term_start")
    }

    val defaultBaseUrl = "http://jw.hbesxy.net"

    suspend fun getCredentials(): Pair<String, String>? {
        val p = context.dataStore.data.first()
        val u = p[Keys.USERNAME]
        val pw = p[Keys.PASSWORD]
        return if (u.isNullOrBlank() || pw.isNullOrBlank()) null else u to pw
    }

    suspend fun saveCredentials(username: String, password: String) {
        context.dataStore.edit { p ->
            p[Keys.USERNAME] = username
            p[Keys.PASSWORD] = password
        }
    }

    suspend fun getBaseUrl(): String {
        val p = context.dataStore.data.first()
        return p[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: defaultBaseUrl
    }

    suspend fun saveConfig(baseUrl: String, xnm: String, term: String) {
        context.dataStore.edit { p ->
            p[Keys.BASE_URL] = baseUrl
            p[Keys.XNM] = xnm
            p[Keys.TERM] = term
        }
    }

    suspend fun getConfig(): Triple<String, String, String> {
        val p = context.dataStore.data.first()
        return Triple(
            p[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: defaultBaseUrl,
            p[Keys.XNM] ?: "",
            p[Keys.TERM] ?: "1"
        )
    }

    suspend fun saveSnapshot(snapshot: ScheduleSnapshot): Boolean {
        val p = context.dataStore.data.first()
        val old = p[Keys.SNAPSHOT_JSON]
        val json = serializeCourses(snapshot.courses)
        val changed = old != null && old != json
        context.dataStore.edit { prefs ->
            prefs[Keys.SNAPSHOT_JSON] = json
            prefs[Keys.FETCHED_AT] = snapshot.fetchedAt
        }
        return changed
    }

    suspend fun getLastFetchedAt(): Long {
        val p = context.dataStore.data.first()
        return p[Keys.FETCHED_AT] ?: 0L
    }

    /** 开学日期（yyyy-MM-dd，开学第一周的周一），未设置返回 null */
    suspend fun getTermStart(): String? {
        val p = context.dataStore.data.first()
        return p[Keys.TERM_START]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveTermStart(date: String) {
        context.dataStore.edit { p ->
            p[Keys.TERM_START] = date
        }
    }

    /**
     * 当前教学周：设置了开学日期则自动计算（开学所在周为第 1 周），
     * 未设置时退回手动保存的周次（兼容旧版本）。
     */
    suspend fun getCurrentWeek(): Int {
        val p = context.dataStore.data.first()
        val start = p[Keys.TERM_START]?.takeIf { it.isNotBlank() }
        if (start != null) {
            val parsed = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(start)
            } catch (e: Exception) { null }
            if (parsed != null) {
                val today = Calendar.getInstance()
                val startCal = Calendar.getInstance().apply { time = parsed }
                val millisPerDay = 24 * 60 * 60 * 1000L
                val days = ((today.timeInMillis - startCal.timeInMillis) / millisPerDay).toInt()
                val week = if (days < 0) 1 else (days / 7) + 1
                return week.coerceIn(1, 30)
            }
        }
        return (p[Keys.CURRENT_WEEK] ?: 1).coerceIn(1, 30)
    }

    suspend fun saveCurrentWeek(week: Int) {
        context.dataStore.edit { p ->
            p[Keys.CURRENT_WEEK] = week.coerceIn(1, 30)
        }
    }

    suspend fun getSavedCourses(): List<Course>? {
        val p = context.dataStore.data.first()
        val json = p[Keys.SNAPSHOT_JSON] ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                Course(
                    name = obj.optString("name"),
                    teacher = obj.optString("teacher"),
                    position = obj.optString("position"),
                    day = obj.optInt("day"),
                    weeks = obj.optJSONArray("weeks")?.let { w ->
                        (0 until w.length()).map { w.getInt(it) }
                    } ?: emptyList(),
                    sections = obj.optJSONArray("sections")?.let { s ->
                        (0 until s.length()).map { s.getInt(it) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) { null }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private fun serializeCourses(courses: List<Course>): String {
        val json = JSONArray()
        for (c in courses) {
            json.put(JSONObject().apply {
                put("name", c.name)
                put("teacher", c.teacher)
                put("position", c.position)
                put("day", c.day)
                put("weeks", JSONArray(c.weeks))
                put("sections", JSONArray(c.sections))
            })
        }
        return json.toString()
    }
}
