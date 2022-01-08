package org.nift4.catox;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;

import java.util.HashMap;

public class SaveToDownloadReceiver extends BroadcastReceiver {
    public static final String NOTIFICATION_TAG = "MediaController";
    public static final String ACTION_CANCEL_DOWNLOAD = BuildConfig.APPLICATION_ID + ".CANCEL_SAVE_TO_DOWNLOAD";
    public static final String EXTRA_ID = BuildConfig.APPLICATION_ID + ".NOTIFICATION_ID";
    private static final HashMap<Integer, Runnable> callbacks = new HashMap<>();
    private static final HashMap<Integer, NotificationCompat.Builder> builders = new HashMap<>();
    private static int notificationIdStart = 0;
    @SuppressLint("StaticFieldLeak")
    private static NotificationManagerCompat notificationManager;

    private static NotificationManagerCompat getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        }
        return notificationManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_CANCEL_DOWNLOAD.equals(intent.getAction())) {
            int notificationId = intent.getIntExtra(EXTRA_ID, -1);
            if (notificationId >= 0) {
                Runnable runnable = callbacks.get(notificationId);
                if (runnable != null) {
                    runnable.run();
                }
                cancelNotification(notificationId);
            }
        }
    }

    public static int createNotificationId() {
        return notificationIdStart++;
    }

    public static void showNotification(Context context, int notificationId, int count, Runnable callback) {
        NotificationsController.checkOtherNotificationsChannel();
        Intent intent = new Intent(context, SaveToDownloadReceiver.class)
                .setAction(ACTION_CANCEL_DOWNLOAD)
                .putExtra(EXTRA_ID, notificationId);
        @SuppressLint("InlinedApi") PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setContentTitle(LocaleController.formatPluralString("SaveToDownloadCount", count))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setProgress(100, 0, true)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .addAction(R.drawable.ic_close_white, LocaleController.getString("Cancel", R.string.Cancel), pendingIntent);
        callbacks.put(notificationId, callback);
        builders.put(notificationId, builder);
        getNotificationManager().notify(NOTIFICATION_TAG, notificationId, builder.build());
    }

    public static void updateNotification(int notificationId, int progress) {
        NotificationCompat.Builder builder = builders.get(notificationId);
        if (builder != null) {
            builder.setProgress(100, progress, false);
            getNotificationManager().notify(NOTIFICATION_TAG, notificationId, builder.build());
        } else {
            cancelNotification(notificationId);
        }
    }

    public static void cancelNotification(int notificationId) {
        callbacks.remove(notificationId);
        builders.remove(notificationId);
        getNotificationManager().cancel(NOTIFICATION_TAG, notificationId);
    }
}
