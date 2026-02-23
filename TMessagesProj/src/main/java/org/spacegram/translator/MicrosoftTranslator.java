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

public class MicrosoftTranslator implements BaseTranslator {

    private static final String API_URL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0";
    private String apiKey;

    public MicrosoftTranslator() {
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
                String url = API_URL + "&to=" + toLang;
                if (!TextUtils.isEmpty(fromLang) && !fromLang.equals("auto")) {
                    url += "&from=" + fromLang;
                }

                URI uri = new URI(url);
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                // Build JSON body
                JSONArray bodyArray = new JSONArray();
                JSONObject textObj = new JSONObject();
                textObj.put("Text", text);
                bodyArray.put(textObj);

                byte[] postDataBytes = bodyArray.toString().getBytes(StandardCharsets.UTF_8);
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
                JSONArray responseArray = new JSONArray(jsonResponse);
                JSONObject firstResult = responseArray.getJSONObject(0);
                JSONArray translations = firstResult.getJSONArray("translations");
                String translatedText = translations.getJSONObject(0).getString("text");

                AndroidUtilities.runOnUIThread(() -> done.run(translatedText, false));

            } catch (Exception e) {
                Log.e("MicrosoftTranslator", "Translation failed", e);
                AndroidUtilities.runOnUIThread(() -> done.run(null, false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
