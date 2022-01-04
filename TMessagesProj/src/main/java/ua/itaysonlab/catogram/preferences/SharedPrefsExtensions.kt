package ua.itaysonlab.catogram.preferences.ktx

import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringPreference(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: String,
) : ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>): String = sharedPreferences.getString(key, defaultValue)!!
    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
        sharedPreferences.edit().putString(key, value).commit()
    }
}

class IntPreference(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Int,
) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Int = sharedPreferences.getInt(key, defaultValue)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
        sharedPreferences.edit().putInt(key, value).commit()
    }
}

class BooleanPreference(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Boolean,
) : ReadWriteProperty<Any, Boolean> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = sharedPreferences.getBoolean(key, defaultValue)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).commit()
    }
}

class LongPreference(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Long,
) : ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Long = sharedPreferences.getLong(key, defaultValue)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) {
        sharedPreferences.edit().putLong(key, value).commit()
    }
}

fun SharedPreferences.int(key: String, defaultValue: Int): ReadWriteProperty<Any, Int> = IntPreference(this, key, defaultValue)
fun SharedPreferences.boolean(key: String, defaultValue: Boolean): ReadWriteProperty<Any, Boolean> = BooleanPreference(this, key, defaultValue)
fun SharedPreferences.string(key: String, defaultValue: String): ReadWriteProperty<Any, String> = StringPreference(this, key, defaultValue)
fun SharedPreferences.long(key: String, defaultValue: Long): ReadWriteProperty<Any, Long> = LongPreference(this, key, defaultValue)