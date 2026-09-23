package com.hbesxy.schedule.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkManager
import com.hbesxy.schedule.data.ScheduleRepository
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.model.ScheduleSnapshot
import com.hbesxy.schedule.model.TermMap
import com.hbesxy.schedule.net.ZhengFangClient
import com.hbesxy.schedule.notify.ChangeNotifier
import com.hbesxy.schedule.parser.ScheduleParser
import com.hbesxy.schedule.util.CrashLogger
import com.hbesxy.schedule.worker.ScheduleRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { ChangeNotifier.ensureChannel(this) } catch (_: Exception) {}
        CrashLogger.install(this)
        setContent { ScheduleApp() }
    }
}

/** 刷新结果：区分成功与失败，避免在主线程做网络 IO */
private sealed class RefreshOutcome {
    class Ok(val courses: List<Course>, val changed: Boolean, val fetchedAt: Long) : RefreshOutcome()
    class Fail(val message: String) : RefreshOutcome()
}

/** 深色玻璃拟态配色 */
private val DarkGlassColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color.White,
    background = Color(0xFF0C1230),
    onBackground = Color.White,
    surface = Color(0x14FFFFFF),
    onSurface = Color.White,
    surfaceVariant = Color(0x1AFFFFFF),
    onSurfaceVariant = Color(0xFFC4CFF0),
    outline = Color(0xFF8A97C9)
)

/** 玻璃卡片：半透明 + 圆角 + 细描边，营造玻璃质感 */
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ScheduleApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ScheduleRepository(context) }

    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var xnm by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("1") }
    var loggedIn by remember { mutableStateOf(false) }
    var courses by remember { mutableStateOf<List<Course>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dailyOn by remember { mutableStateOf(false) }
    var lastFetched by remember { mutableStateOf("从未刷新") }
    var crashLog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val (b, x, t) = repo.getConfig()
            baseUrl = b
            xnm = x
            term = t
            val cred = repo.getCredentials()
            if (cred != null) {
                username = cred.first
                password = cred.second
                loggedIn = true
            }
            val at = repo.getLastFetchedAt()
            if (at > 0) lastFetched = formatTime(at)
            courses = repo.getSavedCourses()
            dailyOn = withContext(Dispatchers.IO) {
                try {
                    WorkManager.getInstance(context).getWorkInfosByTag("daily_schedule_refresh").get()
                        .any { !it.state.isFinished }
                } catch (e: Exception) { false }
            }
        } catch (e: Exception) {
            status = "加载本地数据失败：" + (e.message ?: e.javaClass.simpleName)
        }
        crashLog = CrashLogger.read(context)
    }

    suspend fun doRefresh() {
        busy = true
        status = "正在登录教务系统..."
        // 网络请求与 DataStore 落盘是 IO 阻塞操作，必须在 IO 线程；UI 状态回到主线程更新
        val outcome = try {
            withContext(Dispatchers.IO) {
                val client = ZhengFangClient(baseUrl.ifBlank { repo.defaultBaseUrl })
                val login = client.login(username, password)
                if (!login.ok) {
                    RefreshOutcome.Fail(login.message)
                } else {
                    val raw = client.fetchSchedule(xnm, TermMap.toCode(term))
                    if (raw == null) {
                        RefreshOutcome.Fail("未获取到课表数据，请检查学年学期")
                    } else {
                        val arr = JSONArray()
                        raw.kbList.forEach { arr.put(it) }
                        val parsed = ScheduleParser.parseKbList(arr)
                        val snapshot = ScheduleSnapshot(raw.xnm, raw.xqm, parsed, System.currentTimeMillis())
                        val changed = repo.saveSnapshot(snapshot)
                        RefreshOutcome.Ok(parsed, changed, snapshot.fetchedAt)
                    }
                }
            }
        } catch (e: Exception) {
            RefreshOutcome.Fail("刷新失败：" + (e.message ?: e.javaClass.simpleName))
        }
        when (outcome) {
            is RefreshOutcome.Ok -> {
                courses = outcome.courses
                lastFetched = formatTime(outcome.fetchedAt)
                status = if (outcome.changed) "课表有变动，已更新" else "课表已更新（无变动）"
            }
            is RefreshOutcome.Fail -> status = outcome.message
        }
        busy = false
    }

    fun saveAndSchedule() {
        scope.launch {
            repo.saveCredentials(username, password)
            repo.saveConfig(baseUrl.ifBlank { repo.defaultBaseUrl }, xnm, term)
            loggedIn = true
            status = "已保存，正在首次刷新..."
            doRefresh()
        }
    }

    MaterialTheme(colorScheme = DarkGlassColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0C1230),
                            Color(0xFF2A1E5C),
                            Color(0xFF4E2B78)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text("恩施学院课表", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("教务课表实时同步", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(14.dp))

                val crash = crashLog
                if (crash != null) {
                    GlassCard {
                        Text("上次崩溃日志（请截图整段发我）", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Text(crash.take(1500), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { crashLog = null; CrashLogger.clear(context) }) {
                            Text("清除日志")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (!loggedIn) {
                    LoginForm(
                        baseUrl = baseUrl, onBaseUrl = { baseUrl = it },
                        username = username, onUsername = { username = it },
                        password = password, onPassword = { password = it },
                        xnm = xnm, onXnm = { xnm = it },
                        term = term, onTerm = { term = it },
                        busy = busy,
                        onLogin = { saveAndSchedule() }
                    )
                } else {
                    ScheduleScreen(
                        courses = courses,
                        status = status,
                        lastFetched = lastFetched,
                        dailyOn = dailyOn,
                        busy = busy,
                        onRefresh = { scope.launch { doRefresh() } },
                        onToggleDaily = { on ->
                            dailyOn = on
                            if (on) ScheduleRefreshWorker.scheduleDaily(context)
                            else WorkManager.getInstance(context).cancelAllWorkByTag("daily_schedule_refresh")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginForm(
    baseUrl: String, onBaseUrl: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    xnm: String, onXnm: (String) -> Unit,
    term: String, onTerm: (String) -> Unit,
    busy: Boolean,
    onLogin: () -> Unit
) {
    GlassCard {
        Text("教务系统入口", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = baseUrl, onValueChange = onBaseUrl, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("http://jw.hbesxy.net") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Text("学号", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value = username, onValueChange = onUsername, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Text("密码", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = xnm, onValueChange = onXnm, modifier = Modifier.weight(1f),
                label = { Text("学年(如2026)") }, singleLine = true)
            OutlinedTextField(value = term, onValueChange = onTerm, modifier = Modifier.weight(1f),
                label = { Text("学期(1/2/3)") }, singleLine = true)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)) {
            Text(if (busy) "处理中..." else "登录并拉取课表", fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("说明：账密仅保存在本机。学期 1=秋季 / 2=春季 / 3=短学期。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScheduleScreen(
    courses: List<Course>?,
    status: String,
    lastFetched: String,
    dailyOn: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onToggleDaily: (Boolean) -> Unit
) {
    GlassCard {
        Text("上次刷新：$lastFetched", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)) {
            Text(if (busy) "刷新中..." else "立即刷新课表", fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每日自动刷新", modifier = Modifier.weight(1f))
            Switch(checked = dailyOn, onCheckedChange = onToggleDaily)
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }

    Spacer(Modifier.height(18.dp))
    val list = courses
    if (list == null) {
        Text("尚未拉取课表，请点击「立即刷新课表」。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f))
        return
    }

    Text("本周课程 · 共 ${list.size} 门", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))

    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val grouped = (1..7).map { d -> d to list.filter { it.day == d }.sortedBy { it.sections.firstOrNull() ?: 0 } }
    for ((day, dayCourses) in grouped) {
        if (dayCourses.isEmpty()) continue
        Spacer(Modifier.height(8.dp))
        Text(dayNames[day], style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF8AB4FF), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        for (c in dayCourses) {
            GlassCard {
                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("第 ${c.sections.joinToString("、")} 节 ｜ 第 ${c.weeks.joinToString("、")} 周",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Text("${c.position.ifBlank { "地点待定" }} ｜ ${c.teacher}",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
