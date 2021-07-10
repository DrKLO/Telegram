/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class AutoMessageReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ApplicationLoader.postInitApplication()
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = remoteInput.getCharSequence(NotificationsController.EXTRA_VOICE_REPLY)
        if (text == null || text.isEmpty()) {
            return
        }
        val dialog_id = intent.getLongExtra("dialog_id", 0)
        val max_id = intent.getIntExtra("max_id", 0)
        val currentAccount = intent.getIntExtra("currentAccount", 0)
        if (dialog_id == 0L || max_id == 0 || !UserConfig.isValidAccount(currentAccount)) {
            return
        }
        SendMessagesHelper.getInstance(currentAccount).sendMessage(
            text.toString(),
            dialog_id,
            null,
            null,
            null,
            true,
            null,
            null,
            null,
            true,
            0,
            null
        )
        MessagesController.getInstance(currentAccount)
            .markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, 0, true, 0)
    }
}