package org.spacegram.translator;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class DeepLTranslator implements BaseTranslator {

    private static final String API_URL = "https://api-free.deepl.com/v2/translate";
    private String apiKey;

    public DeepLTranslator() {
        this.apiKey = ""; // Users can set their own key in settings
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
                URI uri = new URI(API_URL);
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                if (!TextUtils.isEmpty(apiKey)) {
                    connection.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                // Build POST data
                StringBuilder postData = new StringBuilder();
                postData.append("text=").append(Uri.encode(text));
                postData.append("&target_lang=").append(toLang.toUpperCase());
                if (!TextUtils.isEmpty(fromLang) && !fromLang.equals("auto")) {
                    postData.append("&source_lang=").append(fromLang.toUpperCase());
                }

                byte[] postDataBytes = postData.toString().getBytes(StandardCharsets.UTF_8);
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
                JSONArray translations = response.getJSONArray("translations");
                String translatedText = translations.getJSONObject(0).getString("text");

                AndroidUtilities.runOnUIThread(() -> done.run(translatedText, false));

            } catch (Exception e) {
                Log.e("DeepLTranslator", "Translation failed", e);
                AndroidUtilities.runOnUIThread(() -> done.run(null, false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
