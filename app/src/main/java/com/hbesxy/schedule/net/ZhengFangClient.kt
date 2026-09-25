package com.hbesxy.schedule.net

import android.util.Log
import com.hbesxy.schedule.model.LoginResult
import com.hbesxy.schedule.model.ScheduleRaw
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 正方教务系统客户端（jwglxt / zftal-ui-v5）
 * --------------------------------------------------
 * 实现流程：获取登录页(拿 csrftoken) -> 获取 RSA 公钥 -> 加密密码登录 -> 维持 cookie -> 拉课表
 * 教务入口：http://jw.hbesxy.net/jwglxt/
 */
class ZhengFangClient(baseUrl: String) {

    private val jwglxt = normalizeBaseUrl(baseUrl) + "/jwglxt"

    /** 规范化教务入口：去所有空白字符、补全 scheme、非法回退官方地址 */
    private fun normalizeBaseUrl(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), "")
        if (s.isEmpty()) s = "http://jw.hbesxy.net"
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s.trimEnd('/')
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(SimpleCookieJar())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 1. 获取登录页并解析出 csrftoken */
    private fun fetchCsrfToken(): String? {
        val req = Request.Builder().url("$jwglxt/xtgl/login_slogin.html").build()
        client.newCall(req).execute().use { resp ->
            val html = resp.body?.string() ?: return null
            return extractCsrfToken(html)
        }
    }

    private fun extractCsrfToken(html: String): String? {
        val patterns = listOf(
            Regex("""csrftoken\s*=\s*"([^"]+)"""),
            Regex("""name="csrftoken"\s+value="([^"]+)"""),
            Regex("""csrftoken\s*=\s*'([^']+)'""")
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null && m.groupValues[1].isNotBlank()) return m.groupValues[1]
        }
        return null
    }

    /** 2. 获取 RSA 公钥 (modulus, exponent) */
    private fun fetchPublicKey(): Pair<String, String>? {
        val req = Request.Builder().url("$jwglxt/xtgl/login_getPublicKey.html").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return null
            val j = JSONObject(text)
            val modulus = j.optString("modulus")
            val exponent = j.optString("exponent")
            if (modulus.isNotEmpty() && exponent.isNotEmpty()) return modulus to exponent
        }
        return null
    }

    /** 全角标点转半角（教务系统密码只认半角，全角感叹号/括号等会导致密码错误） */
    private fun normalizePassword(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '\u3000' -> sb.append(' ')          // 全角空格
                c in '\uFF01'..'\uFF5E' -> sb.append((c - 0xFEE0).toChar())  // 全角标点/字母数字 → 半角
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 登录；成功后 client 已持有会话 cookie */
    fun login(username: String, password: String): LoginResult {
        try {
            val pwd = normalizePassword(password)
            val csrf = fetchCsrfToken()
                ?: return LoginResult(false, "未解析到 csrftoken，教务登录页结构可能已变化")
            val pk = fetchPublicKey()
                ?: return LoginResult(false, "无法获取 RSA 加密公钥")
            val encPwd = RsaUtil.encryptPassword(pwd, pk.first, pk.second)
            Log.e("EnShiSchedule", "LOGIN user=$username pwd_len=${pwd.length} pwd_fullwidth=${pwd.any { it in '\uFF01'..'\uFF5E' }} csrf=$csrf mod=${pk.first.take(24)} exp=${pk.second} mm=${encPwd.take(24)}")

            val form = FormBody.Builder()
                .add("csrftoken", csrf)
                .add("yhm", username)
                .add("mm", encPwd)
                .build()

            val req = Request.Builder()
                .url("$jwglxt/xtgl/login_slogin.html")
                .post(form)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            Log.e("EnShiSchedule", "COOKIES=${(client.cookieJar as SimpleCookieJar).describe()}")
            Log.e("EnShiSchedule", "BODY csrftoken=$csrf yhm=$username mm=$encPwd")

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return LoginResult(false, "登录响应为空")
                Log.e("EnShiSchedule", "LOGIN resp code=${resp.code} len=${body.length} url=${resp.request.url}")
                Log.e("EnShiSchedule", "RESPCODE up=${body.contains("updatePassword")} idx=${body.contains("index_")} pwderr=${body.contains("用户名或密码不正确")}")
                Log.e("EnShiSchedule", "RESPHEAD=" + body.take(900).replace("\n", " "))
                body.lines().forEach { line ->
                    val t = line.trim()
                    if (t.contains("用户名") || t.contains("密码") || t.contains("验证码") || t.contains("错误")) {
                        Log.e("EnShiSchedule", "RESP: " + t.take(120))
                    }
                }
                extractLoginError(body)?.let { return LoginResult(false, it) }
                val stillOnLogin = body.contains("login_slogin.html") && !body.contains("index_")
                if (stillOnLogin) {
                    return LoginResult(false, "登录未成功：请检查账号密码；若登录页出现验证码，请先在网页登录一次")
                }
                return LoginResult(true, "登录成功")
            }
        } catch (e: Exception) {
            Log.e("EnShiSchedule", "登录异常", e)
            return LoginResult(false, "登录异常：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** 从登录响应中识别明确的错误关键字 */
    private fun extractLoginError(body: String): String? {
        val markers = listOf(
            "用户名或密码不正确" to "账号或密码错误",
            "密码错误" to "密码错误",
            "验证码错误" to "验证码错误",
            "请输入验证码" to "需要输入验证码，请先在网页登录一次再使用本应用",
            "用户不存在" to "账号不存在"
        )
        for ((kw, msg) in markers) if (body.contains(kw)) return msg
        return null
    }

    /** 拉取课表原始数据（需先登录） */
    fun fetchSchedule(xnm: String, xqm: String): ScheduleRaw? {
        val form = FormBody.Builder()
            .add("xnm", xnm)
            .add("xqm", xqm)
            .add("kzlx", "ck")
            .add("xsdm", "")
            .build()

        val candidates = listOf(
            "$jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
            "$jwglxt/kbcx/xsxskbcx_cxXsxsb.html?gnmkdm=N2151"
        )
        for (url in candidates) {
            val req = Request.Builder()
                .url(url)
                .post(form)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string()
                    if (text != null) {
                        val j = JSONObject(text)
                        if (j.has("kbList")) {
                            val arr = j.getJSONArray("kbList")
                            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                            return ScheduleRaw(xnm, xqm, list)
                        }
                    }
                }
            } catch (e: Exception) { /* 尝试下一个候选接口 */ }
        }
        return null
    }
}

/** 简易内存 cookie 容器（每次登录重新建立会话） */
private class SimpleCookieJar : CookieJar {
    private val store = HashMap<String, MutableList<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.getOrPut(url.host) { mutableListOf() }.apply {
            clear()
            addAll(cookies)
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host] ?: emptyList()

    fun describe(): String {
        if (store.isEmpty()) return "EMPTY"
        return store.entries.joinToString(";") { (h, cs) -> h + "=" + cs.joinToString(",") { it.name() + ":" + it.value().take(12) } }
    }
}
