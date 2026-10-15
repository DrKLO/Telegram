/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 Umi.
 */
package org.telegram.ui.amegram;

/** Solid design tokens. Use the darker accent for readable text on light surfaces. */
public final class AmeGramPalette {
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_DARK = 0xFF7C3AED;
    public static final int ACCENT_LIGHT = 0xFFA78BFA;
    public static final int DARK_BACKGROUND = 0xFF11111B;
    public static final int DARK_SURFACE = 0xFF1E1E2E;
    public static final int LIGHT_BACKGROUND = 0xFFF8F7FC;
    public static final int LIGHT_SURFACE = 0xFFFFFFFF;

    private AmeGramPalette() {}

    public static int background(boolean dark, boolean oled) {
        return dark ? (oled ? 0xFF000000 : DARK_BACKGROUND) : LIGHT_BACKGROUND;
    }

    public static int surface(boolean dark, boolean oled) {
        return dark ? (oled ? 0xFF000000 : DARK_SURFACE) : LIGHT_SURFACE;
    }

    public static int text(boolean dark) {
        return dark ? 0xFFF5F3FF : 0xFF242033;
    }

    public static int secondaryText(boolean dark) {
        return dark ? 0xFFC4BED4 : 0xFF625B71;
    }

    public static int accessibleAccent(boolean dark) {
        return dark ? ACCENT_LIGHT : ACCENT_DARK;
    }
}
