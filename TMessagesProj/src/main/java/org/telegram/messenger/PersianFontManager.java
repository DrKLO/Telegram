package org.telegram.messenger;

import android.graphics.Typeface;

public final class PersianFontManager {
    private static final String FONT_REGULAR =
            "fonts/Vazirmatn-Regular.ttf";
    private static final String FONT_MEDIUM =
            "fonts/Vazirmatn-Medium.ttf";
    private static final String FONT_BOLD =
            "fonts/Vazirmatn-Bold.ttf";

    private PersianFontManager() {
        // Utility class.
    }

    public static Typeface regular() {
        return load(FONT_REGULAR, Typeface.DEFAULT);
    }

    public static Typeface medium() {
        return load(FONT_MEDIUM, Typeface.DEFAULT_BOLD);
    }

    public static Typeface bold() {
        return load(FONT_BOLD, Typeface.DEFAULT_BOLD);
    }

    private static Typeface load(String assetPath, Typeface fallback) {
        Typeface typeface = AndroidUtilities.getTypeface(assetPath);
        return typeface != null ? typeface : fallback;
    }

}
