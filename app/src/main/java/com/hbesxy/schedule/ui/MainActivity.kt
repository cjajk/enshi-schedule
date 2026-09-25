package com.hbesxy.schedule.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.hbesxy.schedule.worker.ScheduleRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 校徽深蓝主色 */
val EnShiBlue = Color(0xFF123A6B)
val EnShiBlueLight = Color(0xFF3B6FD4)
val EnShiBlueSoft = Color(0xFF9DB7E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChangeNotifier.ensureChannel(this)
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
    var showPassword by remember { mutableStateOf(false) }
    var xnm by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("1") }
    var loggedIn by remember { mutableStateOf(false) }
    var courses by remember { mutableStateOf<List<Course>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dailyOn by remember { mutableStateOf(false) }
    var lastFetched by remember { mutableStateOf("从未刷新") }

    LaunchedEffect(Unit) {
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
    }

    suspend fun doRefresh() {
        busy = true
        status = "正在登录教务系统..."
        try {
            val (msg, snapshot) = withContext(Dispatchers.IO) {
                val client = ZhengFangClient(baseUrl.ifBlank { repo.defaultBaseUrl })
                val login = client.login(username, password)
                if (!login.ok) return@withContext login.message to null as ScheduleSnapshot?
                val raw = client.fetchSchedule(xnm, TermMap.toCode(term))
                if (raw == null) return@withContext "未获取到课表数据，请检查学年学期" to null as ScheduleSnapshot?
                val arr = JSONArray()
                raw.kbList.forEach { arr.put(it) }
                val parsed = ScheduleParser.parseKbList(arr)
                val snapshot = ScheduleSnapshot(raw.xnm, raw.xqm, parsed, System.currentTimeMillis())
                val changed = repo.saveSnapshot(snapshot)
                val m = if (changed) "课表有变动，已更新" else "课表已更新（无变动）"
                m to snapshot
            }
            if (snapshot != null) {
                courses = snapshot.courses
                lastFetched = formatTime(snapshot.fetchedAt)
            }
            status = msg
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

    fun logout() {
        scope.launch {
            repo.clearAll()
            username = ""
            password = ""
            xnm = ""
            term = "1"
            loggedIn = false
            courses = null
            status = ""
            lastFetched = "从未刷新"
        }
    }

    // 苹果风：柔和蓝白渐变玻璃背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF1FF), Color(0xFFF8FBFF), Color(0xFFE8F0FF))
                )
            )
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 24.dp)
            ) {
                HeaderBar()
                Spacer(Modifier.height(20.dp))
                if (!loggedIn) {
                    LoginForm(
                        baseUrl = baseUrl, onBaseUrl = { baseUrl = it },
                        username = username, onUsername = { username = it },
                        password = password, onPassword = { password = it },
                        showPassword = showPassword, onToggleShowPassword = { showPassword = !showPassword },
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
                        },
                        onLogout = { logout() }
                    )
                }
            }
        }
    }
}

/** 顶部玻璃标题栏 */
@Composable
fun HeaderBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(EnShiBlueLight.copy(alpha = 0.75f), EnShiBlue.copy(alpha = 0.85f))
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(vertical = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "恩施学院课表",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "正方教务系统 · 每日自动同步",
                color = White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }
    }
}

/** 毛玻璃卡片 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.60f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/** 玻璃输入框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EnShiBlue.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = White.copy(alpha = 0.55f),
            unfocusedContainerColor = White.copy(alpha = 0.45f),
            focusedLabelColor = EnShiBlue,
            unfocusedLabelColor = Color(0xFF7C87A6),
            cursorColor = EnShiBlue,
            focusedTextColor = Color(0xFF1C2438),
            unfocusedTextColor = Color(0xFF1C2438)
        )
    )
}

/** 主渐变按钮 */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EnShiBlue,
            contentColor = White,
            disabledContainerColor = EnShiBlue.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginForm(
    baseUrl: String, onBaseUrl: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    showPassword: Boolean, onToggleShowPassword: () -> Unit,
    xnm: String, onXnm: (String) -> Unit,
    term: String, onTerm: (String) -> Unit,
    busy: Boolean,
    onLogin: () -> Unit
) {
    GlassCard {
        Text(
            "登录教务系统",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1C2438)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "输入正方教务账号密码，自动拉取最新课表",
            fontSize = 12.sp,
            color = Color(0xFF7C87A6)
        )
        Spacer(Modifier.height(20.dp))
        GlassTextField(value = baseUrl, onValueChange = onBaseUrl, modifier = Modifier.fillMaxWidth(), label = "教务地址")
        Spacer(Modifier.height(12.dp))
        GlassTextField(value = username, onValueChange = onUsername, modifier = Modifier.fillMaxWidth(), label = "学号")
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(), label = "密码",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleShowPassword) {
                    Text(if (showPassword) "隐藏" else "显示", color = EnShiBlue, fontSize = 13.sp)
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextField(value = xnm, onValueChange = onXnm, modifier = Modifier.weight(1f), label = "学年(如2026)")
            GlassTextField(value = term, onValueChange = onTerm, modifier = Modifier.weight(1f), label = "学期(1/2/3)")
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = if (busy) "处理中..." else "登录并拉取课表", enabled = !busy, modifier = Modifier.fillMaxWidth()) { onLogin() }
        Spacer(Modifier.height(12.dp))
        Text(
            "账密仅保存在本机 · 学期 1=秋季 / 2=春季 / 3=短学期",
            fontSize = 11.sp,
            color = Color(0xFF7C87A6),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    courses: List<Course>?,
    status: String,
    lastFetched: String,
    dailyOn: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上次刷新", fontSize = 12.sp, color = Color(0xFF7C87A6))
                Spacer(Modifier.height(2.dp))
                Text(lastFetched, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每日自动刷新", fontSize = 13.sp, color = Color(0xFF1C2438))
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = dailyOn, onCheckedChange = onToggleDaily,
                    colors = SwitchDefaults.colors(checkedTrackColor = EnShiBlueLight)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = if (busy) "刷新中..." else "立即刷新课表", enabled = !busy, modifier = Modifier.weight(1f)) { onRefresh() }
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedButtonDefaults.colors(contentColor = EnShiBlue)
            ) {
                Text("退出登录", fontWeight = FontWeight.Medium)
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, fontSize = 12.sp, color = if (status.startsWith("刷新失败") || status.contains("不正确")) Color(0xFFD3545C) else EnShiBlueLight)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("将清除本机保存的账号、密码与课表数据，确定退出？") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("确定", color = EnShiBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    Spacer(Modifier.height(16.dp))
    val list = courses
    if (list == null) {
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("尚未拉取课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
                Spacer(Modifier.height(6.dp))
                Text("点击「立即刷新课表」获取最新安排", fontSize = 12.sp, color = Color(0xFF7C87A6))
            }
        }
        return
    }
    Text(
        "共 ${list.size} 门课程",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1C2438),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    val sorted = list.sortedWith(compareBy<Course> { it.day }.thenBy { it.sections.firstOrNull() ?: 0 })
    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    for (c in sorted) {
        GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(top = 5.dp)
                        .background(EnShiBlueLight, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C2438))
                        Spacer(Modifier.weight(1f))
                        Text(
                            dayNames[c.day],
                            fontSize = 11.sp,
                            color = EnShiBlueLight,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(EnShiBlueLight.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "第 ${c.sections.joinToString("、")} 节 ｜ 第 ${c.weeks.joinToString("、")} 周",
                        fontSize = 12.sp,
                        color = Color(0xFF5A6680)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${c.position.ifBlank { "地点待定" }}  ·  ${c.teacher}",
                        fontSize = 12.sp,
                        color = Color(0xFF7C87A6)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
