/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class BackgroundService extends Service {

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            check();
        }
    };

    public BackgroundService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        check();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.e("tmessages", "onStartCommand");
        return START_STICKY;
    }

    private void check() {
        handler.removeCallbacks(checkRunnable);
        handler.postDelayed(checkRunnable, 1500);
        ConnectionsManager connectionsManager = ConnectionsManager.Instance;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("tmessages", "onDestroy");
    }
}
