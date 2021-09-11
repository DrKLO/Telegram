package org.telegram.api

import okhttp3.RequestBody
import org.telegram.PARTNER_ID
import org.telegram.database.User

import retrofit2.Call
import retrofit2.http.*

internal interface Login {
    @Headers("partner_id: $PARTNER_ID", "Content-Type: application/json")
    @POST("/api/v1/clients/")
    fun auth(@Body body: RequestBody): Call<User>
} 