package com.hbesxy.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.hbesxy.schedule.model.Course
import java.util.Calendar
import kotlin.math.abs

/**
 * 一周课程可视化网格
 * --------------------------------------------------
 * 横轴：周一~周日；纵轴：第 1 节 ~ 最大节次。
 * 每门课按（星期几 × 起始节次）定位，卡片高度 = 占用节次数 × 单格高，
 * 同一格多门课（冲突/合班）自动左右分栏，今天所在列高亮。
 */
private val coursePalette = listOf(
    Color(0xFFD9E6FF), // 淡蓝
    Color(0xFFDDF2E3), // 淡绿
    Color(0xFFFFE8D6), // 淡橙
    Color(0xFFE8E2FF), // 淡紫
    Color(0xFFFFE3EC), // 淡粉
    Color(0xFFD8F3F5), // 淡青
    Color(0xFFFFF3D6)  // 淡黄
)

internal fun courseColor(name: String): Color {
    var h = 0
    for (ch in name) h = h * 31 + ch.code
    return coursePalette[abs(h) % coursePalette.size]
}

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val cellH: Dp = 56.dp
private val headerH: Dp = 34.dp
private val labelW: Dp = 24.dp

@Composable
fun WeeklyScheduleGrid(courses: List<Course>, modifier: Modifier = Modifier) {
    if (courses.isEmpty()) return
    val maxSection = courses.maxOf { c -> c.sections.maxOrNull() ?: 1 }
    val today = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // 1=周一 .. 7=周日

    // 每门课 → 格子区间
    data class Block(val course: Course, val start: Int, val end: Int, val col: Int)
    val blocks = courses.map { c ->
        val s = c.sections.minOrNull() ?: 1
        val e = c.sections.maxOrNull() ?: s
        Block(c, s, e, c.day)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val colW = if (maxWidth > labelW) (maxWidth - labelW) / 7f else 44.dp

        // ===== 背景层：表头 + 节次行条带 =====
        Column(Modifier.fillMaxWidth()) {
            // 表头：节次标签位 + 7 个星期列
            Row(Modifier.height(headerH), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(labelW))
                for (d in 1..7) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (d == today) EnShiBlueLight.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "周" + dayNames[d - 1],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (d == today) EnShiBlue else Color(0xFF4A5568)
                        )
                    }
                }
            }
            // 节次行：左侧节次号 + 7 格交替底色
            for (s in 1..maxSection) {
                Row(Modifier.height(cellH)) {
                    Box(Modifier.width(labelW), contentAlignment = Alignment.Center) {
                        Text("$s", fontSize = 10.sp, color = Color(0xFF8A93A6))
                    }
                    for (d in 1..7) {
                        val isToday = d == today
                        val bg = when {
                            isToday -> EnShiBlueLight.copy(alpha = 0.08f)
                            (s + d) % 2 == 0 -> Color(0xFFF2F6FD)
                            else -> Color(0xFFFFFFFF)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(bg)
                        )
                    }
                }
            }
        }

        // ===== 前景层：课程卡片（绝对定位） =====
        blocks.forEach { b ->
            // 冲突处理：同一天同一起始节次的多门课，按课程名排序后左右分栏
            val sameSlot = blocks.filter { it.course.day == b.col && it.start == b.start }
            val idx = sameSlot.sortedBy { it.course.name }.indexOf(b)
            val slotCount = sameSlot.size
            val slotW = if (slotCount > 1) colW / slotCount else colW
            val xOffset = if (slotCount > 1) idx * slotW else 0.dp

            val top = headerH + (b.start - 1) * cellH
            val height = (b.end - b.start + 1) * cellH - 3.dp
            val left = labelW + (b.col - 1) * colW + xOffset

            Box(
                Modifier
                    .offset(x = left, y = top)
                    .width(slotW - 3.dp)
                    .height(height)
                    .background(courseColor(b.course.name), RoundedCornerShape(10.dp))
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        b.course.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (b.end - b.start + 1 >= 3) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF1C2438)
                    )
                    if (b.end - b.start + 1 >= 3) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            b.course.position.ifBlank { "地点待定" },
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFF5A6680)
                        )
                    }
                }
            }
        }
    }
}
