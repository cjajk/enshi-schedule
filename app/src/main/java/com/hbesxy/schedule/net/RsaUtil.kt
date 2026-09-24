package com.hbesxy.schedule.net

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * 正方教务系统密码 RSA 加密（PKCS1 v1.5）
 * 对应原适配脚本中 JSEncrypt 的加密方式。
 */
object RsaUtil {

    fun encryptPassword(password: String, modulusHex: String, exponentHex: String): String {
        val modulus = parseBigInt(modulusHex)
        val exponent = parseBigInt(exponentHex)
        val spec = RSAPublicKeySpec(modulus, exponent)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 兼容两种公钥格式：
     * 1) 十六进制 modulus/exponent（部分正方系统）
     * 2) Base64 编码的 modulus/exponent（恩施学院实测：如 AQAB / ALxhr...）
     * 纯 0-9a-f 视为 hex，否则按 Base64 解码为字节。
     */
    private fun parseBigInt(s: String): BigInteger {
        val t = s.trim()
        return if (t.matches(Regex("[0-9a-fA-F]+"))) BigInteger(t, 16)
        else BigInteger(1, Base64.decode(t, Base64.DEFAULT))
    }
}
