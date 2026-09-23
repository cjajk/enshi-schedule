package com.hbesxy.schedule.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.model.ScheduleSnapshot
import kotlinx.coroutines.flow.first

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
        val oldFp = old?.let { computeFingerprint(it) }
        val newFp = snapshot.fingerprint()
        val changed = oldFp != null && oldFp != newFp

        val json = JSONArray()
        for (c in snapshot.courses) {
            json.put(JSONObject().apply {
                put("name", c.name)
                put("teacher", c.teacher)
                put("position", c.position)
                put("day", c.day)
                put("weeks", JSONArray(c.weeks))
                put("sections", JSONArray(c.sections))
            })
        }
        context.dataStore.edit { p ->
            p[Keys.SNAPSHOT_JSON] = json.toString()
            p[Keys.FETCHED_AT] = snapshot.fetchedAt
        }
        return changed
    }

    suspend fun getLastFetchedAt(): Long {
        val p = context.dataStore.data.first()
        return p[Keys.FETCHED_AT] ?: 0L
    }

    suspend fun getSavedCourses(): List<Course>? {
        val p = context.dataStore.data.first()
        val json = p[Keys.SNAPSHOT_JSON] ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Course(
                    name = it.optString("name"),
                    teacher = it.optString("teacher"),
                    position = it.optString("position"),
                    day = it.optInt("day"),
                    weeks = it.optJSONArray("weeks").let { w -> (0 until w.length()).map { w.getInt(it) } },
                    sections = it.optJSONArray("sections").let { s -> (0 until s.length()).map { s.getInt(it) } }
                )
            }
        } catch (e: Exception) { null }
    }

    private fun computeFingerprint(json: String): String {
        return try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            list.map {
                listOf(
                    it.optString("name"),
                    it.optString("teacher"),
                    it.optString("position"),
                    it.optInt("day"),
                    it.optJSONArray("weeks").toString(),
                    it.optJSONArray("sections").toString()
                ).joinToString("|")
            }.sorted().joinToString("\n")
        } catch (e: Exception) { json }
    }
}
