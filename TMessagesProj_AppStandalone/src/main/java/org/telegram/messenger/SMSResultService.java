package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SMSResultService extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SMSJobController.receivedSMSIntent(intent, getResultCode());
    }
}
