/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NotificationsService : Service() {
    override fun onCreate() {
        super.onCreate()
        ApplicationLoader.postInitApplication()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        val preferences = MessagesController.getGlobalNotificationsSettings()
        if (preferences.getBoolean("pushService", true)) {
            val intent = Intent("org.telegram.start")
            sendBroadcast(intent)
        }
    }
}