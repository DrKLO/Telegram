package org.telegram.utils.settings;

public final class LongSetting {

    private final String name;
    private final long defaultValue;
    private volatile boolean loaded;
    private volatile long value;

    private LongSetting(String name, long defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static LongSetting of(String name, long defaultValue) {
        return new LongSetting(name, defaultValue);
    }

    public long get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = SettingsPreferences.get().getLong(name, defaultValue);
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(long value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putLong(name, value).apply();
    }
}
