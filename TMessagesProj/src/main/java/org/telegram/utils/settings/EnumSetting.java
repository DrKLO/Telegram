package org.telegram.utils.settings;

import androidx.annotation.NonNull;

public final class EnumSetting<T extends Enum<T>> {

    private final String name;
    private final T defaultValue;
    private volatile boolean loaded;
    private volatile T value;

    private EnumSetting(String name, @NonNull T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static <T extends Enum<T>> EnumSetting<T> of(String name, @NonNull T defaultValue) {
        return new EnumSetting<>(name, defaultValue);
    }

    public @NonNull T get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = readValue();
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(@NonNull T value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putString(name, value.name()).apply();
    }

    @NonNull
    private T readValue() {
        String storedValue = SettingsPreferences.get().getString(name, defaultValue.name());
        if (storedValue != null) {
            try {
                return Enum.valueOf(defaultValue.getDeclaringClass(), storedValue);
            } catch (IllegalArgumentException ignore) {
            }
        }
        return defaultValue;
    }
}
