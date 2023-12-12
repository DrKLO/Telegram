/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.android.search.verification.client.SearchActionVerificationClientService;

import org.telegram.tgnet.TLRPC;

public class GoogleVoiceClientService extends SearchActionVerificationClientService {

    @Override
    public void performAction(Intent intent, boolean isVerified, Bundle options) {
        if (!isVerified) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int currentAccount = UserConfig.selectedAccount;
                ApplicationLoader.postInitApplication();
                if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
                    return;
                }
                String text = intent.getStringExtra("android.intent.extra.TEXT");
                if (!TextUtils.isEmpty(text)) {
                    String contactUri = intent.getStringExtra("com.google.android.voicesearch.extra.RECIPIENT_CONTACT_URI");
                    String id = intent.getStringExtra("com.google.android.voicesearch.extra.RECIPIENT_CONTACT_CHAT_ID");
                    long uid = Long.parseLong(id);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                    if (user == null) {
                        user = MessagesStorage.getInstance(currentAccount).getUserSync(uid);
                        if (user != null) {
                            MessagesController.getInstance(currentAccount).putUser(user, true);
                        }
                    }
                    if (user != null) {
                        ContactsController.getInstance(currentAccount).markAsContacted(contactUri);
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(text, user.id, null, null, null, true, null, null, null, true, 0, null, false));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }
}
