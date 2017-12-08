/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
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
        if (intent.getAction().equals("android.intent.action.PHONE_STATE")) {
            String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(phoneState)) {
                String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReceiveCall, PhoneFormat.stripExceptNumbers(phoneNumber));
            }
        }
        /*TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephony.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == 1 && incomingNumber != null && incomingNumber.length() > 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReceiveCall, PhoneFormat.stripExceptNumbers(incomingNumber));
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);*/
    }
}
