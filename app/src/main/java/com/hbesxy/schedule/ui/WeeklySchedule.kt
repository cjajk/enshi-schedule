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

@Composable
fun WeeklyScheduleGrid(courses: List<Course>) {
    val maxSection = (courses.flatMap { it.sections }.maxOrNull() ?: 10).coerceAtLeast(10)
    val slotHeight = 56.dp
    val dayColumnWidth = 100.dp
    val timeColumnWidth = 32.dp
    val scrollState = rememberScrollState()
    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    val todayIndex = remember {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    val palette = listOf(
        Color(0xFFE8F0FE), Color(0xFFFCE8EC), Color(0xFFE6F4EA),
        Color(0xFFFEF3E2), Color(0xFFF3E8FE), Color(0xFFE0F2F1), Color(0xFFFFF8E1)
    )
    val accentDots = listOf(
        Color(0xFF4A7FD9), Color(0xFFE5484D), Color(0xFF30A46C),
        Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFEAB308)
    )
    fun colorIndex(name: String): Int = (name.hashCode() and Int.MAX_VALUE) % palette.size

    val coursesByDay = remember(courses) { courses.groupBy { it.day } }

    Column {
        Row(Modifier.horizontalScroll(scrollState)) {
            Spacer(Modifier.width(timeColumnWidth))
            for (d in 1..7) {
                val isToday = d == todayIndex
                Box(
                    modifier = Modifier
                        .width(dayColumnWidth)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayNames[d - 1],
                            fontSize = 13.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) Accent else InkSecondary
                        )
                        if (isToday) {
                            Spacer(Modifier.height(2.dp))
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Accent)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        Row {
            Column(Modifier.width(timeColumnWidth)) {
                for (s in 1..maxSection) {
                    Box(
                        modifier = Modifier.height(slotHeight),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            "$s",
                            fontSize = 10.sp,
                            color = InkTertiary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            Row(Modifier.horizontalScroll(scrollState)) {
                for (d in 1..7) {
                    val isToday = d == todayIndex
                    Box(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .height(slotHeight * maxSection)
                            .background(if (isToday) Accent.copy(alpha = 0.03f) else Color.Transparent)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            repeat(maxSection) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(slotHeight)
                                        .border(0.5.dp, Divider)
                                )
                            }
                        }

                        for (c in coursesByDay[d].orEmpty()) {
                            val start = c.sections.minOrNull() ?: continue
                            val end = c.sections.maxOrNull() ?: start
                            val span = (end - start + 1).coerceAtLeast(1)
                            val idx = colorIndex(c.name)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                                    .offset(y = slotHeight * (start - 1))
                                    .width(dayColumnWidth - 6.dp)
                                    .height(slotHeight * span - 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette[idx])
                                    .padding(7.dp)
                            ) {
                                Column {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(accentDots[idx])
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        c.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp
                                    )
                                    if (c.position.isNotBlank() && span >= 2) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            c.position,
                                            fontSize = 9.sp,
                                            color = InkSecondary,
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
