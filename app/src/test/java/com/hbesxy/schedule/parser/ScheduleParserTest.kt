package com.hbesxy.schedule.parser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课表解析单元测试（纯 JVM，等价平移自 JS 版 test-parser.js）
 * 注意：正方教务 jcs 为四位数，如 “0102”=第1、2节、“0506”=第5、6节。
 */
class ScheduleParserTest {

    private fun item(name: String, day: Int, jcs: String, zcd: String): JSONObject =
        JSONObject()
            .put("kcmc", name)
            .put("xm", "张老师")
            .put("cdmc", "A101")
            .put("xqj", day)
            .put("jcs", jcs)
            .put("zcd", zcd)

    @Test
    fun parseTwoDigitSections() {
        val arr = JSONArray().put(item("大学英语", 1, "0102", "1-16周"))
        val out = ScheduleParser.parseKbList(arr)
        assertEquals(1, out.size)
        assertEquals(listOf(1, 2), out[0].sections)
        assertEquals((1..16).toList(), out[0].weeks)
    }

    @Test
    fun parseSingleSection() {
        val arr = JSONArray().put(item("体育", 3, "9", "1-16周"))
        val out = ScheduleParser.parseKbList(arr)
        assertEquals(1, out.size)
        assertEquals(listOf(9), out[0].sections)
    }

    @Test
    fun parseRangeSection() {
        val arr = JSONArray().put(item("高数", 2, "1-3节", "1-16周"))
        val out = ScheduleParser.parseKbList(arr)
        assertEquals(listOf(1, 2, 3), out[0].sections)
    }

    @Test
    fun parseEvenWeeks() {
        val arr = JSONArray().put(item("实验", 5, "0506", "2-10周(双)"))
        val out = ScheduleParser.parseKbList(arr)
        assertEquals(1, out.size)
        assertEquals(listOf(5, 6), out[0].sections)
        assertEquals(listOf(2, 4, 6, 8, 10), out[0].weeks)
    }

    @Test
    fun parseSegmentedOddWeeks() {
        val arr = JSONArray().put(item("讲座", 4, "0708", "1-8周,10-16周(单)"))
        val out = ScheduleParser.parseKbList(arr)
        assertEquals(1, out.size)
        assertEquals(listOf(7, 8), out[0].sections)
        // 第一段 “1-8周” 无单双标记，整段保留；第二段 “10-16周(单)” 只留奇数周
        assertEquals((1..8).toList() + listOf(11, 13, 15), out[0].weeks)
    }

    @Test
    fun dropInvalidItem() {
        val bad = JSONObject().put("kcmc", "未知").put("jcs", "")
        val arr = JSONArray().put(bad)
        assertEquals(0, ScheduleParser.parseKbList(arr).size)
    }
}
