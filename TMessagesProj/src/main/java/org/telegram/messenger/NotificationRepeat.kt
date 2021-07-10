/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.app.IntentService
import android.content.Intent

class NotificationRepeat : IntentService("NotificationRepeat") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        val currentAccount = intent.getIntExtra("currentAccount", UserConfig.selectedAccount)
        if (!UserConfig.isValidAccount(currentAccount)) {
            return
        }
        AndroidUtilities.runOnUIThread {
            NotificationsController.getInstance(currentAccount).repeatNotificationMaybe()
        }
    }
}