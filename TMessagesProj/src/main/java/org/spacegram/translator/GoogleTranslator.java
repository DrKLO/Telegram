package org.spacegram.translator;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.json.JSONArray;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class GoogleTranslator implements BaseTranslator {

    private static final String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36"
    };

    @Override
    public void translate(String text, String fromLang, String toLang, Utilities.Callback2<String, Boolean> done) {
        if (TextUtils.isEmpty(text) || done == null) return;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String encodedText = Uri.encode(text);
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + 
                        (TextUtils.isEmpty(fromLang) ? "auto" : fromLang) + 
                        "&tl=" + toLang + "&dt=t&ie=UTF-8&oe=UTF-8&q=" + encodedText;

                connection = (HttpURLConnection) new URI(url).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", userAgents[(int) (Math.random() * userAgents.length)]);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() != 200) {
                    boolean rateLimit = connection.getResponseCode() == 429;
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
                JSONArray array = new JSONArray(new JSONTokener(jsonResponse));
                JSONArray sentences = array.getJSONArray(0);
                StringBuilder translatedText = new StringBuilder();

                for (int i = 0; i < sentences.length(); i++) {
                    JSONArray sentence = sentences.getJSONArray(i);
                    if (!sentence.isNull(0)) {
                        translatedText.append(sentence.getString(0));
                    }
                }

                String finalResult = translatedText.toString();
                AndroidUtilities.runOnUIThread(() -> done.run(finalResult, false));

            } catch (Exception e) {
                Log.e("SpaceGramTranslator", "Translation failed", e);
                AndroidUtilities.runOnUIThread(() -> done.run(null, false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
