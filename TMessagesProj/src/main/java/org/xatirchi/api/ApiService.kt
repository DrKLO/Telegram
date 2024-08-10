package org.xatirchi.api

import org.xatirchi.callApi.modul.Future
import org.xatirchi.callApi.modul.QR
import org.xatirchi.callApi.modul.UserInfo
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("login/key_log.php?step=get")
    fun getKeyLog(): Call<QR>

    @POST("login/key_log.php?")
    fun getQrCode(
        @Body str: String
    ): Call<String>

    @POST("/login/key_log.php?")
    fun login(
        @Body userInfo:String
    ): Call<String>

    @POST("login/key_log.php?")
    fun subscribe(
        @Body id:String
    ): Call<String>

    @GET("sv.php")
    fun dialogData(): Call<ArrayList<Future>>
}