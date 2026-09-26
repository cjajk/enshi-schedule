package com.hbesxy.schedule.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.hbesxy.schedule.data.ScheduleRepository
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.model.ScheduleSnapshot
import com.hbesxy.schedule.model.TermMap
import com.hbesxy.schedule.net.ZhengFangClient
import com.hbesxy.schedule.parser.ScheduleParser
import com.hbesxy.schedule.worker.ScheduleRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
/**
 * 登录 / 课表页面的完整 UI 状态。
 * 替代原来散落在 Composable 里的十几个 remember 变量。
 */
data class ScheduleUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val xnm: String = "",
    val term: String = "1",
    val loggedIn: Boolean = false,
    val courses: List<Course>? = null,
    val status: String = "",
    val busy: Boolean = false,
    val dailyOn: Boolean = false,
    val lastFetched: String = "从未刷新"
)
/**
 * 承载原来写在 ScheduleApp Composable 里的登录 / 刷新 / 登出逻辑。
 * 好处：
 *  1. 屏幕旋转或进程重建时状态不丢（配合 SavedStateHandle 可进一步增强）；
 *  2. 业务逻辑脱离 Compose，可以单独写单元测试；
 *  3. Composable 只负责渲染，读起来更清爽。
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ScheduleRepository(application)
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    init {
        loadInitialState()
    }
    private fun loadInitialState() {
        viewModelScope.launch {
            val (b, x, t) = repo.getConfig()
            val cred = repo.getCredentials()
            val at = repo.getLastFetchedAt()
            val savedCourses = repo.getSavedCourses()
            val dailyOn = withContext(Dispatchers.IO) {
                try {
                    WorkManager.getInstance(getApplication())
                        .getWorkInfosByTag("daily_schedule_refresh").get()
                        .any { !it.state.isFinished }
                } catch (e: Exception) {
                    false
                }
            }
            _uiState.update { current ->
                current.copy(
                    baseUrl = b,
                    xnm = x,
                    term = t,
                    username = cred?.first ?: current.username,
                    password = cred?.second ?: current.password,
                    loggedIn = cred != null,
                    lastFetched = if (at > 0) formatTime(at) else current.lastFetched,
                    courses = savedCourses,
                    dailyOn = dailyOn
                )
            }
        }
    }
    // ---- 登录表单的输入回调 ----
    fun onBaseUrlChange(v: String) = _uiState.update { it.copy(baseUrl = v) }
    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onToggleShowPassword() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onXnmChange(v: String) = _uiState.update { it.copy(xnm = v) }
    fun onTermChange(v: String) = _uiState.update { it.copy(term = v.filter { c -> c.isDigit() }) }
    /** 登录并首次拉取课表 */
    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            repo.saveCredentials(state.username, state.password)
            repo.saveConfig(state.baseUrl.ifBlank { repo.defaultBaseUrl }, state.xnm, state.term)
            _uiState.update { it.copy(loggedIn = true, status = "已保存，正在首次刷新...") }
            refresh()
        }
    }
    /** 刷新课表（登录后手动点击「立即刷新」也会走这里） */
    fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "正在登录教务系统...") }
            try {
                val (msg, snapshot) = withContext(Dispatchers.IO) {
                    val client = ZhengFangClient(state.baseUrl.ifBlank { repo.defaultBaseUrl })
                    val login = client.login(state.username, state.password)
                    if (!login.ok) {
                        return@withContext login.message to null as ScheduleSnapshot?
                    }
                    val raw = client.fetchSchedule(state.xnm, TermMap.toCode(state.term))
                        ?: return@withContext "未获取到课表数据，请检查学年学期" to null as ScheduleSnapshot?
                    val arr = JSONArray()
                    raw.kbList.forEach { arr.put(it) }
                    val parsed = ScheduleParser.parseKbList(arr)
                    val snapshot = ScheduleSnapshot(raw.xnm, raw.xqm, parsed, System.currentTimeMillis())
                    val changed = repo.saveSnapshot(snapshot)
                    val m = if (changed) "课表有变动，已更新" else "课表已更新（无变动）"
                    m to snapshot
                }
                _uiState.update { current ->
                    if (snapshot != null) {
                        current.copy(
                            courses = snapshot.courses,
                            lastFetched = formatTime(snapshot.fetchedAt),
                            status = msg
                        )
                    } else {
                        current.copy(status = msg)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(status = "刷新失败：" + (e.message ?: e.javaClass.simpleName))
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
    fun toggleDaily(on: Boolean) {
        _uiState.update { it.copy(dailyOn = on) }
        val app = getApplication<Application>()
        if (on) {
            ScheduleRefreshWorker.scheduleDaily(app)
        } else {
            WorkManager.getInstance(app).cancelAllWorkByTag("daily_schedule_refresh")
        }
    }
    fun logout() {
        viewModelScope.launch {
            repo.clearAll()
            _uiState.value = ScheduleUiState()
        }
    }
    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}
