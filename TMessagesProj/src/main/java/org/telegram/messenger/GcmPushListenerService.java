/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.gcm.GcmListenerService;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import static android.support.v4.net.ConnectivityManagerCompat.RESTRICT_BACKGROUND_STATUS_ENABLED;

public class GcmPushListenerService extends GcmListenerService {

    public static final int NOTIFICATION_ID = 1;

    @Override
    public void onMessageReceived(String from, final Bundle bundle) {
        FileLog.d("GCM received bundle: " + bundle + " from: " + from);
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                ApplicationLoader.postInitApplication();

                try {
                    String key = bundle.getString("loc_key");
                    if ("DC_UPDATE".equals(key)) {
                        String data = bundle.getString("custom");
                        JSONObject object = new JSONObject(data);
                        int dc = object.getInt("dc");
                        String addr = object.getString("addr");
                        String[] parts = addr.split(":");
                        if (parts.length != 2) {
                            return;
                        }
                        String ip = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        ConnectionsManager.getInstance().applyDatacenterAddress(dc, ip, port);
                    } else if ("MESSAGE_ANNOUNCEMENT".equals(key)) {
                        Object obj = bundle.get("google.sent_time");
                        long time;
                        try {
                            if (obj instanceof String) {
                                time = Utilities.parseLong((String) obj);
                            } else if (obj instanceof Long) {
                                time = (Long) obj;
                            } else {
                                time = System.currentTimeMillis();
                            }
                        } catch (Exception ignore) {
                            time = System.currentTimeMillis();
                        }

                        TLRPC.TL_updateServiceNotification update = new TLRPC.TL_updateServiceNotification();
                        update.popup = false;
                        update.flags = 2;
                        update.inbox_date = (int) (time / 1000);
                        update.message = bundle.getString("message");
                        update.type = "announcement";
                        update.media = new TLRPC.TL_messageMediaEmpty();
                        final TLRPC.TL_updates updates = new TLRPC.TL_updates();
                        updates.updates.add(update);
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance().processUpdates(updates, false);
                            }
                        });
                    } else if (Build.VERSION.SDK_INT >= 24 && ApplicationLoader.mainInterfacePaused && UserConfig.isClientActivated()) {
                        Object value = bundle.get("badge");
                        if (value == null) {
                            Object obj = bundle.get("google.sent_time");
                            long time;
                            if (obj instanceof String) {
                                time = Utilities.parseLong((String) obj);
                            } else if (obj instanceof Long) {
                                time = (Long) obj;
                            } else {
                                time = -1;
                            }
                            if (time == -1 || UserConfig.lastAppPauseTime < time) {
                                ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                                NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
                                if (connectivityManager.getRestrictBackgroundStatus() == RESTRICT_BACKGROUND_STATUS_ENABLED && netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                                    NotificationsController.getInstance().showSingleBackgroundNotification();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                ConnectionsManager.onInternalPushReceived();
                ConnectionsManager.getInstance().resumeNetworkMaybe();
            }
        });
    }
}
