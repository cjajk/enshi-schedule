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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
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
fun ScheduleApp(viewModel: ScheduleViewModel = viewModel()) {
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    // 所有状态现在只有一个来源：ViewModel。感知生命周期，Activity 后台时自动停止收集。
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 简约苹果风：柔和蓝白渐变背景（浅、通透、不刺眼）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE9F1FF), Color(0xFFF7FAFF), Color(0xFFEDF3FF))
                )
            )
    ) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp, bottom = 24.dp)
            ) {
                HeaderBar()
                Spacer(Modifier.height(20.dp))
                if (!state.loggedIn) {
                    // 登录态：内容区独立滚动（单层滚动，避免嵌套导致测量崩溃）
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
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
/** 顶部渐变标题栏 */
@Composable
fun HeaderBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(EnShiBlueLight, EnShiBlue)
                ),
                RoundedCornerShape(26.dp)
            )
            .padding(vertical = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "恩施学院课表",
                color = White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "正方教务系统 · 每日自动同步",
                color = White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }
    }
}
/** 简约卡片：白色半透明 + 清晰浅蓝描边 + 柔和阴影（保留玻璃质感但可读） */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, EnShiBlue.copy(alpha = 0.14f)),
        colors = CardDefaults.cardColors(containerColor = White.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}
/** 输入框：始终有清晰边框（未聚焦半透明蓝、聚焦深蓝），容器高对比度 */
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
        shape = RoundedCornerShape(14.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EnShiBlue,
            unfocusedBorderColor = EnShiBlue.copy(alpha = 0.32f),
            focusedContainerColor = White.copy(alpha = 0.97f),
            unfocusedContainerColor = White.copy(alpha = 0.92f),
            focusedLabelColor = EnShiBlue,
            unfocusedLabelColor = Color(0xFF5A6680),
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
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EnShiBlue,
            contentColor = White,
            disabledContainerColor = EnShiBlue.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
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
            color = Color(0xFF5A6680)
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
            color = Color(0xFF5A6680),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
/** 登录后主界面：底部导航（课表 / 我的），退出登录在“我的”里 */
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
        // 内容区：独占剩余高度，自身滚动（单层滚动，底部导航固定）
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
        // 底部导航（课表 / 我的）
        BottomNavBar(tab = tab, onTab = { tab = it })
    }
}
/** 底部导航栏：简约玻璃质感，最右为“我的” */
@Composable
fun BottomNavBar(tab: Int, onTab: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(White.copy(alpha = 0.92f))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("课表", "我的").forEachIndexed { i, name ->
                val selected = tab == i
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) EnShiBlueLight.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) EnShiBlue else Color(0xFF5A6680)
                    )
                }
            }
        }
    }
}
/** 课表页：刷新卡片 + 周视图/列表切换 + 课程明细 */
@Composable
fun ScheduleHome(
    courses: List<Course>?,
    status: String,
    lastFetched: String,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上次刷新", fontSize = 12.sp, color = Color(0xFF5A6680))
                Spacer(Modifier.height(2.dp))
                Text(lastFetched, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
            }
            PrimaryButton(text = if (busy) "刷新中..." else "立即刷新", enabled = !busy) { onRefresh() }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                status,
                fontSize = 12.sp,
                color = if (status.startsWith("刷新失败") || status.contains("不正确")) Color(0xFFD3545C) else EnShiBlueLight
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    val list = courses
    if (list == null) {
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("尚未拉取课表", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
                Spacer(Modifier.height(6.dp))
                Text("点击「立即刷新」获取最新安排", fontSize = 12.sp, color = Color(0xFF5A6680))
            }
        }
        return
    }
    // 优化点：用 remember(list) 缓存排序结果，避免每次重组都重新排序同一份数据
    val sorted = remember(list) {
        list.sortedWith(compareBy<Course> { it.day }.thenBy { it.sections.firstOrNull() ?: 0 })
    }
    val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
    // 视图切换：周视图（网格）/ 列表视图
    var viewMode by remember { mutableStateOf("week") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            "共 ${list.size} 门课程",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1C2438),
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Row(
            modifier = Modifier
                .background(Color(0xFFE6EDF8), RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            TextButton(
                onClick = { viewMode = "week" },
                modifier = Modifier
                    .height(30.dp)
                    .background(if (viewMode == "week") White else Color.Transparent, RoundedCornerShape(8.dp))
            ) {
                Text(
                    "周视图", fontSize = 12.sp,
                    color = if (viewMode == "week") EnShiBlue else Color(0xFF5A6680),
                    fontWeight = if (viewMode == "week") FontWeight.SemiBold else FontWeight.Normal
                )
            }
            TextButton(
                onClick = { viewMode = "list" },
                modifier = Modifier
                    .height(30.dp)
                    .background(if (viewMode == "list") White else Color.Transparent, RoundedCornerShape(8.dp))
            ) {
                Text(
                    "列表", fontSize = 12.sp,
                    color = if (viewMode == "list") EnShiBlue else Color(0xFF5A6680),
                    fontWeight = if (viewMode == "list") FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
    if (viewMode == "week") {
        // ===== 周课表可视化网格 =====
        GlassCard(modifier = Modifier.padding(bottom = 14.dp)) {
            WeeklyScheduleGrid(list)
        }
        Text(
            "课程明细",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1C2438),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        for (c in sorted) {
            GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C2438))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "第 ${c.sections.joinToString("、")} 节 ｜ 第 ${c.weeks.joinToString("、")} 周 ｜ ${c.position.ifBlank { "地点待定" }}",
                            fontSize = 11.sp,
                            color = Color(0xFF5A6680)
                        )
                    }
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
            }
        }
    } else {
        // ===== 列表视图 =====
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
                            color = Color(0xFF4A5568)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${c.position.ifBlank { "地点待定" }}  ·  ${c.teacher}",
                            fontSize = 12.sp,
                            color = Color(0xFF5A6680)
                        )
                    }
                }
            }
        }
    }
}
/** 我的页：账号信息 + 每日自动刷新 + 退出登录 */
@Composable
fun ProfilePage(
    username: String,
    lastFetched: String,
    dailyOn: Boolean,
    onToggleDaily: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    // 账号卡片
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(EnShiBlueLight.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("恩", color = EnShiBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("恩施学院课表", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C2438))
                Spacer(Modifier.height(2.dp))
                Text("学号 $username", fontSize = 12.sp, color = Color(0xFF5A6680))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    // 每日自动刷新
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("每日自动刷新", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
                Spacer(Modifier.height(2.dp))
                Text("每天自动同步教务系统，调课第一时间提醒", fontSize = 11.sp, color = Color(0xFF5A6680))
            }
            Switch(
                checked = dailyOn, onCheckedChange = onToggleDaily,
                colors = SwitchDefaults.colors(checkedTrackColor = EnShiBlueLight)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    // 上次刷新
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上次刷新", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2438))
                Spacer(Modifier.height(2.dp))
                Text(lastFetched, fontSize = 12.sp, color = Color(0xFF5A6680))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    // 退出登录
    Button(
        onClick = { showLogoutDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFDE8E8),
            contentColor = Color(0xFFD3545C)
        )
    ) {
        Text("退出登录", fontWeight = FontWeight.Medium)
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
}
