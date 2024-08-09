package org.xatirchi.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.ApplicationLoader

object Password {

    private val sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()
    private val gson = Gson()


    private fun getAllUserPasswords(): ArrayList<MyPassword> {
        val passwords = ArrayList<MyPassword>()

        val gson = Gson()
        val str = sharedPreferences.getString("password", "")
        if (str !== "") {
            val type: TypeToken<*> = object : TypeToken<List<MyPassword?>?>() {
            }
            val fromJson = gson.fromJson<ArrayList<MyPassword>>(str, type.type)
            for (markId in fromJson) {
                passwords.add(markId)
            }
        }

        return passwords
    }

    fun myPassword(userId: Long): Boolean {
        val passwords = getAllUserPasswords()
        for (password in passwords) {
            if (password.userId == userId) {
                return true
            }
        }
        return false
    }

    fun checkPassword(enteredPassword: MyPassword): Boolean {
        val passwords = getAllUserPasswords()
        for (password in passwords) {
            if (password.userId == enteredPassword.userId && password.password == enteredPassword.password) {
                return true
            }
        }
        return false
    }

    fun createPassword(myPassword: MyPassword) {
        val passwords = getAllUserPasswords()
        passwords.add(myPassword)
        savePasswords(passwords)
    }

    fun changePassword(oldPassword: MyPassword, newPassword: MyPassword) {
        val passwords = getAllUserPasswords()
        val i = passwords.indexOf(oldPassword)
        passwords[i] = newPassword
        savePasswords(passwords)
    }

    fun removePassword(oldPassword: MyPassword) {
        val passwords = getAllUserPasswords()
        passwords.remove(oldPassword)
        savePasswords(passwords)
    }

    private fun savePasswords(passwords: ArrayList<MyPassword>) {
        val str = gson.toJson(passwords)
        editor.putString("password", str)
        editor.commit()
    }

    fun createTwoStepPassword(twoStepPassword: String) {
        editor.putString("two_step_password", twoStepPassword)
        editor.commit()
    }

    fun checkTwoStepPassword(twoStepPassword: String): Boolean {
        val password = getTwoStepPassword()
        return if (password != "") {
            password == twoStepPassword
        } else {
            false
        }
    }

    fun getTwoStepPassword(): String {
        val password = sharedPreferences.getString("two_step_password", "")
        return password ?: ""
    }

}

data class MyPassword(val password: String, val userId: Long)