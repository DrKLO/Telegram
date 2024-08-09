package org.xatirchi.utils

import org.apache.commons.codec.binary.Base64
import java.security.MessageDigest

object AESUtil {

    private val kluch = "topdingmiGapYoqTgdan@adhambec-gaYozSovgaBor"

    fun generateCodeForQr(): String {
        val randomData = getRandomString(10)
        val strGson = "{\"step\":\"get\",\"$randomData\":\"${randomData.reversed()}\"}"
        val yopiqRandomData = removeEquals(yopish(strGson))
        val md5Data = md5(yopiqRandomData)
        return setEqual("$yopiqRandomData$md5Data")
    }

    fun generateUserInfo(uid: String, password: String): String {
        val randomData = getRandomString(10)
        val strGson =
            "{\"step\":\"check\",\"$randomData\":\"${randomData.reversed()}\",\"uid\":\"$uid\",\"pass\":\"$password\"}"
        val yopiqRandomData = removeEquals(yopish(strGson))
        val md5Data = md5(yopiqRandomData)
        return setEqual("$yopiqRandomData$md5Data")
    }

    fun generateSubscribeCheck(id:String): String {
        val randomData = getRandomString(10)
        val strGson =
            "{\"step\":\"wakeup\",\"$randomData\":\"${randomData.reversed()}\",\"id\":\"$id\"}"
        val yopiqRandomData = removeEquals(yopish(strGson))
        val md5Data = md5(yopiqRandomData)
        return setEqual("$yopiqRandomData$md5Data")
    }

    fun openCode(code: String): String {
        val withoutEquals = removeEquals(code)
        var md5 = ""
        try {
            var count = 0
            for (i in withoutEquals.length - 1 downTo 0) {
                if (count <= 31) {
                    md5 = "$md5${withoutEquals[i]}"
                    count++
                } else {
                    break
                }
            }
        } catch (e: Exception) {
        }
        val yopiqCode = withoutEquals.replace(md5.reversed(), "")
        return ochish((yopiqCode + "=="))
    }

    fun setEqual(str: String): String {
        return str.split("").joinToString("=")
    }

    fun removeEquals(str: String): String {
        return str.replace("=", "")
    }

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    fun yopish(mal: String): String {
        val yop = mal.mapIndexed { i, char ->
            (char.toInt() xor kluch[i % kluch.length].toInt()).toChar()
        }.joinToString("")
        return String(Base64.encodeBase64(yop.toByteArray()))
    }

    fun ochish(mal: String): String {
        val yop = String(Base64.decodeBase64(mal.toByteArray()))
        return yop.mapIndexed { i, char ->
            (char.toInt() xor kluch[i % kluch.length].toInt()).toChar()
        }.joinToString("")
    }
}