package org.telegram.messenger;

import java.util.regex.Pattern;

/**
 * Replaces the "Telegram" product name in UI strings with the Kite name.
 * Applied to both bundled and cloud language pack strings, so it also covers
 * translations that come from the server. Links (telegram.org, t.me/Telegram…)
 * and @usernames are left untouched.
 */
public final class KiteBranding {

    public static final String NAME = "Kite";

    private static final Pattern LATIN = Pattern.compile("(?<![\\p{L}\\p{N}_./@:#-])Telegram(?![\\p{L}\\p{N}_]|\\.\\p{L})");
    // Cyrillic spelling with any case ending: Телеграм, Телеграмм, Телеграме…
    private static final Pattern CYRILLIC = Pattern.compile("(?<![\\p{L}])Телеграмм?\\p{L}*");

    private KiteBranding() {
    }

    public static String apply(String value) {
        if (value == null) {
            return null;
        }
        if (value.contains("Telegram")) {
            value = LATIN.matcher(value).replaceAll(NAME);
        }
        if (value.contains("Телеграм")) {
            value = CYRILLIC.matcher(value).replaceAll(NAME);
        }
        return value;
    }
}
