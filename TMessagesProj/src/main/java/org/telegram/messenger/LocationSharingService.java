/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class LocationSharingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private NotificationCompat.Builder builder;
    private Handler handler;
    private Runnable runnable;

    public LocationSharingService() {
        super();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.liveLocationsChanged);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        runnable = new Runnable() {
            public void run() {
                handler.postDelayed(runnable, 60000);
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        LocationController.getInstance().update();
                    }
                });
            }
        };
        handler.postDelayed(runnable, 60000);
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }

    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
        stopForeground(true);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.liveLocationsChanged) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (LocationController.getInstance().sharingLocationsUI.isEmpty()) {
                            stopSelf();
                        } else {
                            updateNotification();
                        }
                    }
                });
            }
        }
    }

    private void updateNotification() {
        if (builder == null) {
            return;
        }
        String param;
        ArrayList<LocationController.SharingLocationInfo> infos = LocationController.getInstance().sharingLocationsUI;
        if (infos.size() == 1) {
            LocationController.SharingLocationInfo info = infos.get(0);
            int lower_id = (int) info.messageObject.getDialogId();
            if (lower_id > 0) {
                TLRPC.User user = MessagesController.getInstance().getUser(lower_id);
                param = UserObject.getFirstName(user);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
                if (chat != null) {
                    param = chat.title;
                } else {
                    param = "";
                }
            }
        } else {
            param = LocaleController.formatPluralString("Chats", LocationController.getInstance().sharingLocationsUI.size());
        }
        String str = String.format(LocaleController.getString("AttachLiveLocationIsSharing", R.string.AttachLiveLocationIsSharing), LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation), param);
        builder.setTicker(str);
        builder.setContentText(str);
        NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(6, builder.build());
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (LocationController.getInstance().sharingLocationsUI.isEmpty()) {
            stopSelf();
        }
        if (builder == null) {
            Intent intent2 = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent2.setAction("org.tmessages.openlocations");
            intent2.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent2, 0);

            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);
            builder.setWhen(System.currentTimeMillis());
            builder.setSmallIcon(R.drawable.notification);
            builder.setContentIntent(contentIntent);
            builder.setContentTitle(LocaleController.getString("AppName", R.string.AppName));
            Intent stopIntent = new Intent(ApplicationLoader.applicationContext, StopLiveLocationReceiver.class);
            builder.addAction(0, LocaleController.getString("StopLiveLocation", R.string.StopLiveLocation), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        startForeground(6, builder.build());
        updateNotification();
        return Service.START_NOT_STICKY;
    }
}
