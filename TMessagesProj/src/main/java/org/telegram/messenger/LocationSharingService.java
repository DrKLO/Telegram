/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class LocationSharingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;
    private Handler handler;
    private Runnable runnable;

    public LocationSharingService() {
        super();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.liveLocationsChanged);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        runnable = () -> {
            handler.postDelayed(runnable, 1000);
            Utilities.stageQueue.postRunnable(() -> {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    LocationController.getInstance(a).update();
                }
            });
        };
        handler.postDelayed(runnable, 1000);
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
        stopForeground(true);
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(6);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.liveLocationsChanged) {
            if (handler != null) {
                handler.post(() -> {
                    ArrayList<LocationController.SharingLocationInfo> infos = getInfos();
                    if (infos.isEmpty()) {
                        stopSelf();
                    } else {
                        updateNotification(true);
                    }
                });
            }
        }
    }

    private ArrayList<LocationController.SharingLocationInfo> getInfos() {
        ArrayList<LocationController.SharingLocationInfo> infos = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            ArrayList<LocationController.SharingLocationInfo> arrayList = LocationController.getInstance(a).sharingLocationsUI;
            if (!arrayList.isEmpty()) {
                infos.addAll(arrayList);
            }
        }
        return infos;
    }

    private void updateNotification(boolean post) {
        if (builder == null) {
            return;
        }
        String param;
        ArrayList<LocationController.SharingLocationInfo> infos = getInfos();
        String str;
        if (infos.size() == 1) {
            LocationController.SharingLocationInfo info = infos.get(0);
            long dialogId = info.messageObject.getDialogId();
            int currentAccount = info.messageObject.currentAccount;
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                param = UserObject.getFirstName(user);
                str = LocaleController.getString(R.string.AttachLiveLocationIsSharing);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (chat != null) {
                    param = chat.title;
                } else {
                    param = "";
                }
                str = LocaleController.getString(R.string.AttachLiveLocationIsSharingChat);
            }
        } else {
            param = LocaleController.formatPluralString("Chats", infos.size());
            str = LocaleController.getString(R.string.AttachLiveLocationIsSharingChats);
        }
        String text = String.format(str, LocaleController.getString(R.string.AttachLiveLocation), param);
        builder.setTicker(text);
        builder.setContentText(text);
        if (post) {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(6, builder.build());
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getInfos().isEmpty()) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        try {
            if (builder == null) {
                Intent intent2 = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                intent2.setAction("org.tmessages.openlocations");
                intent2.addCategory(Intent.CATEGORY_LAUNCHER);
                PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent2, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
                builder.setWhen(System.currentTimeMillis());
                builder.setSmallIcon(R.drawable.live_loc);
                builder.setContentIntent(contentIntent);
                NotificationsController.checkOtherNotificationsChannel();
                builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
                builder.setContentTitle(LocaleController.getString(R.string.AppName));
                Intent stopIntent = new Intent(ApplicationLoader.applicationContext, StopLiveLocationReceiver.class);
                builder.addAction(0, LocaleController.getString(R.string.StopLiveLocation), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, stopIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }

            updateNotification(false);
            if (!startForegroundCompat(builder.build())) {
                showPausedNotification();
                stopSelf();
                return Service.START_NOT_STICKY;
            }
            hidePausedNotification();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return Service.START_NOT_STICKY;
    }

    private boolean startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(6, notification);
            return true;
        }
        try {
            startForeground(6, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            return true;
        } catch (Throwable e) {
            // not allowed when started from background, the location is available only while the app is in use
            FileLog.e(e);
            return false;
        }
    }

    private static final String PAUSED_NOTIFICATION_TAG = "live_location_paused";

    // shown while there are active sharings but the service can't run, LocationController restarts it when the app is resumed
    public static void showPausedNotification() {
        boolean hasSharings = false;
        boolean forever = false;
        long timeLeft = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final ArrayList<LocationController.SharingLocationInfo> infos = LocationController.getInstance(a).sharingLocationsUI;
            for (int i = 0; i < infos.size(); i++) {
                final LocationController.SharingLocationInfo info = infos.get(i);
                hasSharings = true;
                if (info.stopTime == Integer.MAX_VALUE) {
                    forever = true;
                } else {
                    timeLeft = Math.max(timeLeft, (info.stopTime - (long) ConnectionsManager.getInstance(info.account).getCurrentTime()) * 1000L);
                }
            }
        }
        if (!hasSharings || !forever && timeLeft <= 0) {
            return;
        }
        try {
            final Context context = ApplicationLoader.applicationContext;
            Intent intent = new Intent(context, LaunchActivity.class);
            intent.setAction("org.tmessages.openlocations");
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent stopIntent = new Intent(context, StopLiveLocationReceiver.class);

            final String text = LocaleController.getString(R.string.LiveLocationPausedText);
            NotificationsController.checkOtherNotificationsChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setSmallIcon(R.drawable.live_loc);
            builder.setContentTitle(LocaleController.getString(R.string.LiveLocationPausedTitle));
            builder.setContentText(text);
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
            builder.setContentIntent(contentIntent);
            builder.setAutoCancel(true);
            builder.addAction(0, LocaleController.getString(R.string.StopLiveLocation), PendingIntent.getBroadcast(context, 2, stopIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            if (!forever) {
                // nothing to resume once the last sharing expires
                builder.setTimeoutAfter(timeLeft);
            }
            NotificationManagerCompat.from(context).notify(PAUSED_NOTIFICATION_TAG, 6, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void hidePausedNotification() {
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(PAUSED_NOTIFICATION_TAG, 6);
    }
}
