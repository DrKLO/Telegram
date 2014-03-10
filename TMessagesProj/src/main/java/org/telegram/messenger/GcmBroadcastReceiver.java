/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class GcmBroadcastReceiver extends BroadcastReceiver {

    public static final int NOTIFICATION_ID = 1;
    private static PowerManager.WakeLock wakeLock = null;
    private static final Integer sync = 1;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        FileLog.d("tmessages", "GCM received intent: " + intent);

        if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            synchronized (sync) {
                try {
                    if (wakeLock == null) {
                        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lock");
                    }
                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire(5000);
                    }
                } catch (Exception e) {
                    try {
                        if (wakeLock != null) {
                            wakeLock.release();
                        }
                    } catch (Exception e2) {
                        FileLog.e("tmessages", e2);
                    }
                    FileLog.e("tmessages", e);
                }
            }

//            SharedPreferences preferences = context.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
//            boolean globalEnabled = preferences.getBoolean("EnableAll", true);
//            if (!globalEnabled) {
//                FileLog.d("tmessages", "GCM disabled");
//                return;
//            }

            ConnectionsManager.Instance.resumeNetworkMaybe();
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
            String registration = intent.getStringExtra("registration_id");
            if (intent.getStringExtra("error") != null) {
                FileLog.e("tmessages", "Registration failed, should try again later.");
            } else if (intent.getStringExtra("unregistered") != null) {
                FileLog.e("tmessages", "unregistration done, new messages from the authorized sender will be rejected");
            } else if (registration != null) {
                FileLog.e("tmessages", "registration id = " + registration);
            }
        }

        setResultCode(Activity.RESULT_OK);
    }
}
