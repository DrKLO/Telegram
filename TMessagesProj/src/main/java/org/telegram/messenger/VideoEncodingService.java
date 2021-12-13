/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class VideoEncodingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;
    private String path;
    private int currentProgress;
    private int currentAccount;

    public VideoEncodingService() {
        super();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.stopEncodingService);
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {

        }
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(4);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.stopEncodingService);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("destroy video service");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploadProgressChanged) {
            String fileName = (String) args[0];
            if (account == currentAccount && path != null && path.equals(fileName)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float progress = Math.min(1f, loadedSize / (float) totalSize);
                Boolean enc = (Boolean) args[3];
                currentProgress = (int) (progress * 100);
                builder.setProgress(100, currentProgress, currentProgress == 0);
                try {
                    NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(4, builder.build());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.stopEncodingService) {
            String filepath = (String) args[0];
            account = (Integer) args[1];
            if (account == currentAccount && (filepath == null || filepath.equals(path))) {
                stopSelf();
            }
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        path = intent.getStringExtra("path");
        int oldAccount = currentAccount;
        currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount);
        if (!UserConfig.isValidAccount(currentAccount)) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (oldAccount != currentAccount) {
            NotificationCenter.getInstance(oldAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged);
        }
        boolean isGif = intent.getBooleanExtra("gif", false);
        if (path == null) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start video service");
        }
        if (builder == null) {
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
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
        builder.setProgress(100, currentProgress, true);
        startForeground(4, builder.build());
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(4, builder.build());
        return Service.START_NOT_STICKY;
    }
}
