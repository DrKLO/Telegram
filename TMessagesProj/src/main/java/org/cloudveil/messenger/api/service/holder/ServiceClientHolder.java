package org.cloudveil.messenger.api.service.holder;

import android.util.Base64;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.cloudveil.messenger.api.service.interceptor.HttpLoggingInterceptor;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

class ServiceClientHolder<T> {
    private T instance;

    public ServiceClientHolder(String endpoint, Class<T> clazz) {
        final Retrofit restAdapter = createRestAdapter(endpoint);
        instance = restAdapter.create(clazz);
    }

    protected Retrofit createRestAdapter(String endpoint) {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
                .create();

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        okHttpClientBuilder.connectTimeout(1, TimeUnit.MINUTES);
        okHttpClientBuilder.readTimeout(1, TimeUnit.MINUTES);
        okHttpClientBuilder.writeTimeout(1, TimeUnit.MINUTES);

        okHttpClientBuilder.interceptors().add(interceptor);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(endpoint)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(okHttpClientBuilder.build())
                .build();

        return retrofit;
    }

    public T getInterface() {
        return instance;
    }

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.decode(json.getAsString(), Base64.NO_WRAP);
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
        }
    }
}
