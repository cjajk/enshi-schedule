package com.hbesxy.schedule.net

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

class ZhengFangClient(private val baseUrl: String) {

    private val jwglxt = baseUrl.trimEnd('/') + "/jwglxt"

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

    fun login(username: String, password: String): LoginResult {
        try {
            val csrf = fetchCsrfToken()
                ?: return LoginResult(false, "未解析到 csrftoken，教务登录页结构可能已变化")
            val pk = fetchPublicKey()
                ?: return LoginResult(false, "无法获取 RSA 加密公钥")
            val encPwd = RsaUtil.encryptPassword(password, pk.first, pk.second)

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
                val stillLoginPage = body.contains("login_slogin.html") && !body.contains("index_")
                return if (stillLoginPage) {
                    LoginResult(false, "登录失败：账号或密码错误")
                } else {
                    LoginResult(true, "登录成功")
                }
            }
        } catch (e: Exception) {
            return LoginResult(false, "登录异常：" + (e.message ?: e.javaClass.simpleName))
        }
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
            } catch (e: Exception) { /* 尝试下一个候选接口 */ }
        }
        return null
    }
}

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
}
