package org.telegram.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;

public class WearReplyReceiver extends BroadcastReceiver {
    public WearReplyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        CharSequence text = getMessageText(intent);
        int chatID = intent.getIntExtra("chatID",-1);
        if(chatID!=-1){
            if(text!=null)MessagesController.getInstance().sendMessage(text.toString(),chatID);
        }
    }
    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence("extra_voice_reply");
        }
        return null;
    }


}
