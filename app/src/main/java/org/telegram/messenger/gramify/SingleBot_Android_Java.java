// Devgram Android ke andar Single Bot Integration - Java (OkHttp)
// Ye code aapke Settings > Devgram Bot me daal sakte ho
// No flooding bypass - proper error handling

package org.telegram.messenger.gramify;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class SingleBot_Android_Java {

    private static final OkHttpClient client = new OkHttpClient();
    private String botToken;

    public SingleBot_Android_Java(String botToken) {
        this.botToken = botToken; // Settings se aayega
    }

    // 1. Group ka naam change - SAFE
    public void setChatTitle(long chatId, String newTitle, Callback callback) {
        if (newTitle.length() < 1 || newTitle.length() > 128) {
            callback.onFailure(null, new IOException("Title 1-128 chars only"));
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.telegram.org/bot" + botToken + "/setChatTitle").newBuilder()
                .addQueryParameter("chat_id", String.valueOf(chatId))
                .addQueryParameter("title", newTitle)
                .build();

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { callback.onFailure(call, e); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                JSONObject json = new JSONObject();
                try { json = new JSONObject(body); } catch (Exception ignore) {}
                
                // FloodWait handle - Telegram returns 429
                if (body.contains("Too Many Requests") || body.contains("retry_after")) {
                    // IMPORTANT: yahan rukna padega, bypass nahi karna
                    callback.onFailure(call, new IOException("FloodWait: " + body));
                } else {
                    callback.onResponse(call, response);
                }
            }
        });
    }

    // 2. Message Send - SAFE
    public void sendMessage(long chatId, String text, Callback callback) {
        if (text.isEmpty() || text.length() > 4096) {
            callback.onFailure(null, new IOException("Message empty or too long"));
            return;
        }

        RequestBody form = new FormBody.Builder()
                .add("chat_id", String.valueOf(chatId))
                .add("text", text)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/sendMessage")
                .post(form)
                .build();

        client.newCall(request).enqueue(callback);
    }

    // Usage example in Devgram Settings:
    /*
    SingleBot_Android_Java bot = new SingleBot_Android_Java("56530:YOUR_TOKEN");
    bot.setChatTitle(-100123456789L, "Devgram Official", new Callback() {...});
    bot.sendMessage(-100123456789L, "Hello Devgram!", new Callback() {...});
    
    IMPORTANT: 
    - chatId group ka hona chahiye jahan bot admin hai
    - Har setChatTitle ke baad 60 sec ka gap rakho warna FloodWait ayega
    */
}
