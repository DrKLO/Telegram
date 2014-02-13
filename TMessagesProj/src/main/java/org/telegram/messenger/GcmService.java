/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.app.IntentService;
import android.content.Intent;

public class GcmService extends IntentService {

    public GcmService() {
        super("GcmService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
//            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
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
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
}
