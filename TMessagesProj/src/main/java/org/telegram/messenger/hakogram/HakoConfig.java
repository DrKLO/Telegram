package org.telegram.messenger.hakogram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.HashSet;
import java.util.Set;

/**
 * HakoGram central config.
 * All settings are stored in SharedPreferences "hakogram" to survive app updates.
 * This class is intentionally isolated to simplify future merges with upstream Telegram.
 *
 * Modified original files: MessagesController, StoriesController - only small guards referencing this class.
 */
public class HakoConfig {

    private static final String PREF_NAME = "hakogram";

    // Phantom
    private static final String KEY_PHANTOM_COLLAPSED = "phantom_collapsed";
    private static final String KEY_PHANTOM_NO_READ_MESSAGES = "phantom_no_read_messages";
    private static final String KEY_PHANTOM_NO_READ_STORIES = "phantom_no_read_stories";
    private static final String KEY_PHANTOM_NO_ONLINE = "phantom_no_online";
    private static final String KEY_PHANTOM_NO_TYPING = "phantom_no_typing";

    // Slezka
    private static final String KEY_SLEZHKA_SHOW_CHATS = "slezka_show_chats";
    private static final String KEY_SLEZHKA_SAVE_LAST_ONLINE = "slezka_save_last_online";
    private static final String KEY_SLEZHKA_NOTIFY_ENABLED = "slezka_notify_enabled";
    private static final String KEY_SLEZHKA_NOTIFY_READ = "slezka_notify_read";
    private static final String KEY_SLEZHKA_NOTIFY_ONLINE = "slezka_notify_online";
    private static final String KEY_SLEZHKA_NOTIFY_TYPING = "slezka_notify_typing";
    private static final String KEY_SLEZHKA_NOTIFY_OFFLINE = "slezka_notify_offline";
    private static final String KEY_SLEZHKA_NOTIFY_BLOCK = "slezka_notify_block";
    private static final String KEY_SLEZHKA_SELECTED_USERS = "slezka_selected_users"; // csv of user ids
    private static final String KEY_SLEZHKA_NOTIFY_METHODS = "slezka_notify_methods"; // csv 1=SMS,2=TG,3=VIB,4=FLASH

    private static SharedPreferences getPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            // fallback for early init before ApplicationLoader
            return null;
        }
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPrefsSafe() {
        SharedPreferences p = getPrefs();
        if (p == null) throw new IllegalStateException("ApplicationLoader not initialized");
        return p;
    }

    // helpers
    private static boolean getBool(String key, boolean def) {
        SharedPreferences p = getPrefs();
        if (p == null) return def;
        return p.getBoolean(key, def);
    }
    private static void putBool(String key, boolean val) {
        SharedPreferences p = getPrefsSafe();
        p.edit().putBoolean(key, val).apply();
    }
    private static String getString(String key, String def) {
        SharedPreferences p = getPrefs();
        if (p == null) return def;
        return p.getString(key, def);
    }
    private static void putString(String key, String val) {
        SharedPreferences p = getPrefsSafe();
        p.edit().putString(key, val).apply();
    }

    // ---- Phantom ----
    public static boolean isPhantomCollapsed() {
        return getBool(KEY_PHANTOM_COLLAPSED, true);
    }
    public static void setPhantomCollapsed(boolean v) { putBool(KEY_PHANTOM_COLLAPSED, v); }

    public static boolean isNoReadMessages() { return getBool(KEY_PHANTOM_NO_READ_MESSAGES, false); }
    public static void setNoReadMessages(boolean v) { putBool(KEY_PHANTOM_NO_READ_MESSAGES, v); }

    public static boolean isNoReadStories() { return getBool(KEY_PHANTOM_NO_READ_STORIES, false); }
    public static void setNoReadStories(boolean v) { putBool(KEY_PHANTOM_NO_READ_STORIES, v); }

    public static boolean isNoOnline() { return getBool(KEY_PHANTOM_NO_ONLINE, false); }
    public static void setNoOnline(boolean v) { putBool(KEY_PHANTOM_NO_ONLINE, v); }

    public static boolean isNoTyping() { return getBool(KEY_PHANTOM_NO_TYPING, false); }
    public static void setNoTyping(boolean v) { putBool(KEY_PHANTOM_NO_TYPING, v); }

    // ---- Slezka ----
    public static boolean isSlezkaShowChats() { return getBool(KEY_SLEZHKA_SHOW_CHATS, false); }
    public static void setSlezkaShowChats(boolean v) { putBool(KEY_SLEZHKA_SHOW_CHATS, v); }

    public static boolean isSlezkaSaveLastOnline() { return getBool(KEY_SLEZHKA_SAVE_LAST_ONLINE, false); }
    public static void setSlezkaSaveLastOnline(boolean v) { putBool(KEY_SLEZHKA_SAVE_LAST_ONLINE, v); }

    public static boolean isSlezkaNotifyEnabled() { return getBool(KEY_SLEZHKA_NOTIFY_ENABLED, false); }
    public static void setSlezkaNotifyEnabled(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_ENABLED, v); }

    public static boolean isSlezkaNotifyRead() { return getBool(KEY_SLEZHKA_NOTIFY_READ, false); }
    public static void setSlezkaNotifyRead(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_READ, v); }

    public static boolean isSlezkaNotifyOnline() { return getBool(KEY_SLEZHKA_NOTIFY_ONLINE, false); }
    public static void setSlezkaNotifyOnline(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_ONLINE, v); }

    public static boolean isSlezkaNotifyTyping() { return getBool(KEY_SLEZHKA_NOTIFY_TYPING, false); }
    public static void setSlezkaNotifyTyping(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_TYPING, v); }

    public static boolean isSlezkaNotifyOffline() { return getBool(KEY_SLEZHKA_NOTIFY_OFFLINE, false); }
    public static void setSlezkaNotifyOffline(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_OFFLINE, v); }

    public static boolean isSlezkaNotifyBlock() { return getBool(KEY_SLEZHKA_NOTIFY_BLOCK, false); }
    public static void setSlezkaNotifyBlock(boolean v) { putBool(KEY_SLEZHKA_NOTIFY_BLOCK, v); }

    // Selected users as CSV string
    public static String getSlezkaSelectedUsersCsv() { return getString(KEY_SLEZHKA_SELECTED_USERS, ""); }
    public static void setSlezkaSelectedUsersCsv(String csv) { putString(KEY_SLEZHKA_SELECTED_USERS, csv); }

    public static Set<Long> getSlezkaSelectedUserIds() {
        String csv = getSlezkaSelectedUsersCsv();
        Set<Long> set = new HashSet<>();
        if (csv == null || csv.isEmpty()) return set;
        for (String s : csv.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            try { set.add(Long.parseLong(s)); } catch (Exception ignored) {}
        }
        return set;
    }
    public static void setSlezkaSelectedUserIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) { putString(KEY_SLEZHKA_SELECTED_USERS, ""); return; }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        putString(KEY_SLEZHKA_SELECTED_USERS, sb.toString());
    }

    // Notify methods: 1=SMS, 2=Telegram in-app, 3=Vibration, 4=Flashlight
    public static String getSlezkaNotifyMethodsCsv() { return getString(KEY_SLEZHKA_NOTIFY_METHODS, ""); }
    public static void setSlezkaNotifyMethodsCsv(String csv) { putString(KEY_SLEZHKA_NOTIFY_METHODS, csv); }

    public static Set<Integer> getSlezkaNotifyMethods() {
        String csv = getSlezkaNotifyMethodsCsv();
        Set<Integer> set = new HashSet<>();
        if (csv == null || csv.isEmpty()) return set;
        for (String s : csv.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            try { set.add(Integer.parseInt(s)); } catch (Exception ignored) {}
        }
        return set;
    }
    public static void setSlezkaNotifyMethods(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) { putString(KEY_SLEZHKA_NOTIFY_METHODS, ""); return; }
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        putString(KEY_SLEZHKA_NOTIFY_METHODS, sb.toString());
    }

    public static String getSlezkaNotifyMethodsSummary() {
        Set<Integer> set = getSlezkaNotifyMethods();
        if (set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer i : set) {
            String name;
            switch (i) {
                case 1: name = "SMS"; break;
                case 2: name = "Telegram"; break;
                case 3: name = "Вибрация"; break;
                case 4: name = "Фонарик"; break;
                default: name = String.valueOf(i);
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(name);
        }
        return sb.toString();
    }

    public static String getSlezkaSelectedUsersSummary(android.content.Context context, int currentAccount) {
        Set<Long> ids = getSlezkaSelectedUserIds();
        if (ids.isEmpty()) return "";
        // Try to resolve names via MessagesController
        StringBuilder sb = new StringBuilder();
        try {
            org.telegram.messenger.MessagesController mc = org.telegram.messenger.MessagesController.getInstance(currentAccount);
            int c = 0;
            for (Long uid : ids) {
                if (c++ >= 3) { sb.append(" +").append(ids.size() - 3); break; }
                org.telegram.tgnet.TLRPC.User user = mc.getUser(uid);
                if (user != null) {
                    String name = org.telegram.messenger.UserObject.getUserName(user);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name);
                } else {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(uid);
                }
            }
            if (sb.length() == 0) return ids.size() + " выбрано";
        } catch (Exception e) {
            return ids.size() + " выбрано";
        }
        return sb.toString();
    }
}
