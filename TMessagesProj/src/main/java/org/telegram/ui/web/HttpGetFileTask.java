package org.telegram.ui.web;


import android.content.ContentResolver;
import android.os.AsyncTask;
import android.os.Build;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.Keep;

import com.google.android.exoplayer2.util.MimeTypes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.channels.FileChannel;

@Keep
public class HttpGetFileTask extends AsyncTask<String, Void, File> {

    private File file;

    private Utilities.Callback<File> doneCallback;
    private Utilities.Callback<Float> progressCallback;

    private String overrideExt;

    private Exception exception;
    private long max_size = -1;

    public HttpGetFileTask(
        Utilities.Callback<File> doneCallback,
        Utilities.Callback<Float> progressCallback
    ) {
        this.doneCallback = doneCallback;
        this.progressCallback = progressCallback;
    }

    @Keep
    public HttpGetFileTask setOverrideExtension(String ext) {
        this.overrideExt = ext;
        return this;
    }

    @Keep
    public HttpGetFileTask setDestFile(File file) {
        this.file = file;
        return this;
    }

    @Keep
    public HttpGetFileTask setMaxSize(long max_size) {
        this.max_size = max_size;
        return this;
    }

    @Override
    protected File doInBackground(String... params) {
        String urlString = params[0];

        long totalSize = 0L;
        long downloadedSize = 0L;
        for (int i = 0; i < 5; ++i) {
            boolean resuming = i > 0;
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                if (resuming) {
                    urlConnection.setRequestProperty("Range", "bytes=" + downloadedSize + "-");
                }
                urlConnection.setDoInput(true);

                int statusCode = urlConnection.getResponseCode();
                InputStream in;
                if (statusCode >= 200 && statusCode < 300) {
                    in = urlConnection.getInputStream();
                } else {
                    in = urlConnection.getErrorStream();
                }

                final int status = urlConnection.getResponseCode();
                if (resuming && status != 206) {
                    FileLog.d("failed to resume, server doesn't support partial content. downloading from the beginning");
                    downloadedSize = 0L;
                    resuming = false;
                    if (file != null) {
                        try {
                            file.delete();
                        } catch (Exception ignore) {};
                        file = null;
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    totalSize = urlConnection.getContentLengthLong();
                } else {
                    totalSize = urlConnection.getContentLength();
                }
                if (max_size > 0 && totalSize > max_size) {
                    in.close();
                    if (file != null) file = null;
                    return null;
                }

                if (file == null) {
                    final String ext = overrideExt != null ? overrideExt : MimeTypeMap.getSingleton().getExtensionFromMimeType(urlConnection.getContentType());
                    file = StoryEntry.makeCacheFile(UserConfig.selectedAccount, ext);
                }

                try (BufferedInputStream bis = new BufferedInputStream(in, 16_384);
                     FileOutputStream fos = new FileOutputStream(file, resuming);
                     FileChannel fileChannel = fos.getChannel()) {

                    byte[] buffer = new byte[16_384];
                    int bytesRead;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        fileChannel.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead));
                        downloadedSize += bytesRead;

                        if (isCancelled()) {
                            try {
                                file.delete();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            return null;
                        }

                        if (totalSize > 0) {
                            float progress = Utilities.clamp01((float) downloadedSize / totalSize);
                            if (progressCallback != null) {
                                AndroidUtilities.runOnUIThread(() -> progressCallback.run(progress));
                            }
                        }
                    }

                    if (progressCallback != null) {
                        AndroidUtilities.runOnUIThread(() -> progressCallback.run(1.0f));
                    }
                }

                return isCancelled() ? null : file;
            } catch (Exception e) {
                if (e instanceof ProtocolException) {
                    // unexpected end of stream, lets try again!
                    FileLog.d("got unexpected end of stream, lets try to resume");
                    continue;
                }
                this.exception = e;
                FileLog.e(e);
                return null;
            }
        }
        this.exception = new RuntimeException("too many retries");
        return null;
    }

    @Override
    protected void onPostExecute(File file) {
        if (doneCallback != null) {
            if (exception == null) {
                doneCallback.run(file);
            } else {
                doneCallback.run(null);
            }
        }
    }
}