package org.telegram.ui;


    import android.net.Uri;
    import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
    import org.json.JSONTokener;

    import java.io.BufferedReader;
    import java.io.ByteArrayOutputStream;

import java.io.IOException;

    import java.io.InputStreamReader;
    import java.io.Reader;
    import java.net.HttpURLConnection;
    import java.net.URI;
    import java.net.URLEncoder;
    import java.nio.charset.Charset;


public class TranslateApi extends AsyncTask<String, String, String> {

        private OnTranslationCompleteListener listener;
        public boolean isCaption = false;
        @Override

        protected String doInBackground(String... strings) {

            String[] strArr = (String[]) strings;
            if(strArr[3]=="true") isCaption = true;
            String finalResult = null;
            String finalSourceLanguage;
            String uri = "";
            HttpURLConnection connection = null;
            try {

                uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=";
                uri += Uri.encode(strArr[0]);
                uri += "&tl=";
                uri += Uri.encode(strArr[1]);
                uri += "&dt=t&q=";
                uri += Uri.encode(strArr[2].toString());
                connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");
                connection.setRequestProperty("Content-Type", "application/json");

                StringBuilder textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")))) {
                    int c;
                    while ((c = reader.read()) != -1) {
                        textBuilder.append((char) c);
                    }
                }
                String jsonString = textBuilder.toString();

                JSONTokener tokener = new JSONTokener(jsonString);
                JSONArray array = new JSONArray(tokener);
                JSONArray array1 = array.getJSONArray(0);
                String sourceLanguage = null;
                try {
                    sourceLanguage = array.getString(2);
                } catch (Exception e2) {}
                if (sourceLanguage != null && sourceLanguage.contains("-")) {
                    sourceLanguage = sourceLanguage.substring(0, sourceLanguage.indexOf("-"));
                }
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < array1.length(); ++i) {
                    String blockText = array1.getJSONArray(i).getString(0);
                    if (blockText != null && !blockText.equals("null")) {
                        result.append(blockText);
                    }
                }
                if (strArr[2].length() > 0 && strArr[2].charAt(0) == '\n') {
                    result.insert(0, "\n");
                }
                finalResult = result.toString();
                finalSourceLanguage = sourceLanguage;

            } catch (Exception e) {

                Log.e("translate_api",e.getMessage());

                listener.onError(e);

                

            }
                return finalResult;
        }

        @Override

        protected void onPreExecute() {

            super.onPreExecute();

            listener.onStartTranslation();

        }

        @Override

        protected void onPostExecute(String text) {

            listener.onCompleted(text,isCaption);

        }

        public interface OnTranslationCompleteListener{

            void onStartTranslation();

            void onCompleted(String text,boolean isCaption);

            void onError(Exception e);

        }

        public void setOnTranslationCompleteListener(OnTranslationCompleteListener listener){

            this.listener=listener;

        }

    }

