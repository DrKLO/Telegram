package org.telegram.utils.code.highlight;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TextStyleSpan;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Drop-in adapter for Telegram Android's CodeHighlighting public API. */
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

        public Span(
                boolean smallerSize,
                int type,
                TextStyleSpan.TextStyleRun style,
                String lng,
                String code) {
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
            switch (PrismaTokenStyles.legacyGroup(this.group)) {
                case MATCH_KEYWORD:
                    return Theme.key_code_keyword;
                case MATCH_OPERATOR:
                    return Theme.key_code_operator;
                case MATCH_CONSTANT:
                    return Theme.key_code_constant;
                case MATCH_STRING:
                    return Theme.key_code_string;
                case MATCH_NUMBER:
                    return Theme.key_code_number;
                case MATCH_COMMENT:
                    return Theme.key_code_comment;
                case MATCH_FUNCTION:
                    return Theme.key_code_function;
                default:
                    return -1;
            }
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            int key = getColorKey();
            if (key >= 0) tp.setColor(Theme.getColor(key));
        }
    }

    // setting 100-300 spans is slow
    // fast way to fix this is to do it on another thread, and not give access to data until it's
    // finished processing
    @SuppressWarnings({"unchecked"})
    public static class LockedSpannableString extends SpannableString {
        public LockedSpannableString(CharSequence text) {
            super(text);
        }

        public volatile boolean ready = false;

        private <T> T[] rawSpans(int start, int end, Class<T> kind) {
            return super.getSpans(start, end, kind);
        }

        private int rawSpanStart(Object span) {
            return super.getSpanStart(span);
        }

        private int rawSpanEnd(Object span) {
            return super.getSpanEnd(span);
        }

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

    public static class LockedWithFallbackSpannableString extends LockedSpannableString {
        public SpannableStringBuilder fallback;

        public LockedWithFallbackSpannableString(
                CharSequence text, SpannableStringBuilder fallback) {
            super(text);
            this.fallback = fallback;
        }

        @Override
        public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
            if (!ready && fallback != null) return fallback.getSpans(queryStart, queryEnd, kind);
            return super.getSpans(queryStart, queryEnd, kind);
        }

        @Override
        public int nextSpanTransition(int start, int limit, Class kind) {
            if (!ready && fallback != null) return fallback.nextSpanTransition(start, limit, kind);
            return super.nextSpanTransition(start, limit, kind);
        }

        @Override
        public int getSpanStart(Object what) {
            if (!ready && fallback != null) return fallback.getSpanStart(what);
            return super.getSpanStart(what);
        }

        @Override
        public int getSpanEnd(Object what) {
            if (!ready && fallback != null) return fallback.getSpanEnd(what);
            return super.getSpanEnd(what);
        }

        @Override
        public int getSpanFlags(Object what) {
            if (!ready && fallback != null) return fallback.getSpanFlags(what);
            return super.getSpanFlags(what);
        }
    }

    private static final class SyntaxFontSpan extends MetricAffectingSpan {
        private static final Typeface[] FONTS = {
            Typeface.MONOSPACE,
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
            Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC),
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)
        };
        private final int style;
        private final int flags;

        SyntaxFontSpan(int flags) {
            this.flags = flags;
            style =
                    ((flags & PrismaTokenStyles.BOLD) != 0 ? 1 : 0)
                            | ((flags & PrismaTokenStyles.ITALIC) != 0 ? 2 : 0);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            updateMeasureState(paint);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            paint.setTypeface(FONTS[style]);
        }
    }

    private static final PrismaHighlightingService SERVICE =
            new PrismaHighlightingService(
                    () -> {
                        byte[] grammars;
                        try (InputStream input =
                                        ApplicationLoader.applicationContext
                                                .getAssets()
                                                .open("grammars.dat");
                                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = input.read(buffer)) != -1)
                                output.write(buffer, 0, length);
                            grammars = output.toByteArray();
                        }
                        // Telegram's NativeLoader loads the main tmessages library before this.
                        final PrismaHighlighter highlighter = new PrismaHighlighter(grammars);
                        return new PrismaHighlightingService.Tokenizer() {
                            @Override
                            public PrismaHighlighter.Tokenization tokenize(
                                    String text, String language) {
                                return highlighter.tokenize(text, language);
                            }

                            @Override
                            public String[] getLanguages() {
                                return highlighter.getLanguages();
                            }

                            @Override
                            public void close() {
                                highlighter.close();
                            }
                        };
                    },
                    FileLog::e);

    private static final class CacheKey {
        final String language;
        final String code;

        CacheKey(String language, String code) {
            this.language = language;
            this.code = code;
        }

        @Override
        public int hashCode() {
            return 31 * language.hashCode() + code.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) object;
            return language.equals(other.language) && code.equals(other.code);
        }
    }

    // Published spannables are shared by MessageObject and must be treated as read-only.
    private static final LinkedHashMap<CacheKey, SpannableString> RENDERED =
            new LinkedHashMap<>(8, 0.75f, true);
    private static int renderedCharacters;
    private static final int RENDERED_CHARACTER_LIMIT = 64 * 1024;

    public static SpannableString getHighlighted(CharSequence text, String language) {
        CharSequence source = text == null ? "" : text;
        String normalized = PrismaHighlightingService.normalizeLanguage(language);
        if (normalized.isEmpty() || source.length() == 0) return new SpannableString(source);
        CacheKey key = new CacheKey(normalized, source.toString());
        LockedSpannableString result;
        boolean cacheable =
                !(source instanceof Spanned) && key.code.length() <= RENDERED_CHARACTER_LIMIT;
        synchronized (RENDERED) {
            if (cacheable) {
                SpannableString cached = RENDERED.get(key);
                if (cached != null) return cached;
            }
            result = new LockedSpannableString(source);
            if (cacheable) {
                RENDERED.put(key, result);
                renderedCharacters += key.code.length();
                while (RENDERED.size() > 8 || renderedCharacters > RENDERED_CHARACTER_LIMIT) {
                    Map.Entry<CacheKey, SpannableString> oldest =
                            RENDERED.entrySet().iterator().next();
                    renderedCharacters -= oldest.getKey().code.length();
                    RENDERED.remove(oldest.getKey());
                }
            }
        }
        highlight(result, 0, result.length(), normalized, 0, null, true);
        return result;
    }

    /**
     * Callback receives a snapshot on the UI thread; editors should check their own text revision.
     */
    public static void highlightEditable(
            CharSequence text, String language, Utilities.Callback<SpannableString> whenDone) {
        if (whenDone == null) return;
        SpannableString result = new SpannableString(text == null ? "" : text);
        String normalized = PrismaHighlightingService.normalizeLanguage(language);
        if (normalized.isEmpty() || result.length() == 0) {
            AndroidUtilities.runOnUIThread(() -> whenDone.run(result));
            return;
        }
        SERVICE.request(
                result.toString(),
                normalized,
                answer ->
                        AndroidUtilities.runOnUIThread(
                                () -> {
                                    apply(result, 0, result.length(), answer.tokens);
                                    whenDone.run(result);
                                }));
    }

    public static void prepare() {
        SERVICE.prepare();
    }

    public static Set<String> getLanguages() {
        return SERVICE.getLanguages();
    }

    private static final Object TARGET_LOCK = new Object();
    // Identity lookup is intentional: SpannableString's content/span hash can change after setSpan.
    private static final ArrayList<TargetRequests> TARGETS = new ArrayList<>();

    private static final class TargetRequests {
        final WeakReference<Spannable> target;
        final HashMap<Long, Pending> ranges = new HashMap<>();

        TargetRequests(Spannable target) {
            this.target = new WeakReference<>(target);
        }
    }

    private static final class Pending {
        final String source;
        PrismaHighlightingService.Request request;

        Pending(String source) {
            this.source = source;
        }
    }

    private static TargetRequests findTarget(Spannable text, boolean create) {
        Iterator<TargetRequests> iterator = TARGETS.iterator();
        while (iterator.hasNext()) {
            TargetRequests requests = iterator.next();
            Spannable target = requests.target.get();
            if (target == null) iterator.remove();
            else if (target == text) return requests;
        }
        if (!create) return null;
        TargetRequests requests = new TargetRequests(text);
        TARGETS.add(requests);
        return requests;
    }

    /** Replaces this range's syntax spans. Superseded requests never overwrite newer results. */
    public static void highlight(
            Spannable text,
            int start,
            int end,
            String language,
            int type,
            TextStyleSpan.TextStyleRun style,
            boolean smallerSize) {
        if (text == null) return;
        if (start < 0 || end < start || end > text.length()) throw new IndexOutOfBoundsException();
        Pending pending = new Pending(text.subSequence(start, end).toString());
        long range = ((long) start << 32) | (end & 0xffffffffL);
        TargetRequests target;
        synchronized (TARGET_LOCK) {
            target = findTarget(text, true);
            Pending previous = target.ranges.put(range, pending);
            if (previous != null && previous.request != null) previous.request.cancel();
        }
        PrismaHighlightingService.Request request =
                SERVICE.request(
                        pending.source,
                        language,
                        answer -> {
                            boolean applied = false;
                            synchronized (TARGET_LOCK) {
                                Spannable current = target.target.get();
                                if (current == null || target.ranges.get(range) != pending) return;
                                // A locked immutable snapshot hides spans until publication.
                                // Prepare it off-main.
                                if (current instanceof LockedSpannableString
                                        && !((LockedSpannableString) current).ready) {
                                    apply(current, start, end, answer.tokens);
                                    applied = true;
                                }
                            }
                            final boolean prepared = applied;
                            AndroidUtilities.runOnUIThread(
                                    () -> {
                                        synchronized (TARGET_LOCK) {
                                            Spannable current = target.target.get();
                                            if (current == null
                                                    || target.ranges.get(range) != pending) return;
                                            boolean matches =
                                                    sameSource(current, start, end, pending.source);
                                            if (matches && !prepared)
                                                apply(current, start, end, answer.tokens);
                                            target.ranges.remove(range);
                                            if (target.ranges.isEmpty()) {
                                                TARGETS.remove(target);
                                                if (current instanceof LockedSpannableString) {
                                                    ((LockedSpannableString) current).unlock();
                                                }
                                            }
                                            if (matches)
                                                NotificationCenter.getGlobalInstance()
                                                        .postNotificationName(
                                                                NotificationCenter.emojiLoaded);
                                        }
                                    });
                        });
        synchronized (TARGET_LOCK) {
            if (target.ranges.get(range) == pending) pending.request = request;
            else request.cancel();
        }
    }

    private static boolean sameSource(CharSequence text, int start, int end, String source) {
        if (end > text.length() || end - start != source.length()) return false;
        for (int i = 0; i < source.length(); ++i) {
            if (text.charAt(start + i) != source.charAt(i)) return false;
        }
        return true;
    }

    private static <T> T[] ownedSpans(Spannable text, int start, int end, Class<T> kind) {
        if (text instanceof LockedSpannableString) {
            return ((LockedSpannableString) text).rawSpans(start, end, kind);
        }
        return text.getSpans(start, end, kind);
    }

    private static void apply(
            Spannable text, int start, int end, PrismaHighlighter.Tokenization tokens) {
        if (start == end) return;
        for (ColorSpan span : ownedSpans(text, start, end, ColorSpan.class)) {
            preserveOutside(text, start, end, span);
            text.removeSpan(span);
        }
        for (SyntaxFontSpan span : ownedSpans(text, start, end, SyntaxFontSpan.class)) {
            preserveOutside(text, start, end, span);
            text.removeSpan(span);
        }
        if (tokens == null) return;
        int[] ranges = tokens.styledRanges();
        for (int i = 0; i < ranges.length; i += 3) {
            int from = start + ranges[i];
            int to = start + ranges[i + 1];
            int flags = ranges[i + 2];
            if (from < start || to > end || from >= to) continue;
            int group = flags & PrismaTokenStyles.COLOR_MASK;
            if (group != 0)
                text.setSpan(new ColorSpan(group), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if ((flags & (PrismaTokenStyles.BOLD | PrismaTokenStyles.ITALIC)) != 0) {
                text.setSpan(new SyntaxFontSpan(flags), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void preserveOutside(Spannable text, int start, int end, Object span) {
        int from, to;
        if (text instanceof LockedSpannableString) {
            from = ((LockedSpannableString) text).rawSpanStart(span);
            to = ((LockedSpannableString) text).rawSpanEnd(span);
        } else {
            from = text.getSpanStart(span);
            to = text.getSpanEnd(span);
        }
        if (from >= 0 && from < start) {
            text.setSpan(
                    copySyntaxSpan(span),
                    from,
                    Math.min(start, to),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (to > end) {
            text.setSpan(
                    copySyntaxSpan(span),
                    Math.max(end, from),
                    to,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static Object copySyntaxSpan(Object span) {
        if (span instanceof ColorSpan) return new ColorSpan(((ColorSpan) span).group);
        return new SyntaxFontSpan(((SyntaxFontSpan) span).flags);
    }
}
