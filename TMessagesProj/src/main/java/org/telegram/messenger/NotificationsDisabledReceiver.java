/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import static android.app.NotificationManager.EXTRA_BLOCKED_STATE;
import static android.app.NotificationManager.EXTRA_NOTIFICATION_CHANNEL_ID;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;

@TargetApi(28)
public class NotificationsDisabledReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED".equals(intent.getAction())) {
            return;
        }
        String channelId = intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID);
        boolean state = intent.getBooleanExtra(EXTRA_BLOCKED_STATE, false);
        if (TextUtils.isEmpty(channelId) || channelId.contains("_ia_")) {
            return;
        }
        String[] args = channelId.split("_");
        if (args.length < 3) {
            return;
        }
        ApplicationLoader.postInitApplication();
        int account = Utilities.parseInt(args[0]);
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("received disabled notification channel event for " + channelId + " state = " + state);
        }
        if (SystemClock.elapsedRealtime() - AccountInstance.getInstance(account).getNotificationsController().lastNotificationChannelCreateTime <= 1000) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("received disable notification event right after creating notification channel, ignoring");
            }
            return;
        }
        SharedPreferences preferences = AccountInstance.getInstance(account).getNotificationsSettings();
        if (args[1].startsWith("channel")) {
            String currentChannel = preferences.getString("channels", null);
            if (!channelId.equals(currentChannel)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("apply channel{channel} " + channelId + " state");
            }
            preferences.edit().putInt(NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_CHANNEL), state ? Integer.MAX_VALUE : 0).commit();
            AccountInstance.getInstance(account).getNotificationsController().updateServerNotificationsSettings(NotificationsController.TYPE_CHANNEL);
        } else if (args[1].startsWith("groups")) {
            String currentChannel = preferences.getString("groups", null);
            if (!channelId.equals(currentChannel)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("apply channel{groups} " + channelId + " state");
            }
            preferences.edit().putInt(NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_GROUP), state ? Integer.MAX_VALUE : 0).commit();
            AccountInstance.getInstance(account).getNotificationsController().updateServerNotificationsSettings(NotificationsController.TYPE_GROUP);
        } else if (args[1].startsWith("private")) {
            String currentChannel = preferences.getString("private", null);
            if (!channelId.equals(currentChannel)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("apply channel{private} " + channelId + " state");
            }
            preferences.edit().putInt(NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_PRIVATE), state ? Integer.MAX_VALUE : 0).commit();
            AccountInstance.getInstance(account).getNotificationsController().updateServerNotificationsSettings(NotificationsController.TYPE_PRIVATE);
        } else if (args[1].startsWith("stories")) {
            String currentChannel = preferences.getString("stories", null);
            if (!channelId.equals(currentChannel)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("apply channel{stories} " + channelId + " state");
            }
            preferences.edit().putBoolean(NotificationsController.getGlobalNotificationsKey(NotificationsController.TYPE_STORIES), !state).commit();
            AccountInstance.getInstance(account).getNotificationsController().updateServerNotificationsSettings(NotificationsController.TYPE_PRIVATE);
        } else {
            long dialogId = Utilities.parseLong(args[1]);
            if (dialogId == 0) {
                return;
            }
            int topicId = 0;
            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            String currentChannel = preferences.getString("org.telegram.key" + dialogId, null);
            if (!channelId.equals(currentChannel)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("apply channel{else} " + channelId + " state");
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("notify2_" + key, state ? 2 : 0);
            if (!state) {
                editor.remove("notifyuntil_" + key);
            }
            editor.commit();
            AccountInstance.getInstance(account).getNotificationsController().updateServerNotificationsSettings(dialogId, 0, true);
        }
        AccountInstance.getInstance(account).getConnectionsManager().resumeNetworkMaybe();
    }
}
