package org.telegram.messenger;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

public class LiteMode {

    public static final int FLAG_ANIMATED_STICKERS_KEYBOARD = 1;
    public static final int FLAG_ANIMATED_STICKERS_CHAT = 2;
    public static final int FLAGS_ANIMATED_STICKERS = FLAG_ANIMATED_STICKERS_KEYBOARD | FLAG_ANIMATED_STICKERS_CHAT;

    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD = 4;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS = 8;
    public static final int FLAG_ANIMATED_EMOJI_CHAT = 16;
    public static final int FLAGS_ANIMATED_EMOJI = FLAG_ANIMATED_EMOJI_KEYBOARD | FLAG_ANIMATED_EMOJI_REACTIONS | FLAG_ANIMATED_EMOJI_CHAT;

    public static final int FLAG_CHAT_BACKGROUND = 32;
    public static final int FLAG_CHAT_FORUM_TWOCOLUMN = 64;
    public static final int FLAG_CHAT_SPOILER = 128;
    public static final int FLAG_CHAT_BLUR = 256;
    public static final int FLAGS_CHAT = FLAG_CHAT_BACKGROUND | FLAG_CHAT_FORUM_TWOCOLUMN | FLAG_CHAT_SPOILER | FLAG_CHAT_BLUR;

    public static final int FLAG_CALLS_ANIMATIONS = 512;
    public static final int FLAG_AUTOPLAY_VIDEOS = 1024;
    public static final int FLAG_AUTOPLAY_GIFS = 2048;

    public static final int ENABLED = (
        FLAGS_ANIMATED_STICKERS |
        FLAGS_ANIMATED_EMOJI |
        FLAGS_CHAT |
        FLAG_CALLS_ANIMATIONS
    );

    public static final int PRESET_LOW = 0;
    public static final int PRESET_MEDIUM = (
        FLAGS_ANIMATED_STICKERS |
        FLAGS_ANIMATED_EMOJI |
        FLAGS_CHAT |
        FLAG_CALLS_ANIMATIONS |
        FLAG_AUTOPLAY_VIDEOS |
        FLAG_AUTOPLAY_GIFS
    );
    public static final int PRESET_HIGH = (
        FLAGS_ANIMATED_STICKERS |
        FLAGS_ANIMATED_EMOJI |
        FLAGS_CHAT |
        FLAG_CALLS_ANIMATIONS |
        FLAG_AUTOPLAY_VIDEOS |
        FLAG_AUTOPLAY_GIFS
    );
    public static final int PRESET_POWER_SAVER = PRESET_LOW;

    private static boolean powerSaverEnabled;

    private static int value;
    private static boolean loaded;

    public static int getValue() {
        return getValue(false);
    }

    public static int getValue(boolean ignorePowerSaving) {
        if (!loaded) {
            loadPreference();
            loaded = true;
        }
//        if (!ignorePowerSaving && powerSaverEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            // TODO: cache this value for some time, because this could be executed every draw
//            PowerManager powerManager = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
//            if (powerManager != null && powerManager.isPowerSaveMode()) {
//                return PRESET_POWER_SAVER;
//            }
//        }
        return value;
    }

    public static boolean isEnabled(int flag) {
        return (getValue() & flag) > 0;
    }

    public static boolean isEnabledSetting(int flag) {
        return (getValue(true) & flag) > 0;
    }

    public static void toggleFlag(int flag) {
        toggleFlag(flag, !isEnabled(flag));
    }

    public static void toggleFlag(int flag, boolean enabled) {
        setAllFlags(enabled ? getValue() | flag : getValue() & ~flag);
    }

    public static void setAllFlags(int flags) {
        int changedFlags = ~getValue() & flags;
        if ((changedFlags & FLAGS_ANIMATED_EMOJI) > 0) {
            AnimatedEmojiDrawable.updateAll();
        }
        if ((changedFlags & FLAG_CHAT_BACKGROUND) > 0) {
            Theme.reloadWallpaper();
        }
        value = flags;
        savePreference();
    }

    public static void loadPreference() {
        int defaultValue = PRESET_HIGH;
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            defaultValue = PRESET_LOW;
        } else if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            defaultValue = PRESET_MEDIUM;
        }

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!preferences.contains("lite_mode")) {
            if (preferences.contains("light_mode")) {
                boolean prevLiteModeEnabled = (preferences.getInt("light_mode", SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW ? 1 : 0) & 1) > 0;
                if (prevLiteModeEnabled) {
                    defaultValue = PRESET_LOW;
                } else {
                    defaultValue = PRESET_HIGH;
                }
            }
            // migrate settings
            if (preferences.contains("loopStickers")) {
                boolean loopStickers = preferences.getBoolean("loopStickers", true);
                if (loopStickers) {
                    defaultValue |= FLAG_ANIMATED_STICKERS_CHAT;
                } else {
                    defaultValue &= ~FLAG_ANIMATED_STICKERS_CHAT;
                }
            }
            if (preferences.contains("autoplay_video")) {
                boolean autoplayVideo = preferences.getBoolean("autoplay_video", true);
                boolean autoplayVideoForce = preferences.getBoolean("autoplay_video_liteforce", false);
                if (autoplayVideo || autoplayVideoForce) {
                    defaultValue |= FLAG_AUTOPLAY_VIDEOS;
                } else {
                    defaultValue &= ~FLAG_AUTOPLAY_VIDEOS;
                }
            }
            if (preferences.contains("autoplay_gif")) {
                boolean autoplayGif = preferences.getBoolean("autoplay_gif", true);
                if (autoplayGif) {
                    defaultValue |= FLAG_AUTOPLAY_GIFS;
                } else {
                    defaultValue &= ~FLAG_AUTOPLAY_GIFS;
                }
            }
            if (preferences.contains("chatBlur")) {
                boolean chatBlur = preferences.getBoolean("chatBlur", true);
                if (chatBlur) {
                    defaultValue |= FLAG_CHAT_BLUR;
                } else {
                    defaultValue &= ~FLAG_CHAT_BLUR;
                }
            }
        }

        value = preferences.getInt("lite_mode", defaultValue);
        powerSaverEnabled = preferences.getBoolean("lite_mode_power_saver", true);
    }

    public static void savePreference() {
        MessagesController.getGlobalMainSettings().edit().putInt("lite_mode", value).putBoolean("lite_mode_power_saver", powerSaverEnabled).apply();
    }


    public static boolean isPowerSaverEnabled() {
        return powerSaverEnabled;
    }

    public static void setPowerSaverEnabled(boolean enabled) {
        powerSaverEnabled = enabled;
        savePreference();
    }
}