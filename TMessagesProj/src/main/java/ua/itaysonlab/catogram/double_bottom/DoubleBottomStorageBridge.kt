package ua.itaysonlab.catogram.double_bottom

import android.app.Activity
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import ua.itaysonlab.catogram.preferences.ktx.boolean
import ua.itaysonlab.catogram.preferences.ktx.int
import ua.itaysonlab.catogram.preferences.ktx.long

// Used for storing data
object DoubleBottomStorageBridge {
    const val DB_TIMER_END = 2 * 60 * 1000 // 2 minutes
    //const val DB_TIMER_END = 25 * 60 * 1000 // 25 minutes for dev version

    private val preferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("dbconfig", Activity.MODE_PRIVATE)

    // Preferences
    var dbTimerExpireDate by preferences.long("db_expiredate", System.currentTimeMillis())
    var fingerprintAccount by preferences.int("db_fp_login", -1)
    var hideAccountsInSwitcher by preferences.boolean("db_hide_accs", false)

    var storageInstance = DBCoreStorage(JSONObject(preferences.getString("db_data", "{}")!!))
        set(value) {
            field = value
            preferences.edit().putString("db_data", value.asJson().toString()).apply()
        }

    // Models

    data class DBCoreStorage(
            val map: MutableMap<String, DBAccountData>,
    ) {
        constructor(json: JSONObject) : this(
                map = toMapTyped<JSONObject>(json).mapValues { DBAccountData(it.value) }.toMutableMap()
        )

        fun asJson(): JSONObject = toJson(map.mapValues { it.value.asJson() })
    }

    data class DBAccountData(
        val id: Long,
        val type: Int,
        val salt: String,
        val hash: String,
    ) {
        constructor(json: JSONObject) : this(
                id = json.getLong("id"),
                type = json.getInt("type"),
                salt = json.getString("pwd_salt"),
                hash = json.getString("pwd_hash"),
        )

        fun asJson(): JSONObject = JSONObject().also {
            it.put("id", id)
            it.put("type", type)
            it.put("pwd_salt", salt)
            it.put("pwd_hash", hash)
        }
    }

    // Util methods

    fun toMap(jsonobj: JSONObject): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()

        val keys = jsonobj.keys()
        while (keys.hasNext()) {
            val key = keys.next()

            var value = jsonobj[key]
            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }

            map[key] = value
        }

        return map
    }

    fun <T> toMapTyped(jsonobj: JSONObject): MutableMap<String, T> {
        val map: MutableMap<String, T> = HashMap()

        val keys = jsonobj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonobj[key] as T
        }

        return map
    }

    fun toJson(map: Map<String, Any>): JSONObject {
        val obj = JSONObject()

        map.entries.forEach {
            obj.put(it.key, it.value)
        }

        return obj
    }

    fun toList(array: JSONArray): List<Any> {
        val list: MutableList<Any> = ArrayList()

        for (i in 0 until array.length()) {
            var value = array[i]
            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }
            list.add(value)
        }

        return list
    }
}