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

class ZhengFangClient(baseUrl: String) {

    private val jwglxt = normalizeBaseUrl(baseUrl) + "/jwglxt"

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

    private fun normalizePassword(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '\u3000' -> sb.append(' ')
                c in '\uFF01'..'\uFF5E' -> sb.append((c - 0xFEE0).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun login(username: String, password: String): LoginResult {
        try {
            val pwd = normalizePassword(password)
            val csrf = fetchCsrfToken()
                ?: return LoginResult(false, "未解析到 csrftoken，教务登录页结构可能已变化")
            val pk = fetchPublicKey()
                ?: return LoginResult(false, "无法获取 RSA 加密公钥")
            val encPwd = RsaUtil.encryptPassword(pwd, pk.first, pk.second)
            if (DEBUG) Log.d(TAG, "login user=$username pwd_len=${pwd.length}")
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
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return LoginResult(false, "登录响应为空")
                if (DEBUG) Log.d(TAG, "login resp code=${resp.code} len=${body.length}")
                extractLoginError(body)?.let { return LoginResult(false, it) }
                val stillOnLogin = body.contains("login_slogin.html") && !body.contains("index_")
                if (stillOnLogin) {
                    return LoginResult(false, "登录未成功：请检查账号密码；若登录页出现验证码，请先在网页登录一次")
                }
                return LoginResult(true, "登录成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "登录异常", e)
            return LoginResult(false, "登录异常：" + (e.message ?: e.javaClass.simpleName))
        }
    }

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
            } catch (e: Exception) { }
        }
        return null
    }

    companion object {
        private const val TAG = "EnShiSchedule"
        private const val DEBUG = false
    }
}

private class SimpleCookieJar : CookieJar {
    private val store = HashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = store.getOrPut(url.host) { HashMap() }
        for (c in cookies) {
            if (c.expiresAt < System.currentTimeMillis()) continue
            m[c.name] = c
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.values?.filter { it.expiresAt >= System.currentTimeMillis() } ?: emptyList()
}
