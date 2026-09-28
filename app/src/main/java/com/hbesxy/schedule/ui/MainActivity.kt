package com.hbesxy.schedule.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hbesxy.schedule.R
import com.hbesxy.schedule.model.Course
import com.hbesxy.schedule.notify.ChangeNotifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Ink = Color(0xFF1A1D29)
val InkSecondary = Color(0xFF6B7280)
val InkTertiary = Color(0xFF9CA3AF)
val Accent = Color(0xFF1B4B8C)
val AccentLight = Color(0xFF4A7FD9)
val Surface = Color(0xFFFFFFFF)
val Background = Color(0xFFF5F6FA)
val Divider = Color(0xFFEFF1F5)
val InputBorder = Color(0xFFD5DAE3)
val ErrorRed = Color(0xFFE5484D)
val SuccessGreen = Color(0xFF30A46C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChangeNotifier.ensureChannel(this)
        val shortcutAction = intent?.getStringExtra(EXTRA_SHORTCUT)
        setContent { ScheduleApp(shortcutAction = shortcutAction) }
    }

    companion object {
        const val EXTRA_SHORTCUT = "shortcut_action"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    viewModel: ScheduleViewModel = viewModel(),
    shortcutAction: String? = null
) {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 长按 App 图标快捷方式：refresh=立即刷新，widget_guide=跳到「我的」页看小组件引导
    var tab by rememberSaveable { mutableStateOf(0) }
    var shortcutHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcutAction) {
        if (shortcutAction != null && !shortcutHandled) {
            shortcutHandled = true
            when (shortcutAction) {
                "refresh" -> viewModel.refresh()
                "widget_guide" -> tab = 1
            }
        }
    }

    // 细腻的浅蓝白渐变背景：底部略深、顶部通透，比纯色更有层次
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF1FB), Background, Color(0xFFF1F4FA))
                )
            )
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            ) {
                Crossfade(
                    targetState = state.loggedIn,
                    animationSpec = tween(320),
                    label = "login"
                ) { loggedIn ->
                    if (!loggedIn) {
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
                            tab = tab,
                            onTab = { tab = it },
                            courses = state.courses,
                            username = state.username,
                            status = state.status,
                            lastFetched = state.lastFetched,
                            currentWeek = state.currentWeek,
                            dailyOn = state.dailyOn,
                            busy = state.busy,
                            onCurrentWeekChange = viewModel::onCurrentWeekChange,
                            onRefresh = viewModel::refresh,
                            onToggleDaily = viewModel::toggleDaily,
                            onLogout = viewModel::logout
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_logo),
            contentDescription = "校徽",
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
        )
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
        shape = RoundedCornerShape(18.dp),
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
            unfocusedBorderColor = InputBorder,
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
    tab: Int,
    onTab: (Int) -> Unit,
    courses: List<Course>?,
    username: String,
    status: String,
    lastFetched: String,
    currentWeek: Int,
    dailyOn: Boolean,
    busy: Boolean,
    onCurrentWeekChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier.fillMaxWidth().fillMaxHeight()) {
        // 渐变品牌头：深蓝 → 亮蓝，白字标题 + 日期/学号
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF16396B), AccentLight))
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Text(
                    if (tab == 0) "我的课表" else "我的",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (tab == 0)
                        SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())
                    else
                        "学号 $username",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            if (tab == 0) {
                ScheduleHome(courses = courses, status = status, lastFetched = lastFetched, currentWeek = currentWeek, busy = busy, onRefresh = onRefresh)
            } else {
                ProfilePage(username = username, lastFetched = lastFetched, dailyOn = dailyOn, currentWeek = currentWeek, onCurrentWeekChange = onCurrentWeekChange, onToggleDaily = onToggleDaily, onLogout = onLogout)
            }
        }
        BottomNavBar(tab = tab, onTab = onTab)
    }
}

@Composable
fun BottomNavBar(tab: Int, onTab: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabs = listOf(
                "课表" to Icons.Filled.DateRange,
                "我的" to Icons.Filled.Person
            )
            tabs.forEachIndexed { i, (name, icon) ->
                val selected = tab == i
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = if (selected) Color.White else InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        name,
                        fontSize = 11.sp,
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
    currentWeek: Int,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    // ---- 按当前教学周过滤：只显示本周要上的课 ----
    val allCourses = courses
    val weekCourses = remember(allCourses, currentWeek) {
        allCourses?.filter { it.weeks.isEmpty() || it.weeks.contains(currentWeek) }
    }

    // ---- 今日概览卡：今天几节课、下一节是什么（只算本周） -------
    if (weekCourses != null) {
        TodayOverviewCard(weekCourses)
        Spacer(Modifier.height(14.dp))
    }

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

    if (weekCourses == null) {
        CleanCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = InkTertiary,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("尚未拉取课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text("点击「立即刷新」获取最新安排", fontSize = 12.sp, color = InkTertiary)
            }
        }
        return
    }

    val list = weekCourses
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
            "本周 ${list.size} 门课程 · 第${currentWeek}周",
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

/** 今日概览：渐变浅蓝卡，显示今天课程数与下一节 */
@Composable
fun TodayOverviewCard(courses: List<Course>) {
    val now = Calendar.getInstance()
    val todayIndex =
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else now.get(Calendar.DAY_OF_WEEK) - 1
    val todayCourses = remember(courses, todayIndex) {
        courses.filter { it.day == todayIndex }
            .sortedBy { it.sections.minOrNull() ?: 0 }
    }
    val next = todayCourses.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.10f), AccentLight.copy(alpha = 0.16f)))
            )
            .padding(18.dp)
    ) {
        Column {
            Text(
                "今天 · ${SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())}",
                fontSize = 12.sp, color = InkSecondary
            )
            Spacer(Modifier.height(6.dp))
            if (todayCourses.isEmpty()) {
                Text(
                    "今天没有课，好好休息",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink
                )
            } else {
                Text(
                    "今天 ${todayCourses.size} 节课",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "下一节：${next!!.name}（第${next.sections.minOrNull()}节 · ${next.position.ifBlank { "地点待定" }}）",
                    fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ProfilePage(
    username: String,
    lastFetched: String,
    dailyOn: Boolean,
    currentWeek: Int,
    onCurrentWeekChange: (String) -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    CleanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_logo),
                contentDescription = "校徽",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
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
                Text("当前教学周", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text("周课表与小组件只显示本周课程，调课后记得更新", fontSize = 11.sp, color = InkTertiary)
            }
            CleanTextField(
                value = currentWeek.toString(),
                onValueChange = onCurrentWeekChange,
                modifier = Modifier.width(72.dp),
                label = "第几周"
            )
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
    Spacer(Modifier.height(12.dp))

    CleanCard {
        Column {
            Text("桌面小组件", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text("长按桌面空白处 → 添加小组件 → 选择「恩施学院课表」，即可在桌面查看今日课程，随刷新自动更新。", fontSize = 11.sp, color = InkTertiary)
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
