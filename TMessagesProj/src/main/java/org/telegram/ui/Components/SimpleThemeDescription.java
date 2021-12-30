package org.telegram.ui.Components;

import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

public class SimpleThemeDescription {
    private SimpleThemeDescription() {}

    public static ArrayList<ThemeDescription> createThemeDescriptions(ThemeDescription.ThemeDescriptionDelegate del, String... keys) {
        ArrayList<ThemeDescription> l = new ArrayList<>(keys.length);
        for (String k : keys) {
            l.add(new ThemeDescription(null, 0, null, null, null, del, k));
        }
        return l;
    }
}