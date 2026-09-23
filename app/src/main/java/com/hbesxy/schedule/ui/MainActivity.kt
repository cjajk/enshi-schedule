package com.hbesxy.schedule.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
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
        try {
            val client = ZhengFangClient(baseUrl.ifBlank { repo.defaultBaseUrl })
            val login = client.login(username, password)
            if (!login.ok) {
                status = login.message
                return
            }
            status = "登录成功，正在拉取课表..."
            val raw = client.fetchSchedule(xnm, TermMap.toCode(term))
            if (raw == null) {
                status = "未获取到课表数据，请检查学年学期"
                return
            }
            val arr = JSONArray()
            raw.kbList.forEach { arr.put(it) }
            val parsed = ScheduleParser.parseKbList(arr)
            val snapshot = ScheduleSnapshot(raw.xnm, raw.xqm, parsed, System.currentTimeMillis())
            val changed = repo.saveSnapshot(snapshot)
            courses = parsed
            lastFetched = formatTime(snapshot.fetchedAt)
            status = if (changed) "课表有变动，已更新" else "课表已更新（无变动）"
        } catch (e: Exception) {
            status = "刷新失败：" + (e.message ?: e.javaClass.simpleName)
        } finally {
            busy = false
        }
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

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("恩施学院课表") }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ))
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (crashLog != null) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("上次崩溃日志（请截图整段发我）", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(crashLog.take(1500), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { crashLog = null; CrashLogger.clear(context) }) {
                                Text("清除日志")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
    Text("教务系统入口", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(value = baseUrl, onValueChange = onBaseUrl, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("http://jw.hbesxy.net") })
    Spacer(Modifier.height(12.dp))
    Text("学号", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(value = username, onValueChange = onUsername, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Text("密码", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation())
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = xnm, onValueChange = onXnm, modifier = Modifier.weight(1f),
            label = { Text("学年(如2026)") })
        OutlinedTextField(value = term, onValueChange = onTerm, modifier = Modifier.weight(1f),
            label = { Text("学期(1/2/3)") })
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "处理中..." else "登录并拉取课表")
    }
    Text("说明：账密仅保存在本机。学期 1=秋季 / 2=春季 / 3=短学期。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("上次刷新：$lastFetched", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "刷新中..." else "立即刷新课表")
            }
            Spacer(Modifier.height(8.dp))
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
    }

    Spacer(Modifier.height(16.dp))
    val list = courses
    if (list == null) {
        Text("尚未拉取课表，请点击「立即刷新课表」。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("共 ${list.size} 门课程", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    val sorted = list.sortedWith(compareBy<Course> { it.day }.thenBy { it.sections.firstOrNull() ?: 0 })
    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    for (c in sorted) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${dayNames[c.day]}｜第 ${c.sections.joinToString("、")} 节｜第 ${c.weeks.joinToString("、")} 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${c.position.ifBlank { "地点待定" }}｜${c.teacher}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
