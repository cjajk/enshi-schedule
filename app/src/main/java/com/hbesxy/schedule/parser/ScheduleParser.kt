package com.hbesxy.schedule.parser

import com.hbesxy.schedule.model.Course
import org.json.JSONArray
import org.json.JSONObject

object ScheduleParser {

    fun parseKbList(kbList: JSONArray): List<Course> {
        val out = mutableListOf<Course>()
        for (i in 0 until kbList.length()) {
            parseItem(kbList.getJSONObject(i))?.let { out.add(it) }
        }
        return out
    }

    private fun parseItem(item: JSONObject): Course? {
        val name = (item.optString("kcmc").ifBlank { item.optString("kcm") }).trim()
        if (name.isEmpty()) return null
        val teacher = item.optString("xm").trim()
        val position = item.optString("cdmc").trim()
        val day = item.optInt("xqj", 0)
        if (day !in 1..7) return null
        val sections = parseSections(item.optString("jcs"))
        if (sections.isEmpty()) return null
        val weeks = parseWeeks(item.optString("zcd"))
        if (weeks.isEmpty()) return null
        return Course(name, teacher, position, day, weeks, sections)
    }

    private fun parseSections(jcs: String): List<Int> {
        val raw = jcs.trim()
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<Int>()

        if (raw.all { it.isDigit() }) {
            if (raw.length % 2 == 0) {
                var i = 0
                while (i + 2 <= raw.length) {
                    val sec = raw.substring(i, i + 2).toIntOrNull() ?: 0
                    if (sec in 1..30) out.add(sec)
                    i += 2
                }
            } else {
                val n = raw.toIntOrNull() ?: 0
                if (n in 1..30) out.add(n)
            }
            return dedupe(out)
        }

        val range = Regex("""(\d+)\s*[-—~]\s*(\d+)""").find(raw)
        if (range != null) {
            val a = range.groupValues[1].toInt()
            val b = range.groupValues[2].toInt()
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            for (s in lo..hi) if (s in 1..30) out.add(s)
            return dedupe(out)
        }

        val nums = Regex("""\d+""").findAll(raw)
        for (m in nums) {
            val s = m.value.toIntOrNull() ?: 0
            if (s in 1..30) out.add(s)
        }
        return dedupe(out)
    }

    private fun parseWeeks(zcd: String): List<Int> {
        val raw = zcd.trim()
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<Int>()
        val parts = raw.split(Regex("[,，、]"))
        for (part in parts) {
            val seg = part.replace("周", "").trim()
            if (seg.isEmpty()) continue
            val single = Regex("""^(\d+)$""").find(seg)
            if (single != null) {
                val w = single.groupValues[1].toIntOrNull() ?: 0
                if (w in 1..30) out.add(w)
                continue
            }
            val range = Regex("""(\d+)\s*[-—~]\s*(\d+)""").find(seg)
            if (range != null) {
                val lo = range.groupValues[1].toInt()
                val hi = range.groupValues[2].toInt()
                val odd = part.contains("单")
                val even = part.contains("双")
                for (w in lo..hi) {
                    if (odd && w % 2 != 1) continue
                    if (even && w % 2 != 0) continue
                    if (w in 1..30) out.add(w)
                }
            }
        }
        return dedupe(out)
    }

    private fun dedupe(list: List<Int>): List<Int> = list.distinct().sorted()
}
