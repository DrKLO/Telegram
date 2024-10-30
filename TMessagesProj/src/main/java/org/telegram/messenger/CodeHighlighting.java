package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TextStyleSpan;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CodeHighlighting {

    public static final int MATCH_NONE = 0;
    public static final int MATCH_KEYWORD = 1;
    public static final int MATCH_OPERATOR = 2;
    public static final int MATCH_CONSTANT = 3;
    public static final int MATCH_STRING = 4;
    public static final int MATCH_NUMBER = 5;
    public static final int MATCH_COMMENT = 6;
    public static final int MATCH_FUNCTION = 7;

    public static int getTextSizeDecrement(int codeLength) {
        if (codeLength > 120) return 5;
        if (codeLength > 50) return 3;
        return 2;
    }

    public static class Span extends CharacterStyle {

        public final String lng;
        public final String code;
        public final int currentType;
        public final TextStyleSpan.TextStyleRun style;
        public final float decrementSize;

        public final boolean smallerSize;

        public Span(boolean smallerSize, int type, TextStyleSpan.TextStyleRun style, String lng, String code) {
            this.smallerSize = smallerSize;

            this.lng = lng;
            this.code = code;
            this.decrementSize = getTextSizeDecrement(code == null ? 0 : code.length());
            this.currentType = type;
            this.style = style;
        }

        @Override
        public void updateDrawState(TextPaint p) {
            if (smallerSize) {
                p.setTextSize(dp(SharedConfig.fontSize - decrementSize));
            }
            if (currentType == 2) {
                p.setColor(0xffffffff);
            } else if (currentType == 1) {
                p.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            } else {
                p.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
            }
            if (style != null) {
                style.applyStyle(p);
            } else {
                p.setTypeface(Typeface.MONOSPACE);
                p.setUnderlineText(false);
            }
        }
    }

    public static class ColorSpan extends CharacterStyle {
        public int group;
        public ColorSpan(int match) {
            this.group = match;
        }

        public int getColorKey() {
            switch (this.group) {
                case MATCH_KEYWORD:  return Theme.key_code_keyword;
                case MATCH_OPERATOR: return Theme.key_code_operator;
                case MATCH_CONSTANT: return Theme.key_code_constant;
                case MATCH_STRING:   return Theme.key_code_string;
                case MATCH_NUMBER:   return Theme.key_code_number;
                case MATCH_COMMENT:  return Theme.key_code_comment;
                case MATCH_FUNCTION: return Theme.key_code_function;
                default: return -1;
            }
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(Theme.getColor(getColorKey()));
        }
    }

    // setting 100-300 spans is slow
    // fast way to fix this is to do it on another thread, and not give access to data until it's finished processing
    public static class LockedSpannableString extends SpannableString {
        public LockedSpannableString(CharSequence text) {
            super(text);
        }

        private boolean ready;
        public void unlock() {
            this.ready = true;
        }

        @Override
        public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
            if (!ready) return (T[]) Array.newInstance(kind, 0);
            return super.getSpans(queryStart, queryEnd, kind);
        }

        @Override
        public int nextSpanTransition(int start, int limit, Class kind) {
            if (!ready) return limit;
            return super.nextSpanTransition(start, limit, kind);
        }

        @Override
        public int getSpanStart(Object what) {
            if (!ready) return -1;
            return super.getSpanStart(what);
        }

        @Override
        public int getSpanEnd(Object what) {
            if (!ready) return -1;
            return super.getSpanEnd(what);
        }

        @Override
        public int getSpanFlags(Object what) {
            if (!ready) return 0;
            return super.getSpanFlags(what);
        }
    }

    private static final HashMap<String, Highlighting> processedHighlighting = new HashMap<>();
    private static class Highlighting {
        String text, language;
        SpannableString result;
    }

    public static SpannableString getHighlighted(String text, String language) {
        if (TextUtils.isEmpty(language)) {
            return new SpannableString(text);
        }
        final String key = language + "`" + text;
        Highlighting process = processedHighlighting.get(key);
        if (process == null) {
            process = new Highlighting();
            process.text = text;
            process.language = language;
            process.result = new LockedSpannableString(text);

            highlight(process.result, 0, process.result.length(), language, 0, null, true);

            Iterator<String> keys = processedHighlighting.keySet().iterator();
            while (keys.hasNext() && processedHighlighting.size() > 8) {
                keys.next();
                keys.remove();
            }

            processedHighlighting.put(key, process);
        }
        return process.result;
    }

    public static void highlight(Spannable text, int start, int end, String lng, int type, TextStyleSpan.TextStyleRun style, boolean smallerSize) {
        if (text == null) {
            return;
        }
//        text.setSpan(new Span(smallerSize, type, style, lng, text.subSequence(start, end).toString(), start <= 0, end >= text.length() - 1, start, end), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Utilities.searchQueue.postRunnable(() -> {
            if (compiledPatterns == null)
                parse();
            long S = System.currentTimeMillis();
            final StringToken[][] tokens = new StringToken[1][];
            try {
                tokens[0] = tokenize(text.subSequence(start, end).toString(), compiledPatterns == null ? null : compiledPatterns.get(lng), 0).toArray();
            } catch (Exception e) {
                FileLog.e(e);
            }
            FileLog.d("[CodeHighlighter] tokenize took " + (System.currentTimeMillis() - S) + "ms");

            long S2 = System.currentTimeMillis();
            ArrayList<CachedToSpan> spans = new ArrayList<>();
            colorize(text, start, end, tokens[0], -1, spans);
            FileLog.d("[CodeHighlighter] colorize took " + (System.currentTimeMillis() - S2) + "ms");

            if (!spans.isEmpty()) {
                if (text instanceof LockedSpannableString) {
                    long S3 = System.currentTimeMillis();
                    for (int i = 0; i < spans.size(); ++i) {
                        CachedToSpan span = spans.get(i);
                        text.setSpan(new ColorSpan(span.group), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    FileLog.d("[CodeHighlighter] applying " + spans.size() + " colorize spans took " + (System.currentTimeMillis() - S3) + "ms in another thread");
                    AndroidUtilities.runOnUIThread(() -> {
                        ((LockedSpannableString) text).unlock();
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        long S3 = System.currentTimeMillis();
                        for (int i = 0; i < spans.size(); ++i) {
                            CachedToSpan span = spans.get(i);
                            text.setSpan(new ColorSpan(span.group), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        FileLog.d("[CodeHighlighter] applying " + spans.size() + " colorize spans took " + (System.currentTimeMillis() - S3) + "ms");
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                    });
                }
            }
        });
    }

    private static void colorize(Spannable text, int start, int end, StringToken[] tokens, int defGroup, ArrayList<CachedToSpan> result) {
        if (tokens == null) {
            return;
        }
        int p = start;
        for (int i = 0; i < tokens.length && p < end; ++i) {
            StringToken t = tokens[i];
            if (t == null) continue;
            if (t.string != null) {
                int group = t.group;
                if (defGroup != -1) {
                    group = defGroup;
                }
                if (group == -1) {
                    p += t.length();
                    continue;
                }

                result.add(new CachedToSpan(group, p, p + t.length()));
//                text.setSpan(new ColorSpan(group), p, p + t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (t.inside != null) {
                colorize(text, p, p + t.length(), t.inside.toArray(), t.group, result);
            }
            p += t.length();
        }
    }

    private static class CachedToSpan {
        public int group;
        public int start, end;
        public CachedToSpan(int group, int start, int end) {
            this.group = group;
            this.start = start;
            this.end = end;
        }
    }

    private static LinkedList tokenize(String text, TokenPattern[] grammar, int depth) {
        return tokenize(text, grammar, null, depth);
    }

    private static LinkedList tokenize(String text, TokenPattern[] grammar, TokenPattern ignorePattern, int depth) {
        LinkedList list = new LinkedList();
        list.addAfter(list.head, new StringToken(text));
        grammar = flatRest(grammar);
        matchGrammar(text, list, grammar, list.head, 0, null, ignorePattern, depth);
        return list;
    }

    private static TokenPattern[] flatRest(TokenPattern[] patterns) {
        if (patterns == null) {
            return null;
        }
        ArrayList<TokenPattern> result = null;
        for (int i = 0; i < patterns.length; ++i) {
            if (patterns[i].pattern != null && "REST".equals(patterns[i].pattern.patternSource)) {
                if (result == null) {
                    result = new ArrayList<TokenPattern>();
                    Collections.addAll(result, patterns);
                }
                result.remove(patterns[i]);
                if (!TextUtils.isEmpty(patterns[i].insideLanguage) && compiledPatterns != null) {
                    TokenPattern[] grammar = compiledPatterns.get(patterns[i].insideLanguage);
                    if (grammar != null) {
                        Collections.addAll(result, grammar);
                    }
                }
            }
        }
        if (result != null) {
            return result.toArray(new TokenPattern[0]);
        }
        return patterns;
    }

    private static void matchGrammar(String text, LinkedList tokenList, TokenPattern[] grammar, Node startNode, int startPos, RematchOptions rematch, TokenPattern ignorePattern, int depth) {
        if (grammar == null || depth > 20) {
            return;
        }
        for (TokenPattern pattern : grammar) {
            if (pattern == ignorePattern || rematch != null && rematch.cause == pattern) {
                return;
            }


            Node currentNode = startNode.next;
            int pos = startPos;
            for (;
                currentNode != tokenList.tail;
                pos += currentNode.value.length(), currentNode = currentNode.next
            ) {
                if (rematch != null && pos >= rematch.reach) {
                    return;
                }

                if (tokenList.length > text.length()) {
                    FileLog.e("[CodeHighlighter] Something went terribly wrong, ABORT, ABORT!");
                    return;
                }

                if (currentNode.value.string == null || currentNode.value.token) {
                    continue;
                }

                String str = currentNode.value.string;

                int removeCount = 1;
                Match match;
                if (pattern.greedy) {
                    match = matchPattern(pattern, pos, text);
                    if (match == null || match.index >= text.length()) {
                        break;
                    }

                    int from = match.index;
                    int to = match.index + match.length;
                    int p = pos;

                    p += currentNode.value.length();
                    while (from >= p) {
                        currentNode = currentNode.next;
                        p += currentNode.value.length();
                    }
                    p -= currentNode.value.length();
                    pos = p;

                    if (currentNode.value.string == null || currentNode.value.token) {
                        continue;
                    }

                    for (
                        Node k = currentNode;
                        k != tokenList.tail && (p < to || !k.value.token);
                        k = k.next
                    ) {
                        removeCount++;
                        p += k.value.length();
                    }
                    removeCount--;

                    str = text.substring(pos, p);
                    match.index -= pos;
                } else {
                    match = matchPattern(pattern, 0, str);
                    if (match == null) {
                        continue;
                    }
                }

                int from = match.index;
                String before = str.substring(0, from);
                String after = str.substring(from + match.length);

                int reach = pos + str.length();
                if (rematch != null && reach > rematch.reach) {
                    rematch.reach = reach;
                }

                Node removeFrom = currentNode.prev;
                if (before.length() > 0) {
                    removeFrom = tokenList.addAfter(removeFrom, new StringToken(before));
                    pos += before.length();
                }

                tokenList.removeRange(removeFrom, removeCount);

                StringToken wrapped;
                if (pattern.insideTokenPatterns != null) {
                    wrapped = new StringToken(pattern.group, tokenize(match.string, pattern.insideTokenPatterns, pattern, depth + 1), match.length);
                } else if (pattern.insideLanguage != null) {
                    wrapped = new StringToken(pattern.group, tokenize(match.string, compiledPatterns.get(pattern.insideLanguage), pattern, depth + 1), match.length);
                } else {
                    wrapped = new StringToken(pattern.group, match.string);
                }
                currentNode = tokenList.addAfter(removeFrom, wrapped);

                if (after.length() > 0) {
                    tokenList.addAfter(currentNode, new StringToken(after));
                }

                if (removeCount > 1) {
                    RematchOptions nestedRematch = new RematchOptions();
                    nestedRematch.cause = pattern;
                    nestedRematch.reach = reach;
                    matchGrammar(text, tokenList, grammar, currentNode.prev, pos, nestedRematch, ignorePattern, depth + 1);

                    if (rematch != null && nestedRematch.reach > rematch.reach) {
                        rematch.reach = nestedRematch.reach;
                    }
                }
            }
        }
    }

    private static Match matchPattern(TokenPattern pattern, int pos, String text) {
        try {
            Matcher matcher = pattern.pattern.getPattern().matcher(text);
            matcher.region(pos, text.length());
            if (!matcher.find()) {
                return null;
            }
            Match match = new Match();
            match.index = matcher.start();
            if (pattern.lookbehind && matcher.groupCount() >= 1) {
                match.index += matcher.end(1) - matcher.start(1);
            }
            match.length = matcher.end() - match.index;
            match.string = text.substring(match.index, match.index + match.length);
            return match;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static class RematchOptions {
        TokenPattern cause;
        int reach;
    }

    private static class Match {
        int index;
        int length;
        String string;
    }

    private static class LinkedList {
        public Node head;
        public Node tail;
        public int length = 0;

        public LinkedList() {
            head = new Node();
            tail = new Node();
            head.next = tail;
            tail.prev = head;
        }

        public Node addAfter(Node node, StringToken value) {
            Node next = node.next;
            Node newNode = new Node();
            newNode.value = value;
            newNode.prev = node;
            newNode.next = next;
            node.next = newNode;
            next.prev = newNode;
            length++;
            return newNode;
        }

        public void removeRange(Node node, int count) {
            Node next = node.next;
            int i = 0;
            for (; i < count && next != tail; ++i) {
                next = next.next;
            }
            node.next = next;
            next.prev = node;
            length -= i;
        }

        public StringToken[] toArray() {
            StringToken[] array = new StringToken[length];
            Node node = head.next;
            for (int i = 0; i < length && node != tail; node = node.next, ++i) {
                array[i] = node.value;
            }
            return array;
        }
    }
    private static class Node {
        public Node prev;
        public Node next;
        public StringToken value;
    }
    private static class StringToken {
        final boolean token;
        final int group;
        final String string;
        final CodeHighlighting.LinkedList inside;
        final int insideLength;
        public StringToken(int group, String string) {
            this.token = true;
            this.group = group;
            this.string = string;
            this.inside = null;
            this.insideLength = 0;
        }
        public StringToken(int group, CodeHighlighting.LinkedList inside, int len) {
            this.token = true;
            this.group = group;
            this.string = null;
            this.inside = inside;
            this.insideLength = len;
        }
        public StringToken(String string) {
            this.token = false;
            this.group = -1;
            this.string = string;
            this.inside = null;
            this.insideLength = 0;
        }

        public int length() {
            if (string != null) {
                return string.length();
            }
            return insideLength;
        }
    }

    private static HashMap<String, TokenPattern[]> compiledPatterns;
    private static void parse() {
        GZIPInputStream zipStream = null;
        BufferedInputStream bufStream = null;
        InputStream stream = null;
        try {
            long start = System.currentTimeMillis();
            stream = ApplicationLoader.applicationContext.getAssets().open("codelng.gzip");
            zipStream = new GZIPInputStream(stream, 65536);
            bufStream = new BufferedInputStream(zipStream, 65536);
            StreamReader reader = new StreamReader(bufStream);

            HashMap<Integer, String[]> languages = new HashMap<>();
            int languagesCount = reader.readUint8();
            for (int i = 0; i < languagesCount; ++i) {
                int lngid = reader.readUint8();
                int aliasesCount = reader.readUint8();
                String[] aliases = new String[aliasesCount];
                for (int j = 0; j < aliasesCount; ++j) {
                    aliases[j] = reader.readString();
                }
                languages.put(lngid, aliases);
            }

            int patternsCount = reader.readUint16();
            ParsedPattern[] patterns = new ParsedPattern[patternsCount];
            for (int i = 0; i < patternsCount; ++i) {
                patterns[i] = new ParsedPattern();
                int b = reader.readUint8();
                patterns[i].multiline = (b & 1) != 0;
                patterns[i].caseInsensitive = (b & 2) != 0;
                patterns[i].pattern = reader.readString();
            }

            if (compiledPatterns == null) {
                compiledPatterns = new HashMap<>();
            }
            for (int i = 0; i < languagesCount; ++i) {
                int lngid = reader.readUint8();
                TokenPattern[] tokens = readTokens(reader, patterns, languages);
                String[] aliases = languages.get(lngid);
                for (String alias : aliases) {
                    compiledPatterns.put(alias, tokens);
                }
            }

            FileLog.d("[CodeHighlighter] Successfully read " + languagesCount + " languages, " + patternsCount + " patterns in " + (System.currentTimeMillis() - start) + "ms from codelng.gzip");

        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (zipStream != null) {
                    zipStream.close();
                }
                if (bufStream != null) {
                    bufStream.close();
                }
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private static class ParsedPattern {
        String pattern;
        boolean multiline;
        boolean caseInsensitive;
        public int flags() {
            return (multiline ? Pattern.MULTILINE : 0) | (caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        }

        private CachedPattern cachedPattern;
        public CachedPattern getCachedPattern() {
            if (cachedPattern == null) {
                cachedPattern = new CachedPattern(pattern, flags());
            }
            return cachedPattern;
        }
    }

    private static TokenPattern[] readTokens(StreamReader reader, ParsedPattern[] patterns, HashMap<Integer, String[]> languages) throws IOException {
        int count = reader.readUint8();
        TokenPattern[] tokens = new TokenPattern[count];
        for (int i = 0; i < count; ++i) {
            int b = reader.readUint8();
            int type = b & 0x03;
            int match = (b >> 2) & 0x07;
            boolean greedy = (b & (1 << 5)) != 0;
            boolean lookbehind = (b & (1 << 6)) != 0;
            int pid = reader.readUint16();
            if (type == 0) {
                tokens[i] = new TokenPattern(match, patterns[pid].getCachedPattern());
            } else if (type == 1) {
                if (match == 0) {
                    tokens[i] = new TokenPattern(patterns[pid].getCachedPattern(), readTokens(reader, patterns, languages));
                } else {
                    tokens[i] = new TokenPattern(match, patterns[pid].getCachedPattern(), readTokens(reader, patterns, languages));
                }
            } else if (type == 2) {
                int lid = reader.readUint8();
                tokens[i] = new TokenPattern(patterns[pid].getCachedPattern(), languages.get(lid)[0]);
            }
            if (greedy) tokens[i].greedy = true;
            if (lookbehind) tokens[i].lookbehind = true;
        }
        return tokens;
    }

    private static class StreamReader {
        private final InputStream is;
        public StreamReader(InputStream is) {
            this.is = is;
        }

        public int readUint8() throws IOException {
            return is.read() & 0xFF;
        }
        public int readUint16() throws IOException {
            return (is.read() & 0xFF) | ((is.read() & 0xFF) << 8);
        }
        public String readString() throws IOException {
            int l = is.read();
            if (l >= 254) {
                l = is.read() | (is.read() << 8) | (is.read() << 16);
            }
            byte[] b = new byte[l];
            for (int i = 0; i < l; ++i)
                b[i] = (byte) is.read();
            return new String(b, StandardCharsets.US_ASCII);
        }
    }

    private static class TokenPattern {
        public final CachedPattern pattern;
        public int group = -1;
        public boolean lookbehind;
        public boolean greedy;
        public TokenPattern[] insideTokenPatterns;
        public String insideLanguage;

        public TokenPattern(int group, CachedPattern pattern) {
            this.pattern = pattern;
            this.group = group;
        }

        public TokenPattern(CachedPattern pattern, TokenPattern...inside) {
            this.pattern = pattern;
            this.insideTokenPatterns = inside;
        }

        public TokenPattern(CachedPattern pattern, String lang) {
            this.pattern = pattern;
            this.insideLanguage = lang;
        }

        public TokenPattern(int group, CachedPattern pattern, TokenPattern...inside) {
            this.group = group;
            this.pattern = pattern;
            this.insideTokenPatterns = inside;
        }
    }

    private static class CachedPattern {
        private Pattern pattern;
        private String patternSource;
        private int patternSourceFlags;

        public CachedPattern(String pattern, int flags) {
            patternSource = pattern;
            patternSourceFlags = flags;
        }

        public Pattern getPattern() {
            if (pattern == null) {
                pattern = Pattern.compile(patternSource, patternSourceFlags);
            }
            return pattern;
        }
    }
}
