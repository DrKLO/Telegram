package org.telegram.ui.web;


import android.os.AsyncTask;

import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpPostTask extends AsyncTask<String, Void, String> {

    private final String dataMime;
    private final String data;
    private final Utilities.Callback<String> callback;
    private final HashMap<String, String> headers = new HashMap<>();

    private Exception exception;

    public HttpPostTask(
        String mime, String data,
        Utilities.Callback<String> callback
    ) {
        this.dataMime = mime;
        this.data = data;
        this.callback = callback;
    }

    public HttpPostTask setHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    @Override
    protected String doInBackground(String... params) {
        String urlString = params[0];

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                urlConnection.setRequestProperty(e.getKey(), e.getValue());
            }
            urlConnection.setDoOutput(true);

            urlConnection.setRequestProperty("Content-Type", dataMime);
            try (OutputStream os = urlConnection.getOutputStream()) {
                byte[] input = data.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int statusCode = urlConnection.getResponseCode();
            BufferedReader in;
            if (statusCode >= 200 && statusCode < 300) {
                in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            return response.toString();

        } catch (Exception e) {
            this.exception = e;
            return null;
        }
    }

    @Override
    protected void onPostExecute(String result) {
        if (callback != null) {
            if (exception == null) {
                callback.run(result);
            } else {
                callback.run(null);
            }
        }
    }
}