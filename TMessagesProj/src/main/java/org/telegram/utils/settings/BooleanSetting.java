package org.telegram.utils.settings;

import android.content.SharedPreferences;

public final class BooleanSetting {

    private final String name;
    private final boolean defaultValue;
    private volatile boolean loaded;
    private volatile boolean present;
    private volatile boolean value;

    private BooleanSetting(String name, boolean defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static BooleanSetting of(String name, boolean defaultValue) {
        return new BooleanSetting(name, defaultValue);
    }

    public boolean get() {
        ensureLoaded();
        return value;
    }

    public boolean isSet() {
        ensureLoaded();
        return present;
    }

    public synchronized void set(boolean value) {
        setLocked(value);
    }

    public synchronized boolean toggle() {
        loadLocked();
        boolean newValue = !value;
        setLocked(newValue);
        return newValue;
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (this) {
            loadLocked();
        }
    }

    private void loadLocked() {
        if (loaded) return;
        SharedPreferences preferences = SettingsPreferences.get();
        present = preferences.contains(name);
        value = preferences.getBoolean(name, defaultValue);
        loaded = true;
    }

    private void setLocked(boolean value) {
        this.value = value;
        present = true;
        loaded = true;
        SettingsPreferences.get().edit().putBoolean(name, value).apply();
    }
}
