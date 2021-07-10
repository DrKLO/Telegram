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

class ShareBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.dataString
        if (url != null) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, url)
            val chooserIntent = Intent.createChooser(
                shareIntent,
                LocaleController.getString("ShareLink", R.string.ShareLink)
            )
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooserIntent)
        }
    }
}