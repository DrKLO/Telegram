/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;
import org.telegram.TL.TLRPC;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.LaunchActivity;

public class GcmBroadcastReceiver extends BroadcastReceiver {

    public static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.i("tmessages", "GCM received intent: " + intent);
        }
        setResultCode(Activity.RESULT_OK);

        if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            SharedPreferences preferences = context.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
            boolean globalEnabled = preferences.getBoolean("EnableAll", true);
            if (!globalEnabled) {
                if (ConnectionsManager.DEBUG_VERSION) {
                    Log.i("tmessages", "GCM disabled");
                }
                return;
            }

            Thread thread = new Thread(new Runnable() {
                public void run() {
                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    String messageType = gcm.getMessageType(intent);
                    sendNotification(context, intent.getExtras());
                }
            });
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            if (ConnectionsManager.DEBUG_VERSION) {
                String registration = intent.getStringExtra("registration_id");
                if (intent.getStringExtra("error") != null) {
                    Log.e("tmessages", "Registration failed, should try again later.");
                } else if (intent.getStringExtra("unregistered") != null) {
                    Log.e("tmessages", "unregistration done, new messages from the authorized sender will be rejected");
                } else if (registration != null) {
                    Log.e("tmessages", "registration id = " + registration);
                }
            }
        }
    }

    private void sendNotification(Context context, Bundle extras) {
        if (!UserConfig.clientActivated || context == null || extras == null) {
            return;
        }
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.d("tmessages", "received push " + extras);
        }
        SharedPreferences preferences = context.getSharedPreferences("Notifications", Context.MODE_PRIVATE);

        boolean groupEnabled = preferences.getBoolean("EnableGroup", true);
        boolean globalVibrate = preferences.getBoolean("EnableVibrateAll", true);
        boolean groupVibrate = preferences.getBoolean("EnableVibrateGroup", true);


        if (ApplicationLoader.Instance != null && (ApplicationLoader.lastPauseTime == 0 || ApplicationLoader.lastPauseTime > System.currentTimeMillis() - 200)) {
            return;
        }

        String defaultPath = null;
        Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
        if (defaultUri != null) {
            defaultPath = defaultUri.getPath();
        }

        String globalSound = preferences.getString("GlobalSoundPath", defaultPath);
        String chatSound = preferences.getString("GroupSoundPath", defaultPath);
        String userSoundPath = null;
        String chatSoundPath = null;

        NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(context, LaunchActivity.class);
        String msg = extras.getString("message");

        try {
            String to_id = extras.getString("user_id");
            int to = Integer.parseInt(to_id);
            if (to != UserConfig.clientUserId) {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int chat_id = 0;
        int user_id = 0;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        String custom = extras.getString("custom");
        try {
            if (custom != null) {
                JSONObject obj = new JSONObject(custom);
                if (obj.has("chat_id")) {
                    Object object = obj.get("chat_id");
                    if (object instanceof Integer) {
                        chat_id = (Integer)object;
                    } else if (object instanceof String) {
                        chat_id = Integer.parseInt((String)object);
                    }
                    if (chat_id != 0) {
                        intent.putExtra("chatId", chat_id);
                    }
                } else if (obj.has("from_id")) {
                    Object object = obj.get("from_id");
                    if (object instanceof Integer) {
                        user_id = (Integer)object;
                    } else if (object instanceof String) {
                        user_id = Integer.parseInt((String)object);
                    }
                    if (user_id != 0) {
                        intent.putExtra("userId", user_id);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (user_id != 0) {
            String key = "notify_" + user_id;
            boolean value = preferences.getBoolean(key, true);
            if (!value) {
                return;
            }
            userSoundPath = preferences.getString("sound_path_" + user_id, null);
        }
        if (chat_id != 0) {
            if (!groupEnabled) {
                return;
            }
            String key = "notify_" + (-chat_id);
            boolean value = preferences.getBoolean(key, true);
            if (!value) {
                return;
            }
            chatSoundPath = preferences.getString("sound_chat_path_" + chat_id, null);
        }

        boolean needVibrate;
        boolean needPreview = true;
        String choosenSoundPath = null;

        if (chat_id != 0) {
            needVibrate = groupVibrate;
        } else {
            needVibrate = globalVibrate;
        }

        if (user_id != 0) {
            if (userSoundPath != null) {
                choosenSoundPath = userSoundPath;
            } else if (globalSound != null) {
                choosenSoundPath = globalSound;
            }
        } else if (chat_id != 0) {
            if (chatSoundPath != null) {
                choosenSoundPath = chatSoundPath;
            } else if (chatSound != null) {
                choosenSoundPath = chatSound;
            }
        }

        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        //intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setContentTitle(Utilities.applicationContext.getString(R.string.AppName))
                .setSmallIcon(R.drawable.notification)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(msg))
                .setContentText(msg)
                .setAutoCancel(true);

        if (needPreview) {
            mBuilder.setTicker(msg);
        }
        if (needVibrate) {
            mBuilder.setVibrate(new long[]{0, 100, 0, 100});
        }
        if (choosenSoundPath != null && !choosenSoundPath.equals("NoSound")) {
            if (choosenSoundPath.equals(defaultPath)) {
                mBuilder.setSound(defaultUri);
            } else {
                mBuilder.setSound(Uri.parse(choosenSoundPath));
            }
        }

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.cancel(NOTIFICATION_ID);
        Notification notification = mBuilder.build();
        notification.ledARGB = 0xff00ff00;
        notification.ledOnMS = 1000;
        notification.ledOffMS = 1000;
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }
}
