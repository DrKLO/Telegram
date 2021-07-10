/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.annotation.TargetApi
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.TextUtils

@TargetApi(28)
class NotificationsDisabledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ("android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED" != intent.action) {
            return
        }
        val channelId = intent.getStringExtra(NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID)
        val state = intent.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, false)
        if (TextUtils.isEmpty(channelId) || channelId!!.contains("_ia_")) {
            return
        }
        val args = channelId.split("_").toTypedArray()
        if (args.size < 3) {
            return
        }
        ApplicationLoader.postInitApplication()
        val account = Utilities.parseInt(args[0])
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("received disabled notification channel event for $channelId state = $state")
        }
        if (SystemClock.elapsedRealtime() - AccountInstance.getInstance(account).notificationsController.lastNotificationChannelCreateTime <= 1000) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("received disable notification event right after creating notification channel, ignoring")
            }
            return
        }
        val preferences = AccountInstance.getInstance(account).notificationsSettings
        when {
            args[1].startsWith("channel") -> {
                val currentChannel = preferences.getString("channels", null)
                if (channelId != currentChannel) {
                    return
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("apply channel $channelId state")
                }
                preferences.edit().putInt(
                    NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_CHANNEL),
                    if (state) Int.MAX_VALUE else 0
                ).commit()
                AccountInstance.getInstance(account).notificationsController.updateServerNotificationsSettings(
                    NotificationsController.TYPE_CHANNEL
                )
            }
            args[1].startsWith("groups") -> {
                val currentChannel = preferences.getString("groups", null)
                if (channelId != currentChannel) {
                    return
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("apply channel $channelId state")
                }
                preferences.edit().putInt(
                    NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_GROUP),
                    if (state) Int.MAX_VALUE else 0
                ).commit()
                AccountInstance.getInstance(account).notificationsController.updateServerNotificationsSettings(
                    NotificationsController.TYPE_GROUP
                )
            }
            args[1].startsWith("private") -> {
                val currentChannel = preferences.getString("private", null)
                if (channelId != currentChannel) {
                    return
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("apply channel $channelId state")
                }
                preferences.edit().putInt(
                    NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_PRIVATE),
                    if (state) Int.MAX_VALUE else 0
                ).commit()
                AccountInstance.getInstance(account).notificationsController.updateServerNotificationsSettings(
                    NotificationsController.TYPE_PRIVATE
                )
            }
            else -> {
                val dialogId = Utilities.parseLong(args[1])
                if (dialogId == 0L) {
                    return
                }
                val currentChannel = preferences.getString("org.telegram.key$dialogId", null)
                if (channelId != currentChannel) {
                    return
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("apply channel $channelId state")
                }
                val editor = preferences.edit()
                editor.putInt("notify2_$dialogId", if (state) 2 else 0)
                if (!state) {
                    editor.remove("notifyuntil_$dialogId")
                }
                editor.commit()
                AccountInstance.getInstance(account).notificationsController.updateServerNotificationsSettings(
                    dialogId,
                    true
                )
            }
        }
        AccountInstance.getInstance(account).connectionsManager.resumeNetworkMaybe()
    }
}