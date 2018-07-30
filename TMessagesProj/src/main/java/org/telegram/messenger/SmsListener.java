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
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsListener extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean outgoing = false;
        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED") || (outgoing = intent.getAction().equals("android.provider.Telephony.NEW_OUTGOING_SMS"))) {
            if (!AndroidUtilities.isWaitingForSms()) {
                return;
            }
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs;
            if (bundle != null) {
                try {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    msgs = new SmsMessage[pdus.length];
                    String wholeString = "";
                    for (int i = 0; i < msgs.length; i++) {
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        wholeString += msgs[i].getMessageBody();
                    }

                    if (outgoing) {

                    } else {
                        try {
                            Pattern pattern = Pattern.compile("[0-9]+");
                            final Matcher matcher = pattern.matcher(wholeString);
                            if (matcher.find()) {
                                String str = matcher.group(0);
                                if (str.length() >= 3) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveSmsCode, matcher.group(0));
                                        }
                                    });
                                }
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }
}
