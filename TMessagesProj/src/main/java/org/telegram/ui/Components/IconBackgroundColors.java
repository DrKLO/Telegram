package org.telegram.ui.Components;

public enum IconBackgroundColors {
    BLUE(0xFF1CA5ED, 0xFF1488E1),
    BLUE_ALT(0xFF1CA5ED, 0xFF1387E1),
    BLUE_DEEP(0xFF4F85F6, 0xFF3568E8),
    BLUE_LIGHT(0xFF1BA4ED, 0xFF1488E1),

    ORANGE(0xFFF09F1B, 0xFFE18A11),
    ORANGE_DEEP(0xFFF28B31, 0xFFE26314),

    GREEN(0xFF55CA47, 0xFF27B434),

    RED(0xFFF45255, 0xFFDF3955),

    CYAN(0xFF32C0CE, 0xFF1D9CC6),

    PURPLE(0xFFC46EF4, 0xFF9F55DF);

    public final int top;
    public final int bottom;

    private IconBackgroundColors(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }
}
