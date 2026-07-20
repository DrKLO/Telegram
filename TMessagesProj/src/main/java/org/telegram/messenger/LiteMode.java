package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class LiteMode {

    public static final int FLAG_ANIMATED_STICKERS_KEYBOARD = 1;
    public static final int FLAG_ANIMATED_STICKERS_CHAT = 2;
    public static final int FLAGS_ANIMATED_STICKERS = FLAG_ANIMATED_STICKERS_KEYBOARD | FLAG_ANIMATED_STICKERS_CHAT;

    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM = 4;
    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD_NOT_PREMIUM = 16384;
    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD = FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM | FLAG_ANIMATED_EMOJI_KEYBOARD_NOT_PREMIUM;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM = 8;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM = 8192;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS = FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM | FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM;
    public static final int FLAG_ANIMATED_EMOJI_CHAT_PREMIUM = 16;
    public static final int FLAG_ANIMATED_EMOJI_CHAT_NOT_PREMIUM = 4096;
    public static final int FLAG_ANIMATED_EMOJI_CHAT = FLAG_ANIMATED_EMOJI_CHAT_PREMIUM | FLAG_ANIMATED_EMOJI_CHAT_NOT_PREMIUM;
    public static final int FLAGS_ANIMATED_EMOJI = FLAG_ANIMATED_EMOJI_KEYBOARD | FLAG_ANIMATED_EMOJI_REACTIONS | FLAG_ANIMATED_EMOJI_CHAT;

    public static final int FLAG_CHAT_BACKGROUND = 32;
    public static final int FLAG_CHAT_FORUM_TWOCOLUMN = 64;
    public static final int FLAG_CHAT_SPOILER = 128;
    public static final int FLAG_CHAT_BLUR = 256;
    public static final int FLAG_CHAT_SCALE = 32768;
    public static final int FLAG_CHAT_THANOS = 65536;
    public static final int FLAG_LIQUID_GLASS = 1 << 18;
    public static final int FLAGS_CHAT = FLAG_CHAT_BACKGROUND | FLAG_CHAT_FORUM_TWOCOLUMN | FLAG_CHAT_SPOILER | FLAG_CHAT_BLUR | FLAG_CHAT_SCALE | FLAG_CHAT_THANOS | FLAG_LIQUID_GLASS;

    public static final int FLAG_CALLS_ANIMATIONS = 512;
    public static final int FLAG_AUTOPLAY_VIDEOS = 1024;
    public static final int FLAG_AUTOPLAY_GIFS = 2048;
    public static final int FLAG_PARTICLES = 1 << 17;

    public static int PRESET_LOW = (
        FLAG_ANIMATED_EMOJI_CHAT_PREMIUM |
        FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM |
        FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM |
        FLAG_AUTOPLAY_GIFS |
        FLAG_CHAT_THANOS |
        FLAG_PARTICLES
    ); // 198684
    public static int PRESET_MEDIUM = (
        FLAGS_ANIMATED_STICKERS |
        FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM |
        FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM |
        FLAG_ANIMATED_EMOJI_CHAT |
        FLAG_CHAT_FORUM_TWOCOLUMN |
        FLAG_CALLS_ANIMATIONS |
        FLAG_AUTOPLAY_VIDEOS |
        FLAG_AUTOPLAY_GIFS |
        FLAG_CHAT_THANOS |
        FLAG_PARTICLES
    ); // 204383
    public static int PRESET_HIGH = (
        FLAGS_ANIMATED_STICKERS |
        FLAGS_ANIMATED_EMOJI |
        FLAG_CHAT_BACKGROUND |
        FLAG_CHAT_FORUM_TWOCOLUMN |
        FLAG_CHAT_SPOILER |
        FLAG_CHAT_BLUR |
        FLAG_CHAT_SCALE |
        FLAG_CHAT_THANOS |
        FLAG_CALLS_ANIMATIONS |
        FLAG_AUTOPLAY_VIDEOS |
        FLAG_AUTOPLAY_GIFS |
        FLAG_PARTICLES
    ); // 262143
    public static int PRESET_POWER_SAVER = 0;

    private static int BATTERY_LOW = 10;
    private static int BATTERY_MEDIUM = 10;
    private static int BATTERY_HIGH = 10;

    private static int powerSaverLevel;
    private static boolean lastPowerSaverApplied;

    private static int value;
    private static boolean loaded;

    public static int getValue() {
        return getValue(false);
    }

    public static int getValue(boolean ignorePowerSaving) {
        if (!loaded) {
            loadPreference();
        }
        if (!ignorePowerSaving && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getBatteryLevel() <= powerSaverLevel && powerSaverLevel > 0) {
                if (!lastPowerSaverApplied) {
                    onPowerSaverApplied(lastPowerSaverApplied = true);
                }
                return PRESET_POWER_SAVER;
            }
            if (lastPowerSaverApplied) {
                onPowerSaverApplied(lastPowerSaverApplied = false);
            }
        }
        return value;
    }

    private static int lastBatteryLevelCached = -1;
    private static long lastBatteryLevelChecked;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getBatteryLevel() {
        long time = 0;
        if (lastBatteryLevelCached < 0 || (time = System.currentTimeMillis()) - lastBatteryLevelChecked > 1000 * 12) {
            BatteryManager batteryManager = (BatteryManager) ApplicationLoader.applicationContext.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                lastBatteryLevelCached = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                lastBatteryLevelChecked = time;
            }
        }
        return lastBatteryLevelCached;
    }

    private static int preprocessFlag(int flag) {
        if ((flag & FLAG_ANIMATED_EMOJI_KEYBOARD) > 0) {
            flag = flag & ~FLAG_ANIMATED_EMOJI_KEYBOARD | (UserConfig.hasPremiumOnAccounts() ? FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM : FLAG_ANIMATED_EMOJI_KEYBOARD_NOT_PREMIUM);
        }
        if ((flag & FLAG_ANIMATED_EMOJI_REACTIONS) > 0) {
            flag = flag & ~FLAG_ANIMATED_EMOJI_REACTIONS | (UserConfig.hasPremiumOnAccounts() ? FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM : FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM);
        }
        if ((flag & FLAG_ANIMATED_EMOJI_CHAT) > 0) {
            flag = flag & ~FLAG_ANIMATED_EMOJI_CHAT | (UserConfig.hasPremiumOnAccounts() ? FLAG_ANIMATED_EMOJI_CHAT_PREMIUM : FLAG_ANIMATED_EMOJI_CHAT_NOT_PREMIUM);
        }
        return flag;
    }

    public static boolean isEnabled(int flag) {
        if (flag == FLAG_CHAT_FORUM_TWOCOLUMN && AndroidUtilities.isTablet()) {
            // always enabled for tablets
            return true;
        }
        return (getValue() & preprocessFlag(flag)) > 0;
    }

    public static boolean isEnabledSetting(int flag) {
        return (getValue(true) & flag) > 0;
    }

    public static void toggleFlag(int flag) {
        toggleFlag(flag, !isEnabled(flag));
    }

    public static void toggleFlag(int flag, boolean enabled) {
        setAllFlags(enabled ? getValue(true) | flag : getValue(true) & ~flag);
    }

    public static void setAllFlags(int flags) {
        // in settings it is already handled. would you handle it? 🫵
        // onFlagsUpdate(value, flags);
        value = flags;
        savePreference();
    }

    public static void updatePresets(TLRPC.TL_jsonObject json) {
        for (int i = 0; i < json.value.size(); ++i) {
            TLRPC.TL_jsonObjectValue kv = json.value.get(i);
            if ("settings_mask".equals(kv.key) && kv.value instanceof TLRPC.TL_jsonArray) {
                ArrayList<TLRPC.JSONValue> array = ((TLRPC.TL_jsonArray) kv.value).value;
                try {
                    PRESET_LOW = (int) ((TLRPC.TL_jsonNumber) array.get(0)).value;
                    PRESET_MEDIUM = (int) ((TLRPC.TL_jsonNumber) array.get(1)).value;
                    PRESET_HIGH = (int) ((TLRPC.TL_jsonNumber) array.get(2)).value;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if ("battery_low".equals(kv.key) && kv.value instanceof TLRPC.TL_jsonArray) {
                ArrayList<TLRPC.JSONValue> array = ((TLRPC.TL_jsonArray) kv.value).value;
                try {
                    BATTERY_LOW = (int) ((TLRPC.TL_jsonNumber) array.get(0)).value;
                    BATTERY_MEDIUM = (int) ((TLRPC.TL_jsonNumber) array.get(1)).value;
                    BATTERY_HIGH = (int) ((TLRPC.TL_jsonNumber) array.get(2)).value;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        loadPreference();
    }

    public static void loadPreference() {
        int defaultValue = PRESET_HIGH, batteryDefaultValue = BATTERY_HIGH;
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            defaultValue = PRESET_LOW;
            batteryDefaultValue = BATTERY_LOW;
        } else if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            defaultValue = PRESET_MEDIUM;
            batteryDefaultValue = BATTERY_MEDIUM;
        }

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!preferences.contains("lite_mode6")) {
            if (preferences.contains("lite_mode5")) {
                defaultValue = preferences.getInt("lite_mode5", defaultValue);
                defaultValue &=~ FLAG_LIQUID_GLASS;
                preferences.edit().putInt("lite_mode6", defaultValue).apply();
            } else if (preferences.contains("lite_mode4")) {
                defaultValue = preferences.getInt("lite_mode4", defaultValue);
                preferences.edit().putInt("lite_mode5", defaultValue).apply();
            } else if (preferences.contains("lite_mode3")) {
                defaultValue = preferences.getInt("lite_mode3", defaultValue);
                defaultValue |= FLAG_PARTICLES;
                preferences.edit().putInt("lite_mode5", defaultValue).apply();
            } else if (preferences.contains("lite_mode2")) {
                defaultValue = preferences.getInt("lite_mode2", defaultValue);
                defaultValue |= FLAG_CHAT_THANOS;
                preferences.edit().putInt("lite_mode3", defaultValue).apply();
            } else if (preferences.contains("lite_mode")) {
                defaultValue = preferences.getInt("lite_mode", defaultValue);
                if (defaultValue == 4095) {
                    defaultValue = PRESET_HIGH;
                }
            } else {
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
                    boolean autoplayVideo = preferences.getBoolean("autoplay_video", true) || preferences.getBoolean("autoplay_video_liteforce", false);
                    if (autoplayVideo) {
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
        }

        int prevValue = value;
        value = preferences.getInt("lite_mode6", defaultValue);
        if (loaded) {
            onFlagsUpdate(prevValue, value);
        }
        powerSaverLevel = preferences.getInt("lite_mode_battery_level", batteryDefaultValue);
        loaded = true;
    }

    public static void savePreference() {
        MessagesController.getGlobalMainSettings().edit().putInt("lite_mode6", value).putInt("lite_mode_battery_level", powerSaverLevel).apply();
    }

    public static int getPowerSaverLevel() {
        if (!loaded) {
            loadPreference();
        }
        return powerSaverLevel;
    }

    public static void setPowerSaverLevel(int value) {
        powerSaverLevel = MathUtils.clamp(value, 0, 100);
        savePreference();

        // check power saver applied
        getValue(false);
    }

    public static boolean isPowerSaverApplied() {
        getValue(false);
        return lastPowerSaverApplied;
    }

    private static void onPowerSaverApplied(boolean powerSaverApplied) {
        if (powerSaverApplied) {
            onFlagsUpdate(getValue(true), PRESET_POWER_SAVER);
        } else {
            onFlagsUpdate(PRESET_POWER_SAVER, getValue(true));
        }
        if (onPowerSaverAppliedListeners != null) {
            AndroidUtilities.runOnUIThread(() -> {
                Iterator<Utilities.Callback<Boolean>> i = onPowerSaverAppliedListeners.iterator();
                while (i.hasNext()) {
                    Utilities.Callback<Boolean> callback = i.next();
                    if (callback != null) {
                        callback.run(powerSaverApplied);
                    }
                }
            });
        }
    }

    private static void onFlagsUpdate(int oldValue, int newValue) {
        int changedFlags = ~oldValue & newValue;
        if ((changedFlags & FLAGS_ANIMATED_EMOJI) > 0) {
            AnimatedEmojiDrawable.updateAll();
        }
        if ((changedFlags & FLAG_CHAT_BACKGROUND) > 0) {
            SvgHelper.SvgDrawable.updateLiteValues();
        }
        if ((changedFlags & FLAG_CHAT_BACKGROUND) > 0) {
            Theme.reloadWallpaper(true);
        }
    }

    private static HashSet<Utilities.Callback<Boolean>> onPowerSaverAppliedListeners;
    public static void addOnPowerSaverAppliedListener(Utilities.Callback<Boolean> listener) {
        if (onPowerSaverAppliedListeners == null) {
            onPowerSaverAppliedListeners = new HashSet<>();
        }
        onPowerSaverAppliedListeners.add(listener);
    }

    public static void removeOnPowerSaverAppliedListener(Utilities.Callback<Boolean> listener) {
        if (onPowerSaverAppliedListeners != null) {
            onPowerSaverAppliedListeners.remove(listener);
        }
    }

    public static class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastBatteryLevelChecked = 0;
            getValue();
        }
    }
}