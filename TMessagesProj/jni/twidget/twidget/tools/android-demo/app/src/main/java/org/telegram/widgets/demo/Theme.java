package org.telegram.widgets.demo;

/** The chat colours the demo draws bubbles with: Telegram's default day and night themes, roughly. */
public final class Theme {

    public static boolean dark;

    private Theme() {
    }

    public static int chatBackground() {
        return dark ? 0xff0e1621 : 0xffc9dcb4;
    }

    public static int bubbleIn() {
        return dark ? 0xff182533 : 0xffffffff;
    }

    public static int bubbleText() {
        return dark ? 0xfff5f5f5 : 0xff000000;
    }

    public static int bubbleSecondary() {
        return dark ? 0xff6d7f8f : 0xff8a8a8a;
    }

    public static int header() {
        return dark ? 0xff17212b : 0xff517da2;
    }

    public static String widgetTheme() {
        return dark ? "dark" : "light";
    }
}
