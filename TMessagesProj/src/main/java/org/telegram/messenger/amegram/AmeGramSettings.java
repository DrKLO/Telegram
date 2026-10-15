/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 Umi.
 */
package org.telegram.messenger.amegram;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-wide UI preferences only. Never store accounts, auth keys or messages here. */
public final class AmeGramSettings {
    private static final String FILE_NAME = "amegram_settings";
    private static final String OLED_DARK_BACKGROUND = "oled_dark_background";
    private final SharedPreferences preferences;

    public AmeGramSettings(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean usesOledDarkBackground() {
        return preferences.getBoolean(OLED_DARK_BACKGROUND, false);
    }

    public void setOledDarkBackground(boolean enabled) {
        preferences.edit().putBoolean(OLED_DARK_BACKGROUND, enabled).apply();
    }
}
