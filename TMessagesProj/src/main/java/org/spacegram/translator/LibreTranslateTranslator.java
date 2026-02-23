package org.spacegram.translator;

import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class LibreTranslateTranslator implements BaseTranslator {

    private static final String DEFAULT_API_URL = "https://libretranslate.com/translate";
    private String apiUrl;
    private String apiKey;

    public LibreTranslateTranslator() {
        this.apiUrl = DEFAULT_API_URL;
        this.apiKey = ""; // Optional for self-hosted instances
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
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
                URI uri = new URI(apiUrl);
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                if (!TextUtils.isEmpty(apiKey)) {
                    connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);

                // Build JSON body
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("q", text);
                jsonBody.put("source", TextUtils.isEmpty(fromLang) || fromLang.equals("auto") ? "auto" : fromLang);
                jsonBody.put("target", toLang);
                jsonBody.put("format", "text");

                byte[] postDataBytes = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postDataBytes);
                }

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
                String translatedText = response.getString("translatedText");

                AndroidUtilities.runOnUIThread(() -> done.run(translatedText, false));

            } catch (Exception e) {
                Log.e("LibreTranslator", "Translation failed", e);
                AndroidUtilities.runOnUIThread(() -> done.run(null, false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
