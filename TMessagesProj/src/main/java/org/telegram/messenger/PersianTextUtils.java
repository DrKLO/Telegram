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

        return isArabicBlock(codePoint);
    }

    private static boolean isArabicBlock(int codePoint) {
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

    public static boolean isPersianOrArabicPunctuation(int codePoint) {
        return isArabicBlock(codePoint) && isPunctuation(codePoint);
    }

    private static boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isSharedPunctuation(CharSequence text, int index, int codePoint) {
        // Keep emoji punctuation and variation/keycap sequences in the original font.
        if (!isPunctuation(codePoint) || isPersianOrArabicPunctuation(codePoint)
                || codePoint == 0x203C || codePoint == 0x2049
                || codePoint == 0x3030 || codePoint == 0x303D) {
            return false;
        }
        int next = index + Character.charCount(codePoint);
        return next >= text.length() || text.charAt(next) != 0xFE0F && text.charAt(next) != 0x20E3;
    }

    private static boolean isHorizontalSpace(int codePoint) {
        return codePoint != '\n' && codePoint != '\r' && codePoint != 0x0085
                && codePoint != 0x2028 && codePoint != 0x2029
                && (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint));
    }

    private static final int SCRIPT_NONE = 0;
    private static final int SCRIPT_ARABIC = 1;
    private static final int SCRIPT_OTHER = 2;

    private static int scriptOf(int codePoint) {
        if (isPersianOrArabicLetter(codePoint) || isPersianOrArabicDigit(codePoint)
                || isPersianOrArabicPunctuation(codePoint)) {
            return SCRIPT_ARABIC;
        }
        return Character.isLetterOrDigit(codePoint) ? SCRIPT_OTHER : SCRIPT_NONE;
    }

    private static boolean prefersFollowingScript(CharSequence text, int index, int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.START_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION) {
            return true;
        }
        if (codePoint == '\"' || codePoint == '\'') {
            if (index == 0) {
                return true;
            }
            int previous = Character.codePointBefore(text, index);
            return isHorizontalSpace(previous) || Character.getType(previous) == Character.START_PUNCTUATION;
        }
        return false;
    }

    public static ArrayList<int[]> findPersianOrArabicRanges(CharSequence text) {
        ArrayList<int[]> ranges = new ArrayList<>();

        if (text == null || text.length() == 0) {
            return ranges;
        }

        int start = -1;
        int previousScript = SCRIPT_NONE;
        int nextScript = SCRIPT_NONE;
        int contextEnd = -1;
        for (int index = 0; index < text.length(); ) {
            int codePoint = Character.codePointAt(text, index);
            int script = scriptOf(codePoint);
            boolean sharedPunctuation = isSharedPunctuation(text, index, codePoint);
            boolean isBaseCharacter = script == SCRIPT_ARABIC;

            if (sharedPunctuation) {
                if (index >= contextEnd) {
                    // Scan each punctuation/space run once, including long repeated punctuation.
                    contextEnd = index + Character.charCount(codePoint);
                    while (contextEnd < text.length()) {
                        int next = Character.codePointAt(text, contextEnd);
                        if (!isHorizontalSpace(next) && !isSharedPunctuation(text, contextEnd, next)) {
                            break;
                        }
                        contextEnd += Character.charCount(next);
                    }
                    nextScript = contextEnd < text.length()
                            ? scriptOf(Character.codePointAt(text, contextEnd)) : SCRIPT_NONE;
                }
                int contextScript;
                if (prefersFollowingScript(text, index, codePoint)) {
                    contextScript = nextScript != SCRIPT_NONE ? nextScript : previousScript;
                } else {
                    contextScript = previousScript != SCRIPT_NONE ? previousScript : nextScript;
                }
                isBaseCharacter = contextScript == SCRIPT_ARABIC;
            }

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

            if (script != SCRIPT_NONE) {
                previousScript = script;
            } else if (!sharedPunctuation && !isHorizontalSpace(codePoint)
                    && !isCombiningMark(codePoint) && codePoint != 0x200C && codePoint != 0x200D) {
                previousScript = SCRIPT_NONE;
            }
            index += Character.charCount(codePoint);
        }

        if (start >= 0) {
            ranges.add(new int[]{start, text.length()});
        }

        return ranges;
    }
}
