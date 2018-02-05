package org.cloudveil.messenger.api.service;


import org.cloudveil.messenger.api.model.request.SettingsRequest;
import org.cloudveil.messenger.api.model.response.SettingsResponse;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MessengerHttpInterface {
    @POST("settings")
    Observable<SettingsResponse> loadSettings(@Body SettingsRequest request);

}