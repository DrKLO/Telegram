package org.telegram.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import org.telegram.BASE_URL
import org.telegram.PARTNER_ID
import org.telegram.database.User
import org.telegram.ui.login.LoginPuttingData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class LoginController(private val context: Context) {
    private fun service(): Retrofit {


        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)

            .callTimeout(1, TimeUnit.MINUTES)
            .connectTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(client.build())
            .build()
    }

    fun authUser(data: LoginPuttingData, loginListener: LoginListener) {
        val body: RequestBody =
            RequestBody.create("application/json".toMediaTypeOrNull(), Gson().toJson(data))
        service().create(Login::class.java).auth(body).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                Log.d(
                    "login_auth",
                    "data: ${response.body()} | ${response.message()} |E ${response.errorBody()} | ${call.request().url} ${response.raw().code}"
                )
                if (response.raw().code == 201) {
                    loginListener.onSuccess(response.body())
                } else {
                    try {
                        val data = JSONObject(call.request().body.toString())["statusCode"].toString()
                        if (data.isNotEmpty() && data != "null") {
                            loginListener.onFailure("Client with this email already registered")
                        } else {
                            loginListener.onFailure()
                        }
                    } catch (e: Exception) {
                        loginListener.onFailure()
                    }
                }

            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                Log.d("login_auth", "error: ${t.message} |")
                try {
                    val data = JSONObject(call.request().body.toString())["statusCode"].toString()
                    if (data.isNotEmpty() && data != "null") {
                        loginListener.onFailure("Client with this email already registered")
                    } else {
                        loginListener.onFailure()
                    }
                } catch (e: Exception) {
                    loginListener.onFailure()
                }
            }
        })
    }
}

interface LoginListener {
    fun onSuccess(data: User?)
    fun onFailure(message: String = "")
}