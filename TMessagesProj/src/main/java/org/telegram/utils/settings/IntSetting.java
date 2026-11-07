package org.telegram.utils.settings;

public final class IntSetting {

    private final String name;
    private final int defaultValue;
    private volatile boolean loaded;
    private volatile int value;

    private IntSetting(String name, int defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static IntSetting of(String name, int defaultValue) {
        return new IntSetting(name, defaultValue);
    }

    public int get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = SettingsPreferences.get().getInt(name, defaultValue);
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(int value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putInt(name, value).apply();
    }
}
