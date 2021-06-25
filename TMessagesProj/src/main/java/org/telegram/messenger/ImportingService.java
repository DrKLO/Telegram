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

public class ImportingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;

    public ImportingService() {
        super();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.historyImportProgressChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.stickersImportProgressChanged);
        }
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
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(5);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.historyImportProgressChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.stickersImportProgressChanged);
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("destroy import service");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.historyImportProgressChanged || id == NotificationCenter.stickersImportProgressChanged) {
            if (!hasImportingStickers() && !hasImportingStickers()) {
                stopSelf();
            }
        }
    }

    private boolean hasImportingHistory() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (SendMessagesHelper.getInstance(a).isImportingHistory()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasImportingStickers() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (SendMessagesHelper.getInstance(a).isImportingStickers()) {
                return true;
            }
        }
        return false;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasImportingStickers() && !hasImportingHistory()) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start import service");
        }
        if (builder == null) {
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString("AppName", R.string.AppName));
            if (hasImportingHistory()) {
                builder.setTicker(LocaleController.getString("ImporImportingService", R.string.ImporImportingService));
                builder.setContentText(LocaleController.getString("ImporImportingService", R.string.ImporImportingService));
            } else {
                builder.setTicker(LocaleController.getString("ImporImportingStickersService", R.string.ImporImportingStickersService));
                builder.setContentText(LocaleController.getString("ImporImportingStickersService", R.string.ImporImportingStickersService));
            }
        }
        builder.setProgress(100, 0, true);
        startForeground(5, builder.build());
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(5, builder.build());
        return Service.START_NOT_STICKY;
    }
}
