package org.telegram.utils.settings;

import androidx.annotation.Nullable;

public final class StringSetting {

    private final String name;
    private final String defaultValue;
    private volatile boolean loaded;
    private volatile String value;

    private StringSetting(String name, @Nullable String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static StringSetting of(String name, @Nullable String defaultValue) {
        return new StringSetting(name, defaultValue);
    }

    public @Nullable String get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = SettingsPreferences.get().getString(name, defaultValue);
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(@Nullable String value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putString(name, value).apply();
    }
}
