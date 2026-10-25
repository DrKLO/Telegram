package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Kite: optional "keep deleted messages". When enabled, messages that other people delete
 * stay in the local cache and are shown with a "deleted" mark instead of disappearing.
 */
public class KiteDeletedMessages {

    private static final String PREF_ENABLED = "kite_keep_deleted";
    private static final int MAX_REMEMBERED = 20000;

    private static final Set<String>[] deleted = new Set[UserConfig.MAX_ACCOUNT_COUNT];

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("kite_deleted" + account, Context.MODE_PRIVATE);
    }

    private static Set<String> get(int account) {
        if (deleted[account] == null) {
            deleted[account] = new HashSet<>(prefs(account).getStringSet("ids", new HashSet<>()));
        }
        return deleted[account];
    }

    /** Keys are 0 for private chats and basic groups, -channelId for channels and supergroups. */
    private static String key(long dialogKey, int messageId) {
        return dialogKey + ":" + messageId;
    }

    public static synchronized void remember(int account, LongSparseArray<ArrayList<Integer>> messages) {
        final Set<String> set = get(account);
        for (int a = 0, size = messages.size(); a < size; a++) {
            final long dialogKey = messages.keyAt(a);
            final ArrayList<Integer> ids = messages.valueAt(a);
            if (ids == null) {
                continue;
            }
            for (int i = 0; i < ids.size(); i++) {
                set.add(key(dialogKey, ids.get(i)));
            }
        }
        if (set.size() > MAX_REMEMBERED) {
            set.clear();
        }
        prefs(account).edit().putStringSet("ids", new HashSet<>(set)).apply();
    }

    public static synchronized boolean isDeleted(int account, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getId() <= 0) {
            return false;
        }
        final Set<String> set = get(account);
        if (set.isEmpty()) {
            return false;
        }
        final TLRPC.Peer peer = messageObject.messageOwner.peer_id;
        final long dialogKey = peer != null && peer.channel_id != 0 ? -peer.channel_id : 0;
        return set.contains(key(dialogKey, messageObject.getId()));
    }
}
