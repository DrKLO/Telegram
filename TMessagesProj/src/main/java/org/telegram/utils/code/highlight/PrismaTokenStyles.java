package org.telegram.utils.code.highlight;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Prism names remain in Tokenization; this is only the default Android presentation policy. */
public final class PrismaTokenStyles {
    public static final int COLOR_MASK = 0xff;
    public static final int BOLD = 1 << 8;
    public static final int ITALIC = 1 << 9;

    public static final int KEYWORD = 1;
    public static final int OPERATOR = 2;
    public static final int CONSTANT = 3;
    public static final int STRING = 4;
    public static final int NUMBER = 5;
    public static final int COMMENT = 6;
    public static final int FUNCTION = 7;
    public static final int BUILTIN = 8;
    public static final int CLASS_NAME = 9;
    public static final int PROPERTY = 10;
    public static final int ATTRIBUTE_NAME = 11;
    public static final int ATTRIBUTE_VALUE = 12;
    public static final int SELECTOR = 13;
    public static final int TAG = 14;
    public static final int SYMBOL = 15;
    public static final int REGEX = 16;
    public static final int URL = 17;
    public static final int ATRULE = 18;
    public static final int INSERTED = 19;
    public static final int DELETED = 20;
    public static final int PUNCTUATION = 21;

    private static final Map<String, Integer> DEFAULT_STYLES = createStyles();
    private final int[] nameStyles;

    PrismaTokenStyles(String[] names) {
        nameStyles = new int[names.length];
        for (int i = 0; i < names.length; ++i) {
            // Alias fields may contain several CSS classes. Resolve once per engine,
            // without allocating a substring for each highlighted token.
            int style = 0;
            for (String name : names[i].split("\\s+")) {
                Integer entry = DEFAULT_STYLES.get(name);
                if (entry != null) {
                    if ((style & COLOR_MASK) == 0) style |= entry & COLOR_MASK;
                    style |= entry & ~COLOR_MASK;
                }
            }
            nameStyles[i] = style;
        }
    }

    private static Map<String, Integer> createStyles() {
        Map<String, Integer> styles = new HashMap<>();
        add(styles, KEYWORD, "keyword");
        add(styles, OPERATOR, "operator", "entity");
        add(styles, CONSTANT, "constant", "boolean");
        add(styles, STRING, "string", "string-literal", "char", "template-string");
        add(styles, NUMBER, "number");
        add(
                styles,
                COMMENT,
                "comment",
                "block-comment",
                "comment-multiline",
                "prolog",
                "doctype",
                "cdata");
        add(
                styles,
                FUNCTION,
                "function",
                "function-definition",
                "function-variable",
                "function-name");
        add(styles, BUILTIN, "builtin", "type");
        add(styles, CLASS_NAME, "class-name");
        add(styles, PROPERTY, "property");
        add(styles, ATTRIBUTE_NAME, "attr-name");
        add(styles, ATTRIBUTE_VALUE, "attr-value");
        add(styles, SELECTOR, "selector");
        add(styles, TAG, "tag");
        add(styles, SYMBOL, "symbol");
        add(styles, REGEX, "regex");
        add(styles, URL, "url");
        add(styles, ATRULE, "atrule");
        add(styles, INSERTED, "inserted");
        add(styles, DELETED, "deleted");
        add(styles, PUNCTUATION, "punctuation");
        add(styles, BOLD, "bold");
        add(styles, ITALIC, "italic");
        add(styles, KEYWORD | BOLD, "important");
        return styles;
    }

    private static void add(Map<String, Integer> target, int style, String... names) {
        for (String name : names) target.put(name, style);
    }

    /** Produces ordered, non-overlapping [start, end, color-group | font-flags] triples. */
    int[] resolve(PrismaHighlighter.Tokenization tokens) {
        int count = tokens.size();
        if (count == 0) return new int[0];
        int[] inherited = new int[count];
        int[] ends = new int[count];
        int[] stackStyles = new int[count];
        RangeBuilder result = new RangeBuilder();
        int top = -1;
        int cursor = 0;
        for (int i = 0; i < count; ++i) {
            int start = tokens.start(i);
            int end = tokens.end(i);
            while (top >= 0 && ends[top] <= start) {
                result.add(cursor, ends[top], stackStyles[top]);
                cursor = ends[top--];
            }
            if (top >= 0) result.add(cursor, start, stackStyles[top]);
            cursor = start;
            int parentStyle = tokens.parent(i) < 0 ? 0 : inherited[tokens.parent(i)];
            int own = nameStyles[tokens.typeId(i)];
            int alias = nameStyles[tokens.aliasId(i)];
            int color = (alias & COLOR_MASK) != 0 ? alias & COLOR_MASK : own & COLOR_MASK;
            int style =
                    (color != 0 ? color : parentStyle & COLOR_MASK)
                            | ((parentStyle | own | alias) & ~COLOR_MASK);
            inherited[i] = style;
            ends[++top] = end;
            stackStyles[top] = style;
        }
        while (top >= 0) {
            result.add(cursor, ends[top], stackStyles[top]);
            cursor = ends[top--];
        }
        return result.finish();
    }

    public static int legacyGroup(int style) {
        switch (style & COLOR_MASK) {
            case BUILTIN:
            case ATRULE:
                return KEYWORD;
            case CLASS_NAME:
            case PROPERTY:
            case TAG:
            case SYMBOL:
                return CONSTANT;
            case ATTRIBUTE_NAME:
            case SELECTOR:
                return FUNCTION;
            case ATTRIBUTE_VALUE:
            case REGEX:
            case URL:
            case INSERTED:
                return STRING;
            case DELETED:
                return COMMENT;
            case PUNCTUATION:
                return OPERATOR;
            default:
                return style & COLOR_MASK;
        }
    }

    private static final class RangeBuilder {
        private int[] values = new int[48];
        private int size;

        void add(int start, int end, int style) {
            if (style == 0 || start >= end) return;
            if (size >= 3 && values[size - 1] == style && values[size - 2] == start) {
                values[size - 2] = end;
                return;
            }
            if (size + 3 > values.length) values = Arrays.copyOf(values, values.length * 2);
            values[size++] = start;
            values[size++] = end;
            values[size++] = style;
        }

        int[] finish() {
            return Arrays.copyOf(values, size);
        }
    }
}
