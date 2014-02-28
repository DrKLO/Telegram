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

    @Override
    public void onReceive(final Context context, final Intent intent) {
        FileLog.d("tmessages", "GCM received intent: " + intent);

        if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lock");
            wl.acquire();

//            SharedPreferences preferences = context.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
//            boolean globalEnabled = preferences.getBoolean("EnableAll", true);
//            if (!globalEnabled) {
//                FileLog.d("tmessages", "GCM disabled");
//                return;
//            }

            Thread thread = new Thread(new Runnable() {
                public void run() {
                    ConnectionsManager.Instance.resumeNetworkMaybe();
                    wl.release();
                }
            });
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
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
