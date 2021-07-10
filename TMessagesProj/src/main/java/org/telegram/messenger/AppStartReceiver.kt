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

class AppStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            AndroidUtilities.runOnUIThread {
                SharedConfig.loadConfig()
                if (SharedConfig.passcodeHash.isNotEmpty()) {
                    SharedConfig.appLocked = true
                    SharedConfig.saveConfig()
                }
                ApplicationLoader.startPushService()
            }
        }
    }
}