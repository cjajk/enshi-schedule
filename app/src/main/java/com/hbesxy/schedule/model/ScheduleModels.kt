package com.hbesxy.schedule.model

data class Course(
    val name: String,
    val teacher: String,
    val position: String,
    val day: Int,
    val weeks: List<Int>,
    val sections: List<Int>
) {
    fun fingerprint(): String =
        "$name|$teacher|$position|$day|${weeks.joinToString(",")}|${sections.joinToString(",")}"
}

data class ScheduleSnapshot(
    val xnm: String,
    val xqm: String,
    val courses: List<Course>,
    val fetchedAt: Long
) {
    fun fingerprint(): String = courses.map { it.fingerprint() }.sorted().joinToString("\n")
}

data class LoginResult(val ok: Boolean, val message: String)

data class ScheduleRaw(
    val xnm: String,
    val xqm: String,
    val kbList: List<org.json.JSONObject>
)

object TermMap {
    const val FIRST = "3"
    const val SECOND = "12"
    const val SHORT = "16"
    val names = mapOf("3" to "第一学期", "12" to "第二学期", "16" to "短学期")
    fun toCode(term: String): String = when (term) {
        "1" -> FIRST
        "2" -> SECOND
        "3" -> SHORT
        else -> FIRST
    }
}
