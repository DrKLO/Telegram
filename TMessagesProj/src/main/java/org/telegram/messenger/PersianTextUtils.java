package org.telegram.messenger;

import java.util.ArrayList;

public final class PersianTextUtils {
    private PersianTextUtils() {
        // Utility class.
    }

    public static boolean isPersianOrArabicLetter(int codePoint) {
        if (!Character.isLetter(codePoint)) {
            return false;
        }

        return (codePoint >= 0x0600 && codePoint <= 0x06FF)
                || (codePoint >= 0x0750 && codePoint <= 0x077F)
                || (codePoint >= 0x0870 && codePoint <= 0x089F)
                || (codePoint >= 0x08A0 && codePoint <= 0x08FF)
                || (codePoint >= 0xFB50 && codePoint <= 0xFDFF)
                || (codePoint >= 0xFE70 && codePoint <= 0xFEFF);
    }

    public static boolean containsPersianOrArabicLetter(CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }

        for (int index = 0; index < text.length(); ) {
            int codePoint = Character.codePointAt(text, index);

            if (isPersianOrArabicLetter(codePoint)) {
                return true;
            }

            index += Character.charCount(codePoint);
        }

        return false;
    }

    public static boolean isPersianOrArabicDigit(int codePoint) {
        return (codePoint >= 0x0660 && codePoint <= 0x0669)
                || (codePoint >= 0x06F0 && codePoint <= 0x06F9);
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);

        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    public static ArrayList<int[]> findPersianOrArabicRanges(CharSequence text) {
        ArrayList<int[]> ranges = new ArrayList<>();

        if (text == null || text.length() == 0) {
            return ranges;
        }

        int start = -1;
        for (int index = 0; index < text.length(); ) {
            int codePoint = Character.codePointAt(text, index);

            boolean isBaseCharacter = isPersianOrArabicLetter(codePoint) || isPersianOrArabicDigit(codePoint);

            boolean continuesRange = start >= 0 &&
                    (isCombiningMark(codePoint)
                            || codePoint == 0x200C
                            || codePoint == 0x200D);

            if (isBaseCharacter) {
                if (start < 0) {
                    start = index;
                }
            } else if (!continuesRange && start >= 0) {
                ranges.add(new int[]{start, index});
                start = -1;
            }

            index += Character.charCount(codePoint);
        }

        if (start >= 0) {
            ranges.add(new int[]{start, text.length()});
        }

        return ranges;
    }
}
