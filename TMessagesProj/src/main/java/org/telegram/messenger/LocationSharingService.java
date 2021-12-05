/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
                str = LocaleController.getString("AttachLiveLocationIsSharing", R.string.AttachLiveLocationIsSharing);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (chat != null) {
                    param = chat.title;
                } else {
                    param = "";
                }
                str = LocaleController.getString("AttachLiveLocationIsSharingChat", R.string.AttachLiveLocationIsSharingChat);
            }
        } else {
            param = LocaleController.formatPluralString("Chats", infos.size());
            str = LocaleController.getString("AttachLiveLocationIsSharingChats", R.string.AttachLiveLocationIsSharingChats);
        }
        String text = String.format(str, LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), param);
        builder.setTicker(text);
        builder.setContentText(text);
        if (post) {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(6, builder.build());
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getInfos().isEmpty()) {
            stopSelf();
        }
        if (builder == null) {
            Intent intent2 = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent2.setAction("org.tmessages.openlocations");
            intent2.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent2, 0);

            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setWhen(System.currentTimeMillis());
            builder.setSmallIcon(R.drawable.live_loc);
            builder.setContentIntent(contentIntent);
            NotificationsController.checkOtherNotificationsChannel();
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString("AppName", R.string.AppName));
            Intent stopIntent = new Intent(ApplicationLoader.applicationContext, StopLiveLocationReceiver.class);
            builder.addAction(0, LocaleController.getString("StopLiveLocation", R.string.StopLiveLocation), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        updateNotification(false);
        startForeground(6, builder.build());
        return Service.START_NOT_STICKY;
    }
}
