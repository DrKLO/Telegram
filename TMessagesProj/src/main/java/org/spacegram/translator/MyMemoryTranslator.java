package org.spacegram.translator;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class MyMemoryTranslator implements BaseTranslator {

    private static final String API_URL = "https://api.mymemory.translated.net/get";
    private String apiKey;

    public MyMemoryTranslator() {
        this.apiKey = ""; // Optional - increases quota if provided
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void translate(String text, String fromLang, String toLang, Utilities.Callback2<String, Boolean> done) {
        if (TextUtils.isEmpty(text) || done == null) return;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String encodedText = Uri.encode(text);
                String langPair = (TextUtils.isEmpty(fromLang) || fromLang.equals("auto") ? "auto" : fromLang) + "|" + toLang;
                
                String url = API_URL + "?q=" + encodedText + "&langpair=" + Uri.encode(langPair);
                if (!TextUtils.isEmpty(apiKey)) {
                    url += "&key=" + apiKey;
                }

                connection = (HttpURLConnection) new URI(url).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    boolean rateLimit = responseCode == 429;
                    AndroidUtilities.runOnUIThread(() -> done.run(null, rateLimit));
                    return;
                }

                StringBuilder resultBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        resultBuilder.append(line);
                    }
                }

                String jsonResponse = resultBuilder.toString();
                JSONObject response = new JSONObject(jsonResponse);
                
                // Check if quota is finished
                if (response.optBoolean("quotaFinished", false)) {
                    AndroidUtilities.runOnUIThread(() -> done.run(null, true));
                    return;
                }

                JSONObject responseData = response.getJSONObject("responseData");
                String translatedText = responseData.getString("translatedText");

                AndroidUtilities.runOnUIThread(() -> done.run(translatedText, false));

            } catch (Exception e) {
                Log.e("MyMemoryTranslator", "Translation failed", e);
                AndroidUtilities.runOnUIThread(() -> done.run(null, false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
