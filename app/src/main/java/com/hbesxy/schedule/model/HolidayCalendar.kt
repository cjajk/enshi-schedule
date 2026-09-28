package com.hbesxy.schedule.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 2026 年国家法定节假日与调休补班安排
 * 依据：国务院办公厅《关于2026年部分节假日安排的通知》（国办发明电〔2025〕7号，2025-11-04 发布）
 *
 * 规则：
 *  - 法定节假日：当天不上课，课表/今日概览/小组件隐藏课程并显示节日名；
 *  - 调休补班日（周末上班）：当天按正常工作日上课。
 */
object HolidayCalendar {

    /** 法定节假日：yyyy-MM-dd -> 节日名称 */
    private val holidays: Map<String, String> = mapOf(
        // 元旦：1月1日(周四)至3日(周六)放假调休，共3天
        "2026-01-01" to "元旦", "2026-01-02" to "元旦", "2026-01-03" to "元旦",
        // 春节：2月15日(周日)至23日(周一)放假调休，共9天
        "2026-02-15" to "春节", "2026-02-16" to "春节", "2026-02-17" to "春节",
        "2026-02-18" to "春节", "2026-02-19" to "春节", "2026-02-20" to "春节",
        "2026-02-21" to "春节", "2026-02-22" to "春节", "2026-02-23" to "春节",
        // 清明节：4月4日(周六)至6日(周一)放假，共3天
        "2026-04-04" to "清明节", "2026-04-05" to "清明节", "2026-04-06" to "清明节",
        // 劳动节：5月1日(周五)至5日(周二)放假调休，共5天
        "2026-05-01" to "劳动节", "2026-05-02" to "劳动节", "2026-05-03" to "劳动节",
        "2026-05-04" to "劳动节", "2026-05-05" to "劳动节",
        // 端午节：6月19日(周五)至21日(周日)放假，共3天
        "2026-06-19" to "端午节", "2026-06-20" to "端午节", "2026-06-21" to "端午节",
        // 中秋节：9月25日(周五)至27日(周日)放假，共3天
        "2026-09-25" to "中秋节", "2026-09-26" to "中秋节", "2026-09-27" to "中秋节",
        // 国庆节：10月1日(周四)至7日(周三)放假调休，共7天
        "2026-10-01" to "国庆节", "2026-10-02" to "国庆节", "2026-10-03" to "国庆节",
        "2026-10-04" to "国庆节", "2026-10-05" to "国庆节", "2026-10-06" to "国庆节",
        "2026-10-07" to "国庆节"
    )

    /** 调休补班日（周末上班）：1月4日、2月14日、2月28日、5月9日、9月20日、10月10日 */
    private val makeupWorkdays: Set<String> = setOf(
        "2026-01-04", "2026-02-14", "2026-02-28",
        "2026-05-09", "2026-09-20", "2026-10-10"
    )

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    private fun key(date: Calendar): String = fmt.format(date.time)

    /** 法定节假日名称；非节假日返回 null */
    fun holidayName(date: Calendar): String? = holidays[key(date)]

    /** 是否为法定节假日（当天不上课） */
    fun isHoliday(date: Calendar): Boolean = holidays.containsKey(key(date))

    /** 是否为调休补班日（周末上班，当天按工作日上课） */
    fun isMakeupWorkday(date: Calendar): Boolean = makeupWorkdays.contains(key(date))

    /** 当天是否上课：法定节假日不上课；平日与调休补班日均按课表上课 */
    fun isClassDay(date: Calendar): Boolean = !isHoliday(date)
}
