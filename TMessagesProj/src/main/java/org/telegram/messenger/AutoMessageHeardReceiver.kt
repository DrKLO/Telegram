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

class AutoMessageHeardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ApplicationLoader.postInitApplication()
        val dialog_id = intent.getLongExtra("dialog_id", 0)
        val max_id = intent.getIntExtra("max_id", 0)
        val currentAccount = intent.getIntExtra("currentAccount", 0)
        if (dialog_id == 0L || max_id == 0 || !UserConfig.isValidAccount(currentAccount)) {
            return
        }
        val lowerId = dialog_id.toInt()
        val highId = (dialog_id shr 32) as Int
        val accountInstance = AccountInstance.getInstance(currentAccount)
        if (lowerId > 0) {
            val user = accountInstance.messagesController.getUser(lowerId)
            if (user == null) {
                Utilities.globalQueue.postRunnable {
                    val user1 = accountInstance.messagesStorage.getUserSync(lowerId)
                    AndroidUtilities.runOnUIThread {
                        accountInstance.messagesController.putUser(user1, true)
                        MessagesController.getInstance(currentAccount)
                            .markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, 0, true, 0)
                    }
                }
                return
            }
        } else if (lowerId < 0) {
            val chat = accountInstance.messagesController.getChat(-lowerId)
            if (chat == null) {
                Utilities.globalQueue.postRunnable {
                    val chat1 = accountInstance.messagesStorage.getChatSync(-lowerId)
                    AndroidUtilities.runOnUIThread {
                        accountInstance.messagesController.putChat(chat1, true)
                        MessagesController.getInstance(currentAccount)
                            .markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, 0, true, 0)
                    }
                }
                return
            }
        }
        MessagesController.getInstance(currentAccount)
            .markDialogAsRead(dialog_id, max_id, max_id, 0, false, 0, 0, true, 0)
    }
}