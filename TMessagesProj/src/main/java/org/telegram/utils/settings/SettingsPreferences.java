package org.telegram.utils.settings;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

final class SettingsPreferences {

    private SettingsPreferences() {
    }

    static SharedPreferences get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final SharedPreferences INSTANCE =
                ApplicationLoader.applicationContext.getSharedPreferences(
                        "mainconfig",
                        Activity.MODE_PRIVATE
                );
    }
}
