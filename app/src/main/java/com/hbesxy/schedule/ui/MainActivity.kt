package com.hbesxy.schedule.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.notify.ChangeNotifier

val Ink = Color(0xFF1A1D29)
val InkSecondary = Color(0xFF6B7280)
val InkTertiary = Color(0xFF9CA3AF)
val Accent = Color(0xFF1B4B8C)
val AccentLight = Color(0xFF4A7FD9)
val Surface = Color(0xFFFFFFFF)
val Background = Color(0xFFF5F6FA)
val Divider = Color(0xFFEFF1F5)
val ErrorRed = Color(0xFFE5484D)
val SuccessGreen = Color(0xFF30A46C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChangeNotifier.ensureChannel(this)
        setContent { ScheduleApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            ) {
                if (!state.loggedIn) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(48.dp))
                        AppLogo()
                        Spacer(Modifier.height(36.dp))
                        LoginForm(
                            baseUrl = state.baseUrl, onBaseUrl = viewModel::onBaseUrlChange,
                            username = state.username, onUsername = viewModel::onUsernameChange,
                            password = state.password, onPassword = viewModel::onPasswordChange,
                            showPassword = state.showPassword, onToggleShowPassword = viewModel::onToggleShowPassword,
                            xnm = state.xnm, onXnm = viewModel::onXnmChange,
                            term = state.term, onTerm = viewModel::onTermChange,
                            busy = state.busy,
                            onLogin = viewModel::login
                        )
                    }
                } else {
                    ScheduleScreen(
                        modifier = Modifier.weight(1f),
                        courses = state.courses,
                        username = state.username,
                        status = state.status,
                        lastFetched = state.lastFetched,
                        dailyOn = state.dailyOn,
                        busy = state.busy,
                        onRefresh = viewModel::refresh,
                        onToggleDaily = viewModel::toggleDaily,
                        onLogout = viewModel::logout
                    )
                }
            }
        }
    }
}

@Composable
fun AppLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("恩", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("恩施学院课表", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text("正方教务系统 · 每日自动同步", fontSize = 12.sp, color = InkTertiary)
    }
}

@Composable
fun CleanCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanTextField(
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
        label = { Text(label, fontSize = 13.sp, color = InkSecondary) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Divider,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedLabelColor = Accent,
            unfocusedLabelColor = InkTertiary,
            cursorColor = Accent,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink
        )
    )
}

@Composable
fun AccentButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
            disabledContainerColor = Accent.copy(alpha = 0.3f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
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
    CleanCard {
        Text("登录教务系统", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text("输入正方教务账号密码，自动拉取最新课表", fontSize = 12.sp, color = InkTertiary)
        Spacer(Modifier.height(22.dp))
        CleanTextField(value = baseUrl, onValueChange = onBaseUrl, modifier = Modifier.fillMaxWidth(), label = "教务地址")
        Spacer(Modifier.height(12.dp))
        CleanTextField(value = username, onValueChange = onUsername, modifier = Modifier.fillMaxWidth(), label = "学号")
        Spacer(Modifier.height(12.dp))
        CleanTextField(
            value = password, onValueChange = onPassword, modifier = Modifier.fillMaxWidth(), label = "密码",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleShowPassword) {
                    Text(if (showPassword) "隐藏" else "显示", color = Accent, fontSize = 13.sp)
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CleanTextField(value = xnm, onValueChange = onXnm, modifier = Modifier.weight(1f), label = "学年(如2026)")
            CleanTextField(value = term, onValueChange = onTerm, modifier = Modifier.weight(1f), label = "学期(1/2/3)")
        }
        Spacer(Modifier.height(22.dp))
        AccentButton(
            text = if (busy) "处理中..." else "登录并拉取课表",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { onLogin() }
        Spacer(Modifier.height(14.dp))
        Text(
            "账密仅保存在本机 · 学期 1=秋季 / 2=春季 / 3=短学期",
            fontSize = 11.sp, color = InkTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    courses: List<Course>?,
    username: String,
    status: String,
    lastFetched: String,
    dailyOn: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (tab == 0) "我的课表" else "我的",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink
            )
            Text(
                username,
                fontSize = 12.sp, color = InkTertiary
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            if (tab == 0) {
                ScheduleHome(courses = courses, status = status, lastFetched = lastFetched, busy = busy, onRefresh = onRefresh)
            } else {
                ProfilePage(username = username, lastFetched = lastFetched, dailyOn = dailyOn, onToggleDaily = onToggleDaily, onLogout = onLogout)
            }
        }
        BottomNavBar(tab = tab, onTab = { tab = it })
    }
}

@Composable
fun BottomNavBar(tab: Int, onTab: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("课表", "我的").forEachIndexed { i, name ->
                val selected = tab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Color.White else InkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleHome(
    courses: List<Course>?,
    status: String,
    lastFetched: String,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    CleanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上次刷新", fontSize = 11.sp, color = InkTertiary)
                Spacer(Modifier.height(3.dp))
                Text(lastFetched, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            }
            AccentButton(text = if (busy) "刷新中" else "立即刷新", enabled = !busy) { onRefresh() }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            val isError = status.contains("失败") || status.contains("不正确")
            Text(
                status,
                fontSize = 12.sp,
                color = if (isError) ErrorRed else AccentLight
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    val list = courses
    if (list == null) {
        CleanCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Text("尚未拉取课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("点击「立即刷新」获取最新安排", fontSize = 12.sp, color = InkTertiary)
            }
        }
        return
    }

    val sorted = remember(list) {
        list.sortedWith(compareBy<Course> { it.day }.thenBy { it.sections.firstOrNull() ?: 0 })
    }
    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    var viewMode by remember { mutableStateOf("week") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Text(
            "共 ${list.size} 门课程",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Divider)
                .padding(3.dp)
        ) {
            listOf("week" to "周视图", "list" to "列表").forEach { (mode, label) ->
                TextButton(
                    onClick = { viewMode = mode },
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (viewMode == mode) Surface else Color.Transparent)
                ) {
                    Text(
                        label, fontSize = 12.sp,
                        color = if (viewMode == mode) Accent else InkSecondary,
                        fontWeight = if (viewMode == mode) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }

    if (viewMode == "week") {
        CleanCard(modifier = Modifier.padding(bottom = 14.dp)) {
            WeeklyScheduleGrid(list)
        }
        Text(
            "课程明细",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
        for (c in sorted) {
            CleanCard(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 36.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentLight)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "第${c.sections.joinToString("、")}节 · ${c.weeks.joinToString("、")}周 · ${c.position.ifBlank { "地点待定" }}",
                            fontSize = 11.sp, color = InkSecondary
                        )
                    }
                    Text(
                        dayNames[c.day],
                        fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Accent.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    } else {
        for (c in sorted) {
            CleanCard(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(top = 6.dp)
                            .clip(CircleShape)
                            .background(AccentLight)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.weight(1f))
                            Text(
                                dayNames[c.day],
                                fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Accent.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "第${c.sections.joinToString("、")}节 · ${c.weeks.joinToString("、")}周",
                            fontSize = 12.sp, color = InkSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${c.position.ifBlank { "地点待定" }}  ·  ${c.teacher}",
                            fontSize = 12.sp, color = InkTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilePage(
    username: String,
    lastFetched: String,
    dailyOn: Boolean,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    CleanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("恩", color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("恩施学院课表", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("学号 $username", fontSize = 12.sp, color = InkSecondary)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    CleanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("每日自动刷新", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("每天自动同步教务系统，调课第一时间提醒", fontSize = 11.sp, color = InkTertiary)
            }
            Switch(
                checked = dailyOn, onCheckedChange = onToggleDaily,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Accent,
                    uncheckedTrackColor = Divider
                )
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    CleanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上次刷新", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(lastFetched, fontSize = 12.sp, color = InkSecondary)
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = { showLogoutDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ErrorRed
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
    ) {
        Text("退出登录", fontWeight = FontWeight.Medium)
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录", fontWeight = FontWeight.SemiBold) },
            text = { Text("将清除本机保存的账号、密码与课表数据，确定退出？") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("确定", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消", color = InkSecondary) }
            }
        )
    }
}
