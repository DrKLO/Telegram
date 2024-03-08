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
    private MediaController.VideoConvertMessage currentMessage;
    private static VideoEncodingService instance;

    int currentAccount;
    String currentPath;

    public VideoEncodingService() {
        super();
    }

    public static void start(boolean cancelled) {
        if (instance == null) {
            try {
                Intent intent = new Intent(ApplicationLoader.applicationContext, VideoEncodingService.class);
                ApplicationLoader.applicationContext.startService(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (cancelled) {
            MediaController.VideoConvertMessage messageInController = MediaController.getInstance().getCurrentForegroundConverMessage();
            if (instance.currentMessage != messageInController) {
                if (messageInController != null) {
                    instance.setCurrentMessage(messageInController);
                } else {
                    instance.stopSelf();
                }
            }
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.stopSelf();
        }
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }


    public void onDestroy() {
        super.onDestroy();
        instance = null;
        try {
            stopForeground(true);
        } catch (Throwable ignore) {

        }
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(4);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        currentMessage = null;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("VideoEncodingService: destroy video service");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploadProgressChanged) {
            String fileName = (String) args[0];
            if (account == currentAccount && currentPath != null && currentPath.equals(fileName)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float progress = Math.min(1f, loadedSize / (float) totalSize);
                Boolean enc = (Boolean) args[3];
                int currentProgress = (int) (progress * 100);
                builder.setProgress(100, currentProgress, currentProgress == 0);
                updateNotification();
            }
        } else if (id == NotificationCenter.fileUploaded || id == NotificationCenter.fileUploadFailed) {
            String fileName = (String) args[0];
            if (account == currentAccount && currentPath != null && currentPath.equals(fileName)) {
                AndroidUtilities.runOnUIThread(() -> {
                    MediaController.VideoConvertMessage message = MediaController.getInstance().getCurrentForegroundConverMessage();
                    if (message != null) {
                        setCurrentMessage(message);
                    } else {
                        stopSelf();
                    }
                });
            }
        }
    }

    private void updateNotification() {
        try {
            MediaController.VideoConvertMessage message = MediaController.getInstance().getCurrentForegroundConverMessage();
            if (message == null) {
                return;
            }
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(4, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning()) {
            return Service.START_NOT_STICKY;
        }
        MediaController.VideoConvertMessage videoConvertMessage = MediaController.getInstance().getCurrentForegroundConverMessage();
        if (videoConvertMessage == null) {
            return Service.START_NOT_STICKY;
        }
        instance = this;
        if (builder == null) {
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString("AppName", R.string.AppName));
        }
        setCurrentMessage(videoConvertMessage);
        try {
            startForeground(4, builder.build());
        } catch (Throwable e) {
            //ignore ForegroundServiceStartNotAllowedException
            FileLog.e(e);
        }
        AndroidUtilities.runOnUIThread(this::updateNotification);
        return Service.START_NOT_STICKY;
    }

    private void updateBuilderForMessage(MediaController.VideoConvertMessage videoConvertMessage) {
        if (videoConvertMessage == null) {
            return;
        }
        boolean isGif = videoConvertMessage.messageObject != null && MessageObject.isGifMessage(videoConvertMessage.messageObject.messageOwner);
        if (isGif) {
            builder.setTicker(LocaleController.getString("SendingGif", R.string.SendingGif));
            builder.setContentText(LocaleController.getString("SendingGif", R.string.SendingGif));
        } else {
            builder.setTicker(LocaleController.getString("SendingVideo", R.string.SendingVideo));
            builder.setContentText(LocaleController.getString("SendingVideo", R.string.SendingVideo));
        }
        int currentProgress = 0;
        builder.setProgress(100, currentProgress, true);
    }

    private void setCurrentMessage(MediaController.VideoConvertMessage message) {
        if (currentMessage == message) {
            return;
        }
        if (currentMessage != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        }
        updateBuilderForMessage(message);
        currentMessage = message;
        currentAccount = message.currentAccount;
        currentPath = message.messageObject.messageOwner.attachPath;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
        if (isRunning()) {
            updateNotification();
        }
    }

    public static boolean isRunning() {
        return instance != null;
    }

}
