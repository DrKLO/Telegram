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
import android.os.Bundle;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import androidx.core.app.RemoteInput;

public class WearReplyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ApplicationLoader.postInitApplication();
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            return;
        }
        CharSequence text = remoteInput.getCharSequence(NotificationsController.EXTRA_VOICE_REPLY);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        long dialog_id = intent.getLongExtra("dialog_id", 0);
        int max_id = intent.getIntExtra("max_id", 0);
        int currentAccount = intent.getIntExtra("currentAccount", 0);
        if (dialog_id == 0 || max_id == 0) {
            return;
        }
        int lowerId = (int) dialog_id;
        int highId = (int) (dialog_id >> 32);
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        if (lowerId > 0) {
            TLRPC.User user = accountInstance.getMessagesController().getUser(lowerId);
            if (user == null) {
                Utilities.globalQueue.postRunnable(() -> {
                    TLRPC.User user1 = accountInstance.getMessagesStorage().getUserSync(lowerId);
                    AndroidUtilities.runOnUIThread(() -> {
                        accountInstance.getMessagesController().putUser(user1, true);
                        sendMessage(accountInstance, text, dialog_id, max_id);
                    });
                });
                return;
            }
        } else if (lowerId < 0) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-lowerId);
            if (chat == null) {
                Utilities.globalQueue.postRunnable(() -> {
                    TLRPC.Chat chat1 = accountInstance.getMessagesStorage().getChatSync(-lowerId);
                    AndroidUtilities.runOnUIThread(() -> {
                        accountInstance.getMessagesController().putChat(chat1, true);
                        sendMessage(accountInstance, text, dialog_id, max_id);
                    });
                });
                return;
            }
        }
        sendMessage(accountInstance, text, dialog_id, max_id);
    }

    private void sendMessage(AccountInstance accountInstance, CharSequence text, long dialog_id, int max_id) {
        accountInstance.getSendMessagesHelper().sendMessage(text.toString(), dialog_id, null, null, true, null, null, null, true, 0);
        accountInstance.getMessagesController().markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, true, 0);
    }
}
