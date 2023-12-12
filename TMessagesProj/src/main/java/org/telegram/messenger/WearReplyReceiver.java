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

import androidx.core.app.RemoteInput;

import org.telegram.tgnet.TLRPC;

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
        long dialogId = intent.getLongExtra("dialog_id", 0);
        int maxId = intent.getIntExtra("max_id", 0);
        int topicId = intent.getIntExtra("topic_id", 0);
        int currentAccount = intent.getIntExtra("currentAccount", 0);
        if (dialogId == 0 || maxId == 0 || !UserConfig.isValidAccount(currentAccount)) {
            return;
        }
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = accountInstance.getMessagesController().getUser(dialogId);
            if (user == null) {
                Utilities.globalQueue.postRunnable(() -> {
                    TLRPC.User user1 = accountInstance.getMessagesStorage().getUserSync(dialogId);
                    AndroidUtilities.runOnUIThread(() -> {
                        accountInstance.getMessagesController().putUser(user1, true);
                        sendMessage(accountInstance, text, dialogId, topicId, maxId);
                    });
                });
                return;
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialogId);
            if (chat == null) {
                Utilities.globalQueue.postRunnable(() -> {
                    TLRPC.Chat chat1 = accountInstance.getMessagesStorage().getChatSync(-dialogId);
                    AndroidUtilities.runOnUIThread(() -> {
                        accountInstance.getMessagesController().putChat(chat1, true);
                        sendMessage(accountInstance, text, dialogId, topicId, maxId);
                    });
                });
                return;
            }
        }
        sendMessage(accountInstance, text, dialogId, topicId, maxId);
    }

    private void sendMessage(AccountInstance accountInstance, CharSequence text, long dialog_id, int topicId, int max_id) {
        MessageObject replyToMsgId = null;
        if (topicId != 0) {
            TLRPC.TL_message topicStartMessage = new TLRPC.TL_message();
            topicStartMessage.message = "";
            topicStartMessage.id = topicId;
            topicStartMessage.peer_id = accountInstance.getMessagesController().getPeer(dialog_id);
            topicStartMessage.action = new TLRPC.TL_messageActionTopicCreate();
            topicStartMessage.action.title = "";
            replyToMsgId = new MessageObject(accountInstance.getCurrentAccount(), topicStartMessage, false, false);
        }

        accountInstance.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(text.toString(), dialog_id, replyToMsgId, null, null, true, null, null, null, true, 0, null, false));
        //TODO handle topics
        if (topicId == 0) {
            accountInstance.getMessagesController().markDialogAsRead(dialog_id, max_id, max_id, 0, false, topicId, 0, true, 0);
        }
    }
}
