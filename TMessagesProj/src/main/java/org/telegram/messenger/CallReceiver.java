/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import org.telegram.PhoneFormat.PhoneFormat;

public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(phoneState)) {
                String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                String phone = PhoneFormat.stripExceptNumbers(phoneNumber);
                SharedConfig.getPreferences().edit()
                        .putString("last_call_phone_number", phone)
                        .putLong("last_call_time", System.currentTimeMillis())
                        .apply();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveCall, phone);
            }
        }
    }

    public static String getLastReceivedCall() {
        String phone = SharedConfig.getPreferences().getString("last_call_phone_number", null);
        if (phone == null) {
            return null;
        }
        long lastTime = SharedConfig.getPreferences().getLong("last_call_time", 0);
        if (System.currentTimeMillis() - lastTime < 1000 * 60 * 60 * 15) {
            return phone;
        }
        return null;
    }

    public static void checkLastReceivedCall() {
        String lastCall = getLastReceivedCall();
        if (lastCall != null) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveCall, lastCall);
        }
    }

    public static void clearLastCall() {
        SharedConfig.getPreferences().edit()
                .remove("last_call_phone_number")
                .remove("last_call_time")
                .apply();
    }
}
