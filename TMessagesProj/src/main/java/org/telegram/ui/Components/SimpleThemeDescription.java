package org.telegram.ui.Components;

import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

public class SimpleThemeDescription {
    private SimpleThemeDescription() {}

    public static ThemeDescription createThemeDescription(ThemeDescription.ThemeDescriptionDelegate del, int key) {
        return new ThemeDescription(null, 0, null, null, null, del, key);
    }

    public static ArrayList<ThemeDescription> createThemeDescriptions(ThemeDescription.ThemeDescriptionDelegate del, int... keys) {
        ArrayList<ThemeDescription> l = new ArrayList<>(keys.length);
        for (int k : keys) {
            l.add(createThemeDescription(del, k));
        }
        return l;
    }

    public static void add(ArrayList<ThemeDescription> descriptions, Runnable upd, int... keys) {
        descriptions.addAll(SimpleThemeDescription.createThemeDescriptions(upd::run, keys));
    }
}