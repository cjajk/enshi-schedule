package com.hbesxy.schedule.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hbesxy.schedule.model.Course
import java.util.Calendar
/**
 * 周课表网格：截图里之前的问题是 7 天 + 时间列硬塞进屏幕宽度，
 * 每格只有二三十 dp，课程名全被截断成"马..."、".."。
 * 这版把每天的列宽固定为一个合理宽度（可完整显示 4~5 个汉字换行），
 * 整个表格允许横向滚动，同时给星期栏加了"今天"高亮，观感上更接近
 * 市面上课程表 App（比如 Sleepy·轻课表）的网格样式。
 */
@Composable
fun WeeklyScheduleGrid(courses: List<Course>) {
    val maxSection = (courses.flatMap { it.sections }.maxOrNull() ?: 10).coerceAtLeast(10)
    val slotHeight = 52.dp
    val dayColumnWidth = 92.dp
    val timeColumnWidth = 36.dp
    val scrollState = rememberScrollState()
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    // 1(周一)..7(周日)，方便和 Course.day 对齐
    val todayIndex = remember {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=周日 ... 7=周六
        if (dow == Calendar.SUNDAY) 7 else dow - 1
    }
    val palette = listOf(
        Color(0xFFFFE3C2), Color(0xFFFFD3DC), Color(0xFFD8E8FF),
        Color(0xFFDFF5E1), Color(0xFFEBDBFF), Color(0xFFFFF3C4), Color(0xFFCFEAE6)
    )
    fun colorFor(name: String): Color = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    val coursesByDay = remember(courses) { courses.groupBy { it.day } }
    Column {
        // ---- 星期表头（与下方网格共用同一个横向滚动状态，保持对齐）----
        Row(Modifier.horizontalScroll(scrollState)) {
            Spacer(Modifier.width(timeColumnWidth))
            for (d in 1..7) {
                val isToday = d == todayIndex
                Box(
                    modifier = Modifier
                        .width(dayColumnWidth)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayNames[d - 1],
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) EnShiBlue else Color(0xFF5A6680)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            // ---- 节次列：固定不滚动 ----
            Column(Modifier.width(timeColumnWidth)) {
                for (s in 1..maxSection) {
                    Box(
                        modifier = Modifier.height(slotHeight),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            "$s",
                            fontSize = 11.sp,
                            color = Color(0xFF9AA5BD),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            // ---- 7 天课程列：横向滚动，避免被挤扁 ----
            Row(Modifier.horizontalScroll(scrollState)) {
                for (d in 1..7) {
                    val isToday = d == todayIndex
                    Box(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .height(slotHeight * maxSection)
                            .background(if (isToday) EnShiBlueLight.copy(alpha = 0.06f) else Color.Transparent)
                    ) {
                        // 节次分隔线
                        Column(Modifier.fillMaxSize()) {
                            repeat(maxSection) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(slotHeight)
                                        .border(border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFEDF1F9)))
                                )
                            }
                        }
                        // 课程色块，按起始节次定位、按跨度决定高度
                        for (c in coursesByDay[d].orEmpty()) {
                            val start = c.sections.minOrNull() ?: continue
                            val end = c.sections.maxOrNull() ?: start
                            val span = (end - start + 1).coerceAtLeast(1)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                                    .offset(y = slotHeight * (start - 1))
                                    .width(dayColumnWidth - 6.dp)
                                    .height(slotHeight * span - 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorFor(c.name))
                                    .padding(6.dp)
                            ) {
                                Column {
                                    Text(
                                        c.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1C2438),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 13.sp
                                    )
                                    if (c.position.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            c.position,
                                            fontSize = 9.sp,
                                            color = Color(0xFF5A6680),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
