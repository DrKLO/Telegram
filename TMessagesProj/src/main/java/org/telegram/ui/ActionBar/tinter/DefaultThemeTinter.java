package org.telegram.ui.ActionBar.tinter;

import java.util.Map;

public class DefaultThemeTinter implements ThemeTinter {

    @Override
    public int[] getBaseTintColors() {
        return null;
    }

    @Override
    public void tint(int tintColor,
                     int themeAccentColor,
                     Map<String, Integer>[] inColors,
                     Map<String, Integer>[] outColors) {
        for (int i = 0; i < inColors.length; i++) {
            outColors[i].putAll(inColors[i]);
        }
    }
}
