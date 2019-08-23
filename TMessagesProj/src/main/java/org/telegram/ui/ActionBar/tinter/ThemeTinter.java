package org.telegram.ui.ActionBar.tinter;

import androidx.annotation.ColorInt;

import java.util.Map;

public interface ThemeTinter {

    int[] getBaseTintColors();

    void tint(@ColorInt int tintColor,
              @ColorInt int themeAccentColor,
              Map<String, Integer>[] inColors,
              Map<String, Integer>[] outColors);
}
