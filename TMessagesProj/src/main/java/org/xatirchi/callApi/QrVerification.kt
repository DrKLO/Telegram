package org.xatirchi.callApi

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.widget.Toast
import com.google.android.exoplayer2.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.ApplicationLoader
import org.xatirchi.api.ApiService
import org.xatirchi.api.RetrofitClient
import org.xatirchi.callApi.modul.QR
import org.xatirchi.callApi.modul.UserInfo
import org.xatirchi.utils.AESUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class QrVerification(context: Context) {

    var sharedPreferences: SharedPreferences
    var editor: Editor

    init {
        sharedPreferences = context.getSharedPreferences("db", MODE_PRIVATE)
        editor = sharedPreferences.edit()
    }

    val apiService = RetrofitClient.instance.create(ApiService::class.java)

    fun subscribe(clientId: Long, callback: (String?) -> Unit) {
        val id = sharedPreferences.getString("id$clientId", "")
        try {
            apiService.subscribe(AESUtil.generateSubscribeCheck(id ?: ""))
                .enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful) {
                            val result = AESUtil.openCode(response.body() ?: "")
                            callback(result)
//                        Log.d("QrVerification", "onResponse: " + result)
                        } else {
                            callback("try again!")
//                        Log.d("QrVerification", "onResponse: " + response.body() + response.code())
                        }
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        callback("try again!")
//                    Log.d("QrVerification", "onFailure: " + t.message)
                    }
                })
        } catch (e: Exception) {
        }
    }

    fun sendTwoStepPassword(clientId: Long, uid: String, pass: String) {
//        var uidCache = sharedPreferences.getString("uid", "")
//        var password = "Adhambek"
        try {
            apiService.login(AESUtil.generateUserInfo(uid ?: "", pass))
                .enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful) {
                            val id = AESUtil.openCode(response.body().toString())
                            editor.putString("id$clientId", id)
                            editor.commit()
//                        Log.d("QrVerification", "Response: $id")
                        } else {
//                        Log.d("QrVerification", "Response: " + response.body() + response.code())
                        }
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
//                    Log.d("QrVerification", "onFailure: " + t.message)
                    }

                })
        } catch (e: Exception) {
        }
    }

    fun getQr(clientId: Long, callback: (QR?) -> Unit) {
        try {
            apiService.getQrCode(AESUtil.generateCodeForQr()).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        if (response.body() != null) {
                            val gsonStr = AESUtil.openCode(response.body().toString())
//                        Log.d(
//                            "QrVerification",
//                            "Response: $gsonStr"
//                        )
                            try {
                                val gson = Gson()
                                if (gsonStr != "") {
                                    val type = object : TypeToken<QR>() {}.type
                                    val qr = gson.fromJson<QR>(gsonStr, type)
//                                Log.d("QrVerification", "onResponse: " + qr.uid)
//                                callback(qr)
                                    Toast.makeText(
                                        ApplicationLoader.applicationContext,
                                        "Keyin o'zgartir qr ni",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    editor.putString("uid$clientId", qr.uid)
                                    editor.commit()
                                }
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        callback(null)
//                    Log.d("QrVerification", "Response error: ${response.errorBody()}")
                    }
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    callback(null)
//                Log.d("QrVerification", "Network error: ${t.message}")
                }
            })
        } catch (e: Exception) {
        }
    }
}