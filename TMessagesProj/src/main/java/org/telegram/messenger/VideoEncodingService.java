/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

public class VideoEncodingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;
    private String path;
    private int currentProgress;

    public VideoEncodingService() {
        super();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileUploadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.stopEncodingService);
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }

    public void onDestroy() {
        stopForeground(true);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileUploadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stopEncodingService);
        FileLog.e("destroy video service");
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileUploadProgressChanged) {
            String fileName = (String)args[0];
            if (path != null && path.equals(fileName)) {
                Float progress = (Float) args[1];
                Boolean enc = (Boolean) args[2];
                currentProgress = (int)(progress * 100);
                builder.setProgress(100, currentProgress, currentProgress == 0);
                try {
                    NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(4, builder.build());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.stopEncodingService) {
            String filepath = (String)args[0];
            if (filepath == null || filepath.equals(path)) {
                stopSelf();
            }
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        path = intent.getStringExtra("path");
        boolean isGif = intent.getBooleanExtra("gif", false);
        if (path == null) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        FileLog.e("start video service");
        if (builder == null) {
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setContentTitle(LocaleController.getString("AppName", R.string.AppName));
            if (isGif) {
                builder.setTicker(LocaleController.getString("SendingGif", R.string.SendingGif));
                builder.setContentText(LocaleController.getString("SendingGif", R.string.SendingGif));
            } else {
                builder.setTicker(LocaleController.getString("SendingVideo", R.string.SendingVideo));
                builder.setContentText(LocaleController.getString("SendingVideo", R.string.SendingVideo));
            }
        }
        currentProgress = 0;
        builder.setProgress(100, currentProgress, currentProgress == 0);
        startForeground(4, builder.build());
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(4, builder.build());
        return Service.START_NOT_STICKY;
    }
}
