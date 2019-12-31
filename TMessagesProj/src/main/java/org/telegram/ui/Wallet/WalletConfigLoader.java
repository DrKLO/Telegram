/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.os.AsyncTask;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TonController;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class WalletConfigLoader extends AsyncTask<Void, Void, String> {

    private String currentUrl;
    private TonController.StringCallback onFinishCallback;

    public static void loadConfig(String url, TonController.StringCallback callback) {
        new WalletConfigLoader(url, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public WalletConfigLoader(String url, TonController.StringCallback callback) {
        super();
        currentUrl = url;
        onFinishCallback = callback;
    }

    protected String doInBackground(Void... voids) {
        ByteArrayOutputStream outbuf = null;
        InputStream httpConnectionStream = null;
        boolean done = false;
        try {
            URL downloadUrl = new URL(currentUrl);
            URLConnection httpConnection = downloadUrl.openConnection();
            httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
            httpConnection.setConnectTimeout(1000);
            httpConnection.setReadTimeout(2000);
            httpConnection.connect();
            httpConnectionStream = httpConnection.getInputStream();

            outbuf = new ByteArrayOutputStream();

            byte[] data = new byte[1024 * 32];
            while (true) {
                int read = httpConnectionStream.read(data);
                if (read > 0) {
                    outbuf.write(data, 0, read);
                } else if (read == -1) {
                    break;
                } else {
                    break;
                }
            }

            String result = new String(outbuf.toByteArray());
            JSONObject jsonObject = new JSONObject(result);
            return result;
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (httpConnectionStream != null) {
                    httpConnectionStream.close();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                if (outbuf != null) {
                    outbuf.close();
                }
            } catch (Exception ignore) {

            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(final String result) {
        if (onFinishCallback != null) {
            onFinishCallback.run(result);
        }
    }
}

