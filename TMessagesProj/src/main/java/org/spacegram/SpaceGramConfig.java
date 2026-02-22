package org.spacegram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SpaceGramConfig {

    private static final Object sync = new Object();
    private static boolean configLoaded;

    public static int translateStyle = 0;
    public static int translateProvider = 1;
    public static String translateTargetLang = "";
    public static String translateSkipLang = "";
    public static boolean autoTranslate = false;
    public static int networkSpeedMode = 0;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("spacegram_config", Activity.MODE_PRIVATE);
            translateStyle = preferences.getInt("translateStyle", 0);
            translateProvider = preferences.getInt("translateProvider", 1);
            translateTargetLang = preferences.getString("translateTargetLang", "");
            translateSkipLang = preferences.getString("translateSkipLang", "");
            autoTranslate = preferences.getBoolean("autoTranslate", false);
            networkSpeedMode = preferences.getInt("networkSpeedMode", 0);
            configLoaded = true;
        }
    }

    public static void saveConfig() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("spacegram_config", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("translateStyle", translateStyle);
                editor.putInt("translateProvider", translateProvider);
                editor.putString("translateTargetLang", translateTargetLang);
                editor.putString("translateSkipLang", translateSkipLang);
                editor.putBoolean("autoTranslate", autoTranslate);
                editor.putInt("networkSpeedMode", networkSpeedMode);
                editor.apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }
}
