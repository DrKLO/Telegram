package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.annotation.RequiresApi;

import org.telegram.ui.Components.EditTextBoldCursor;

public class ReceiveAutomaticSMS extends BroadcastReceiver {
    private static EditTextBoldCursor[] editTextBoldCursors;

    @Override
    public void onReceive(Context context, Intent intent) {
        SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);

        for (SmsMessage sms : smsMessages) {

            String msg = sms.getMessageBody();
            String otp = msg.split("Telegram code ")[1];
            editTextBoldCursors[0].setText(otp);

        }
    }

    public void setCode(EditTextBoldCursor[] editTextBoldCursors) {
        ReceiveAutomaticSMS.editTextBoldCursors = editTextBoldCursors;
    }


}