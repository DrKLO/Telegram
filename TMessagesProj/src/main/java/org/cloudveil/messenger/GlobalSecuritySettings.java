package org.cloudveil.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import org.telegram.messenger.ApplicationLoader;

/**
 * Created by darren on 2017-03-25.
 */

public class GlobalSecuritySettings {
    public static final boolean LOCK_DISABLE_DELETE_CHAT = false;
    public static final boolean LOCK_DISABLE_FORWARD_CHAT = false;
    public static final boolean LOCK_DISABLE_BOTS = true;
    public static final boolean LOCK_DISABLE_YOUTUBE_VIDEO = true;


    private static boolean DEFAULT_LOCK_DISABLE_SECRET_CHAT = false;
    private static int DEFAULT_MIN_SECRET_CHAT_TTL = 0;
    public static final boolean LOCK_DISABLE_IN_APP_BROWSER = true;
    public static final boolean LOCK_DISABLE_AUTOPLAY_GIFS = true;
    public static final boolean LOCK_DISABLE_GIFS = true;
    public static final boolean LOCK_DISABLE_GLOBAL_SEARCH = true;
    public static final boolean LOCK_DISABLE_STICKERS = true;

    public static boolean isDisabledSecretChat() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(GlobalSecuritySettings.class.getCanonicalName(), Activity.MODE_PRIVATE);
        return preferences.getBoolean("disabledSecretChat", DEFAULT_LOCK_DISABLE_SECRET_CHAT);
    }

    public static void setDisableSecretChat(boolean isDisabled) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(GlobalSecuritySettings.class.getCanonicalName(), Activity.MODE_PRIVATE);
        preferences.edit().putBoolean("disabledSecretChat", isDisabled).apply();
    }

    public static int getMinSecretChatTtl() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(GlobalSecuritySettings.class.getCanonicalName(), Activity.MODE_PRIVATE);
        return preferences.getInt("minChatTtl", DEFAULT_MIN_SECRET_CHAT_TTL);
    }

    public static void setMinSecretChatTtl(int ttl) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(GlobalSecuritySettings.class.getCanonicalName(), Activity.MODE_PRIVATE);
        preferences.edit().putInt("minChatTtl", ttl).apply();
    }
}
