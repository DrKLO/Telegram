import org.telegram.messenger.PersianTextUtils;

import java.util.ArrayList;
import java.util.Arrays;

/** Standalone text-range regressions; requires only a JDK, not Android or Gradle. */
public final class PersianTextUtilsTest {
    private static int cases;

    public static void main(String[] args) {
        String word = "\u0633\u0644\u0627\u0645";
        assertRanges(null);
        assertRanges("");
        assertRanges("\u061f", "\u061f");
        assertRanges("\u060c\u061b\u06d4", "\u060c\u061b\u06d4");
        assertRanges("\u06f1\u06f2\u066b\u06f5\u066a", "\u06f1\u06f2\u066b\u06f5\u066a");
        assertRanges(word + "!", word + "!");
        assertRanges(word + "...", word + "...");
        assertRanges("? " + word, "?", word);
        assertRanges("(" + word + ")", "(" + word + ")");
        assertRanges("English (" + word + ")", "(" + word + ")");
        assertRanges(word + " (English)", word);
        assertRanges("\u00ab" + word + "\u00bb\u060c English?", "\u00ab" + word + "\u00bb\u060c");
        assertRanges("\"" + word + "!\" English?", "\"" + word + "!\"");
        assertRanges(word + " \"English!\"", word);
        assertRanges(word + "! English?", word + "!");
        assertRanges("English! " + word + "?", word + "?");
        assertRanges(word + ": English", word + ":");
        assertRanges("English: " + word, word);
        assertRanges(word + " . . .", word, ".", ".", ".");
        assertRanges(word + "\n!", word);
        assertRanges(word + "\r\n? English", word);
        assertRanges(word + "\u2028!", word);
        assertRanges(word + " \ud83d\ude42!", word);
        assertRanges(word + "#\ufe0f\u20e3!", word);
        assertRanges(word + "*\u20e3?", word);
        assertRanges(word + "\u203c\ufe0f", word);
        assertRanges("English! (123), 4.5% #\ufe0f\u20e3");
        assertRanges("!?...");
        assertRanges("Hi \ud83d\ude42 \u0645\u06cc\u200c\u0631\u0648\u0645\u064e 123 \u06f1\u06f2",
                "\u0645\u06cc\u200c\u0631\u0648\u0645\u064e", "\u06f1\u06f2");
        assertRanges(word + " 123.45 English!", word);
        assertRanges("\ud83d\ude42" + word + "!", word + "!");
        assertRanges("\ud800" + word + "\udc00", word);
        assertLongPunctuationRunIsLinear();
        System.out.println("Passed " + cases + " text-range regressions.");
    }

    private static void assertRanges(String text, String... expected) {
        ArrayList<String> actual = new ArrayList<>();
        int previousEnd = 0;
        for (int[] range : PersianTextUtils.findPersianOrArabicRanges(text)) {
            if (range[0] < previousEnd || range[0] >= range[1] || range[1] > text.length()) {
                throw new AssertionError("Invalid range " + Arrays.toString(range));
            }
            previousEnd = range[1];
            actual.add(text.substring(range[0], range[1]));
        }
        if (!Arrays.asList(expected).equals(actual)) {
            throw new AssertionError("For " + text + ": expected " + Arrays.toString(expected) + ", got " + actual);
        }
        cases++;
    }

    private static void assertLongPunctuationRunIsLinear() {
        CountingSequence text = new CountingSequence("\u0633" + "!".repeat(100_000));
        ArrayList<int[]> ranges = PersianTextUtils.findPersianOrArabicRanges(text);
        if (ranges.size() != 1 || ranges.get(0)[0] != 0 || ranges.get(0)[1] != text.length()
                || text.reads > text.length() * 12L) {
            throw new AssertionError("Long punctuation run did not retain linear character access");
        }
        cases++;
    }

    private static final class CountingSequence implements CharSequence {
        private final String value;
        long reads;

        CountingSequence(String value) {
            this.value = value;
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            reads++;
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }
    }
}
