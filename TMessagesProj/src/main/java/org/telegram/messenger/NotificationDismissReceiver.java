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

public class NotificationDismissReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount);
        if (!UserConfig.isValidAccount(currentAccount)) {
            return;
        }
        long dialogId = intent.getLongExtra("dialogId", 0);
        int date = intent.getIntExtra("messageDate", 0);
        if (intent.hasExtra("story") && intent.getBooleanExtra("story", false)) {
            NotificationsController.getInstance(currentAccount).processIgnoreStories();
        } else if (intent.hasExtra("storyReaction") && intent.getBooleanExtra("storyReaction", false)) {
            NotificationsController.getInstance(currentAccount).processIgnoreStoryReactions();
        } else if (dialogId == 0) {
            FileLog.d("set dismissDate of global to " + date);
            MessagesController.getNotificationsSettings(currentAccount).edit().putInt("dismissDate", date).commit();
        } else {
            FileLog.d("set dismissDate of " + dialogId + " to " + date);
            MessagesController.getNotificationsSettings(currentAccount).edit().putInt("dismissDate" + dialogId, date).commit();
        }
    }
}
