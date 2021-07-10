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
import org.telegram.ui.LaunchActivity

class BringAppForegroundService : IntentService("BringAppForegroundService") {
    override fun onHandleIntent(intent: Intent?) {
        val intent2 = Intent(this, LaunchActivity::class.java)
        intent2.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent2.action = Intent.ACTION_MAIN
        startActivity(intent2)
    }
}