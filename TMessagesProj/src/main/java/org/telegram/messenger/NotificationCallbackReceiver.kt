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

class NotificationCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ApplicationLoader.postInitApplication()
        val currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount)
        if (!UserConfig.isValidAccount(currentAccount)) {
            return
        }
        val did = intent.getLongExtra("did", 777000)
        val data = intent.getByteArrayExtra("data")
        val mid = intent.getIntExtra("mid", 0)
        SendMessagesHelper.getInstance(currentAccount).sendNotificationCallback(did, mid, data)
    }
}