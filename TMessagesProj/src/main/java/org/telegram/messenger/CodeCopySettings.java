package org.telegram.messenger;

/** Policy for regular preformatted code blocks. Lengths are UTF-16 code units. */
public final class CodeCopySettings {
    public static final int DEFAULT_MIN_LENGTH = 75;
    public static final String KEY = "codeCopyMinLength";

    private CodeCopySettings() {}

    public static int normalize(int value) {
        return value >= 1 ? value : DEFAULT_MIN_LENGTH;
    }

    /** Null means invalid input; do not silently turn malformed input into a setting. */
    public static Integer parse(String text) {
        if (text == null || !text.matches("[0-9]+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(text);
            return value >= 1 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean shouldShow(int length, int minimum, boolean noforwards) {
        return !noforwards && length >= normalize(minimum);
    }
}
