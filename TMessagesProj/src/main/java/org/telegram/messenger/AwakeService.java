/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.telegram.ui.ApplicationLoader;

public class AwakeService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static volatile int timeout = 10000;
    public static boolean isStarted = false;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        FileLog.e("tmessages", "service started");
        check();
        isStarted = true;
    }

    public static void startService() {
        try {
            if (ApplicationLoader.isScreenOn && ApplicationLoader.lastPauseTime == 0) {
                return;
            }
            timeout = 10000;
            if (!isStarted) {
                ApplicationLoader.applicationContext.startService(new Intent(ApplicationLoader.applicationContext, AwakeService.class));
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void check() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.postInitApplication();
                timeout -= 1000;
                if (timeout <= 0) {
                    stopSelf();
                    isStarted = false;
                    FileLog.e("tmessages", "service stoped");
                } else {
                    check();
                }
            }
        }, 1000);
    }
}
