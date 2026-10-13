package org.telegram.utils.code.highlight;

/**
 * Native libprisma tokenizer. Reuse an instance on a background queue and close it when its owner
 * is destroyed. All native calls on one instance are serialized.
 *
 * <p>Telegram NativeLoader loads the main native library containing PrismaJni.cpp before
 * constructing this class. Native methods must be kept by R8.
 */
public final class PrismaHighlighter implements AutoCloseable {

    public static final int TOKEN_NONE = 0;
    public static final int TOKEN_KEYWORD = 1;
    public static final int TOKEN_OPERATOR = 2;
    public static final int TOKEN_CONSTANT = 3;
    public static final int TOKEN_STRING = 4;
    public static final int TOKEN_NUMBER = 5;
    public static final int TOKEN_COMMENT = 6;
    public static final int TOKEN_FUNCTION = 7;

    private long nativeHandle;
    private final String[] tokenNames;
    private final PrismaTokenStyles styles;

    /** Creates an instance from the complete contents of assets/grammars.dat. */
    public PrismaHighlighter(byte[] grammars) {
        if (grammars == null) {
            throw new NullPointerException("grammars");
        }
        nativeHandle = nativeCreate(grammars);
        if (nativeHandle == 0) {
            throw new IllegalStateException("Native highlighter was not created");
        }
        try {
            tokenNames = nativeGetTokenNames(nativeHandle);
            if (tokenNames == null || tokenNames.length == 0 || !"".equals(tokenNames[0])) {
                throw new IllegalStateException("Invalid native token name table");
            }
            styles = new PrismaTokenStyles(tokenNames);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Returns triples {@code start, end, tokenType} with UTF-16 indices suitable for Android spans.
     * End is exclusive. Ranges are ordered and do not overlap. Unstyled text is omitted. Adjacent
     * ranges with the same type are merged. Language identifiers are ASCII case-insensitive;
     * aliases are supported. Empty or unknown languages return an empty array.
     *
     * <p>This method is synchronous and should be called on a background queue.
     */
    public synchronized int[] highlight(String text, String language) {
        return tokenize(text, language).legacyRanges();
    }

    /** Preserves every Prism type, alias and parent relation, independently of the theme. */
    public synchronized Tokenization tokenize(String text, String language) {
        checkOpen();
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (language == null) {
            throw new NullPointerException("language");
        }
        int[] records = nativeHighlight(nativeHandle, text, language);
        validateRecords(records, text, tokenNames.length);
        return new Tokenization(records, tokenNames, styles);
    }

    private static void validateRecords(int[] records, String text, int nameCount) {
        if (records == null || records.length % 5 != 0) {
            throw new IllegalStateException("Invalid native token array");
        }
        int previousStart = 0;
        for (int offset = 0; offset < records.length; offset += 5) {
            int start = records[offset];
            int end = records[offset + 1];
            int type = records[offset + 2];
            int alias = records[offset + 3];
            int parent = records[offset + 4];
            if (start < previousStart
                    || end <= start
                    || end > text.length()
                    || type < 0
                    || type >= nameCount
                    || alias < 0
                    || alias >= nameCount
                    || parent < -1
                    || parent >= offset / 5
                    || splitsSurrogate(text, start)
                    || splitsSurrogate(text, end)) {
                throw new IllegalStateException("Invalid native token range");
            }
            if (parent >= 0) {
                int parentOffset = parent * 5;
                if (start < records[parentOffset] || end > records[parentOffset + 1]) {
                    throw new IllegalStateException("Native token is outside its parent");
                }
            }
            previousStart = start;
        }
    }

    private static boolean splitsSurrogate(String text, int offset) {
        return offset > 0
                && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset));
    }

    /** Immutable preorder token tree stored as five integers per syntax node. */
    public static final class Tokenization {
        private final int[] records;
        private final String[] names;
        private final PrismaTokenStyles styles;
        private volatile int[] styledRanges;

        Tokenization(int[] records, String[] names, PrismaTokenStyles styles) {
            this.records = records;
            this.names = names;
            this.styles = styles;
        }

        public int size() {
            return records.length / 5;
        }

        public int start(int index) {
            return records[index * 5];
        }

        public int end(int index) {
            return records[index * 5 + 1];
        }

        public String type(int index) {
            return names[typeId(index)];
        }

        public String alias(int index) {
            return names[aliasId(index)];
        }

        public int parent(int index) {
            return records[index * 5 + 4];
        }

        int typeId(int index) {
            return records[index * 5 + 2];
        }

        int aliasId(int index) {
            return records[index * 5 + 3];
        }

        // Package-private: adapters read the cached array, and must never modify it.
        synchronized int[] styledRanges() {
            if (styledRanges == null) styledRanges = styles.resolve(this);
            return styledRanges;
        }

        public int[] copyStyledRanges() {
            return styledRanges().clone();
        }

        int[] legacyRanges() {
            int[] styled = styledRanges();
            int[] result = new int[styled.length];
            int size = 0;
            for (int i = 0; i < styled.length; i += 3) {
                int group = PrismaTokenStyles.legacyGroup(styled[i + 2]);
                if (group == 0) continue;
                if (size >= 3 && result[size - 1] == group && result[size - 2] == styled[i]) {
                    result[size - 2] = styled[i + 1];
                } else {
                    result[size++] = styled[i];
                    result[size++] = styled[i + 1];
                    result[size++] = group;
                }
            }
            return java.util.Arrays.copyOf(result, size);
        }

        long estimatedBytes() {
            return 64L
                    + records.length * 4L
                    + (styledRanges == null ? 0 : styledRanges.length * 4L);
        }
    }

    /** Returns the language identifiers exposed by libprisma, sorted by key. */
    public synchronized String[] getLanguages() {
        checkOpen();
        return nativeGetLanguages(nativeHandle);
    }

    @Override
    public synchronized void close() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    private void checkOpen() {
        if (nativeHandle == 0) {
            throw new IllegalStateException("Highlighter is closed");
        }
    }

    private static native long nativeCreate(byte[] grammars);

    private static native int[] nativeHighlight(long handle, String text, String language);

    private static native String[] nativeGetLanguages(long handle);

    private static native String[] nativeGetTokenNames(long handle);

    private static native void nativeDestroy(long handle);
}
