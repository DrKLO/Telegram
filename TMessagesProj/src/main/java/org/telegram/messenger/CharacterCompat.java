package org.telegram.messenger;

public class CharacterCompat {
    public static final char MIN_HIGH_SURROGATE = '\uD800';
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    public static final char MIN_LOW_SURROGATE  = '\uDC00';

    /**
     * Compat version of {@link Character#highSurrogate(int)}
     */
    public static char highSurrogate(int codePoint) {
        return (char) ((codePoint >>> 10)
                + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }

    /**
     * Compat version of {@link Character#lowSurrogate(int)}
     */
    public static char lowSurrogate(int codePoint) {
        return (char) ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
    }
}
