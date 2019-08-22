package org.telegram.ui.ActionBar.tinter;

import androidx.annotation.ColorInt;

import org.telegram.ui.ActionBar.Theme;

import java.util.Map;

public interface ThemeTinter {

    ThemeTinter defaultThemeTinter = new DefaultThemeTinter();
    ThemeTinter arcticThemeTinter = new ArcticThemeTinter();
    ThemeTinter darkThemeTinter = new DarkThemeTinter();

    static ThemeTinter get(Theme.ThemeInfo themeInfo) {
        if ("arctic.attheme".equals(themeInfo.assetName)) {
            return arcticThemeTinter;
        } else if ("darkblue.attheme".equals(themeInfo.assetName)) {
            return darkThemeTinter;
        } else {
            return defaultThemeTinter;
        }
    }

    void tint(@ColorInt int tintColor,
              @ColorInt int themeAccentColor,
              Map<String, Integer>[] inColors,
              Map<String, Integer>[] outColors);
}
