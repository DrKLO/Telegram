package org.telegram.utils.settings;

public final class FloatSetting {

    private final String name;
    private final float defaultValue;
    private volatile boolean loaded;
    private volatile float value;

    private FloatSetting(String name, float defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static FloatSetting of(String name, float defaultValue) {
        return new FloatSetting(name, defaultValue);
    }

    public float get() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    value = SettingsPreferences.get().getFloat(name, defaultValue);
                    loaded = true;
                }
            }
        }
        return value;
    }

    public synchronized void set(float value) {
        this.value = value;
        loaded = true;
        SettingsPreferences.get().edit().putFloat(name, value).apply();
    }
}
