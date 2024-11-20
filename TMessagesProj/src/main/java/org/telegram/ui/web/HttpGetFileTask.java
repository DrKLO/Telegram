package org.telegram.ui.web;


import android.content.ContentResolver;
import android.os.AsyncTask;
import android.os.Build;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import com.google.android.exoplayer2.util.MimeTypes;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpGetFileTask extends AsyncTask<String, Void, File> {

    private File file;
    private Utilities.Callback<File> callback;
    private Exception exception;
    private long max_size = -1;

    public HttpGetFileTask(Utilities.Callback<File> callback) {
        this.callback = callback;
    }

    public HttpGetFileTask setDestFile(File file) {
        this.file = file;
        return this;
    }

    public HttpGetFileTask setMaxSize(long max_size) {
        this.max_size = max_size;
        return this;
    }

    @Override
    protected File doInBackground(String... params) {
        String urlString = params[0];

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);

            int statusCode = urlConnection.getResponseCode();
            InputStream in;
            if (statusCode >= 200 && statusCode < 300) {
                in = urlConnection.getInputStream();
            } else {
                in = urlConnection.getErrorStream();
            }

            urlConnection.getResponseCode();
            long size;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                size = urlConnection.getContentLengthLong();
            } else {
                size = urlConnection.getContentLength();
            }
            if (max_size > 0 && size > max_size) {
                in.close();
                if (file != null) file = null;
                return null;
            }

            if (file == null) {
                final String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(urlConnection.getContentType());
                file = StoryEntry.makeCacheFile(UserConfig.selectedAccount, ext);
            }

            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
            outputStream.close();
            in.close();

            return file;

        } catch (Exception e) {
            this.exception = e;
            return null;
        }
    }

    @Override
    protected void onPostExecute(File file) {
        if (callback != null) {
            if (exception == null) {
                callback.run(file);
            } else {
                callback.run(null);
            }
        }
    }
}