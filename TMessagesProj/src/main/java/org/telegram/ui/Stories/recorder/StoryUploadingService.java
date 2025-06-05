package org.telegram.ui.Stories.recorder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

public class StoryUploadingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;
    private String path;
    private float currentProgress;
    private int currentAccount = -1;

    public StoryUploadingService() {
        super();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.uploadStoryEnd);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Exception ignore) {}
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(33);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.uploadStoryEnd);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.uploadStoryProgress);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("upload story destroy");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.uploadStoryProgress) {
            if (path != null && path.equals((String) args[0])) {
                currentProgress = (float) args[1];
                builder.setProgress(100, Math.round(100 * currentProgress), currentProgress <= 0);
                try {
                    NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(33, builder.build());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.uploadStoryEnd) {
            if (path != null && path.equals((String) args[0])) {
                stopSelf();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        path = intent.getStringExtra("path");
        int oldAccount = currentAccount;
        currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount);
        if (!UserConfig.isValidAccount(currentAccount)) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (oldAccount != currentAccount) {
            if (oldAccount != -1) {
                NotificationCenter.getInstance(oldAccount).removeObserver(this, NotificationCenter.uploadStoryProgress);
            }
            if (currentAccount != -1) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.uploadStoryProgress);
            }
        }
        if (path == null) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start upload story");
        }
        if (builder == null) {
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString(R.string.AppName));
            builder.setTicker(LocaleController.getString(R.string.StoryUploading));
            builder.setContentText(LocaleController.getString(R.string.StoryUploading));
        }
        currentProgress = 0;
        builder.setProgress(100, Math.round(100 * currentProgress), false);
        startForeground(33, builder.build());
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(33, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return Service.START_NOT_STICKY;
    }
}
