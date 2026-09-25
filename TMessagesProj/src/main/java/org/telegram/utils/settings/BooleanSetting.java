package org.telegram.utils.settings;

public final class BooleanSetting {

    private final String name;
    private final boolean defaultValue;
    private volatile boolean loaded;
    private volatile boolean value;

    private BooleanSetting(String name, boolean defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static BooleanSetting of(String name, boolean defaultValue) {
        return new BooleanSetting(name, defaultValue);
    }

    public boolean get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = SettingsPreferences.get().getBoolean(name, defaultValue);
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(boolean value) {
        setLocked(value);
    }

    public synchronized boolean toggle() {
        if (!loaded) {
            value = SettingsPreferences.get().getBoolean(name, defaultValue);
            loaded = true;
        }
        boolean newValue = !value;
        setLocked(newValue);
        return newValue;
    }

    private void setLocked(boolean value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putBoolean(name, value).apply();
    }
}
