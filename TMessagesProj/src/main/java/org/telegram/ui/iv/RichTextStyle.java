package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;

import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.FormattedDateSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;

public class RichTextStyle {

    public static final int BOLD = TextStyleSpan.FLAG_STYLE_BOLD;
    public static final int ITALIC = TextStyleSpan.FLAG_STYLE_ITALIC;
    public static final int UNDERLINE = TextStyleSpan.FLAG_STYLE_UNDERLINE;
    public static final int STRIKE = TextStyleSpan.FLAG_STYLE_STRIKE;
    public static final int MONO = TextStyleSpan.FLAG_STYLE_MONO;
    public static final int SPOILER = TextStyleSpan.FLAG_STYLE_SPOILER;
    public static final int SUBSCRIPT = TextStyleSpan.FLAG_STYLE_SUBSCRIPT;
    public static final int SUPERSCRIPT = TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
    public static final int MARKED = TextStyleSpan.FLAG_STYLE_MARKED;

    public static final int SUPPORTED = BOLD | ITALIC | UNDERLINE | STRIKE | MONO | SPOILER | SUBSCRIPT | SUPERSCRIPT | MARKED;

    // ---------- model -> Spannable ----------

    public static CharSequence toSpannable(TL_iv.RichText rt) {
        return toSpannable(rt, null);
    }
    public static CharSequence toSpannable(TL_iv.RichText rt, TL_iv.PageBlock block) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, rt, 0, block);
        return sb;
    }

    private static void append(SpannableStringBuilder sb, TL_iv.RichText rt, int flags, TL_iv.PageBlock block) {
        if (rt == null || rt instanceof TL_iv.textEmpty) {
            return;
        }
        if (rt instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                append(sb, child, flags, block);
            }
            return;
        }
        if (rt instanceof TL_iv.textPlain) {
            appendLeaf(sb, ((TL_iv.textPlain) rt).text, flags, block);
            return;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            TL_iv.textCustomEmoji emoji = (TL_iv.textCustomEmoji) rt;
            String alt = emoji.alt == null || emoji.alt.isEmpty() ? "😀" : emoji.alt;
            int start = sb.length();
            sb.append(alt);
            AnimatedEmojiSpan span = new AnimatedEmojiSpan(emoji.document_id, null);
            span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
            sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (flags != 0) {
                sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textUrl) {
            TL_iv.textUrl url = (TL_iv.textUrl) rt;
            int start = sb.length();
            append(sb, url.text, flags, block);
            if (sb.length() > start && url.url != null) {
                sb.setSpan(new URLSpanReplacement(url.url), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textDate) {
            TL_iv.textDate date = (TL_iv.textDate) rt;
            int start = sb.length();
            append(sb, date.text, flags, block);
            if (sb.length() > start) {
                sb.setSpan(dateSpan(date, sb.subSequence(start, sb.length()).toString()), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        if (rt instanceof TL_iv.textMath) {
            TL_iv.textMath math = (TL_iv.textMath) rt;
            int start = sb.length();
            sb.append(" ");
            MathSpan span = MathSpan.create(math.source, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), dp(4 + SharedConfig.fontSize));
            if (span != null) {
                sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.replace(start, sb.length(), math.source == null ? "" : math.source);
            }
            if (sb.length() > start && flags != 0) {
                sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return;
        }
        int childFlag = flagOf(rt);
        if (childFlag != 0) {
            append(sb, rt.text, flags | childFlag, block);
            return;
        }
        appendLeaf(sb, plainOf(rt), flags, block);
    }

    private static void appendLeaf(SpannableStringBuilder sb, String text, int flags, TL_iv.PageBlock block) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int start = sb.length();
        sb.append(text);
        if (flags != 0) {
            sb.setSpan(spanFor(flags, block), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int flagOf(TL_iv.RichText rt) {
        if (rt instanceof TL_iv.textBold) return BOLD;
        if (rt instanceof TL_iv.textItalic) return ITALIC;
        if (rt instanceof TL_iv.textUnderline) return UNDERLINE;
        if (rt instanceof TL_iv.textStrike) return STRIKE;
        if (rt instanceof TL_iv.textFixed) return MONO;
        if (rt instanceof TL_iv.textSpoiler) return SPOILER;
        if (rt instanceof TL_iv.textSubscript) return SUBSCRIPT;
        if (rt instanceof TL_iv.textSuperscript) return SUPERSCRIPT;
        if (rt instanceof TL_iv.textMarked) return MARKED;
        return 0;
    }

    public static String plainOf(TL_iv.RichText rt) {
        if (rt == null || rt instanceof TL_iv.textEmpty) {
            return "";
        }
        if (rt instanceof TL_iv.textPlain) {
            String t = ((TL_iv.textPlain) rt).text;
            return t == null ? "" : t;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            String t = ((TL_iv.textCustomEmoji) rt).alt;
            return t == null ? "" : t;
        }
        if (rt instanceof TL_iv.textMath) {
            return " ";
        }
        if (rt instanceof TL_iv.textConcat) {
            StringBuilder s = new StringBuilder();
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                s.append(plainOf(child));
            }
            return s.toString();
        }
        return plainOf(rt.text);
    }

    private static FormattedDateSpan dateSpan(TL_iv.textDate date, String text) {
        TLRPC.TL_messageEntityFormattedDate entity = new TLRPC.TL_messageEntityFormattedDate();
        entity.flags = date.flags;
        entity.date = date.date;
        entity.applyFlags();
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_URL;
        return new FormattedDateSpan(text, run, entity);
    }

    // ---------- Spannable -> model ----------

    public static TL_iv.RichText fromSpannable(CharSequence cs) {
        int n = cs == null ? 0 : cs.length();
        if (n == 0) {
            return new TL_iv.textEmpty();
        }
        if (!(cs instanceof Spanned)) {
            return plainNode(cs.toString());
        }
        Spanned sp = (Spanned) cs;

        ArrayList<TL_iv.RichText> parts = new ArrayList<>();
        int runStart = 0;
        Run run = null;
        for (int pos = 0; pos < n; ) {
            int next = sp.nextSpanTransition(pos, n, CharacterStyle.class);
            Run here = runAt(sp, pos, next);
            if (run == null) {
                run = here;
            } else if (!run.equals(here)) {
                parts.add(wrap(cs.subSequence(runStart, pos).toString(), run));
                runStart = pos;
                run = here;
            }
            pos = next;
        }
        parts.add(wrap(cs.subSequence(runStart, n).toString(), run == null ? new Run() : run));

        if (parts.size() == 1) {
            return parts.get(0);
        }
        TL_iv.textConcat concat = new TL_iv.textConcat();
        concat.texts = parts;
        return concat;
    }

    private static TL_iv.RichText wrap(String text, Run run) {
        if (run.mathSource != null) {
            TL_iv.textMath math = new TL_iv.textMath();
            math.source = run.mathSource;
            return math;
        }
        TL_iv.RichText cur = run.emojiDocId != 0 ? customEmojiNode(run.emojiDocId, text) : plainNode(text);
        int flags = run.flags;
        if ((flags & BOLD) != 0) cur = wrapOne(new TL_iv.textBold(), cur);
        if ((flags & ITALIC) != 0) cur = wrapOne(new TL_iv.textItalic(), cur);
        if ((flags & UNDERLINE) != 0) cur = wrapOne(new TL_iv.textUnderline(), cur);
        if ((flags & STRIKE) != 0) cur = wrapOne(new TL_iv.textStrike(), cur);
        if ((flags & MONO) != 0) cur = wrapOne(new TL_iv.textFixed(), cur);
        if ((flags & SPOILER) != 0) cur = wrapOne(new TL_iv.textSpoiler(), cur);
        if ((flags & SUBSCRIPT) != 0) cur = wrapOne(new TL_iv.textSubscript(), cur);
        if ((flags & SUPERSCRIPT) != 0) cur = wrapOne(new TL_iv.textSuperscript(), cur);
        if ((flags & MARKED) != 0) cur = wrapOne(new TL_iv.textMarked(), cur);
        if (run.url != null) {
            TL_iv.textUrl url = new TL_iv.textUrl();
            url.text = cur;
            url.url = run.url;
            cur = url;
        }
        if (run.date != null) {
            cur = dateNode(run.date, cur);
        }
        return cur;
    }

    private static TL_iv.RichText dateNode(FormattedDateSpan span, TL_iv.RichText inner) {
        TLRPC.TL_messageEntityFormattedDate entity = span.entity;
        TL_iv.textDate date = new TL_iv.textDate();
        date.text = inner;
        date.flags = entity.flags;
        date.relative = entity.relative;
        date.short_time = entity.short_time;
        date.long_time = entity.long_time;
        date.short_date = entity.short_date;
        date.long_date = entity.long_date;
        date.day_of_week = entity.day_of_week;
        date.date = entity.date;
        return date;
    }

    private static TL_iv.textPlain plainNode(String text) {
        TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text;
        return plain;
    }

    private static TL_iv.textCustomEmoji customEmojiNode(long documentId, String alt) {
        TL_iv.textCustomEmoji emoji = new TL_iv.textCustomEmoji();
        emoji.document_id = documentId;
        emoji.alt = alt == null ? "" : alt;
        return emoji;
    }

    private static TL_iv.RichText wrapOne(TL_iv.RichText wrapper, TL_iv.RichText inner) {
        wrapper.text = inner;
        return wrapper;
    }

    // ---------- range queries / toggling ----------

    /** True if every character in [from, to) carries {@code flag}. Empty ranges are not covered. */
    public static boolean hasStyle(CharSequence cs, int from, int to, int flag) {
        int n = cs == null ? 0 : cs.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to || !(cs instanceof Spanned)) {
            return false;
        }
        Spanned sp = (Spanned) cs;
        for (int pos = from; pos < to; ) {
            int next = sp.nextSpanTransition(pos, to, TextStyleSpan.class);
            if ((flagsBetween(sp, pos, next) & flag) == 0) {
                return false;
            }
            pos = next;
        }
        return true;
    }

    /** True if every character in [from, to) is covered by a link. Empty ranges are not covered. */
    public static boolean hasLink(CharSequence cs, int from, int to) {
        int n = cs == null ? 0 : cs.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to || !(cs instanceof Spanned)) {
            return false;
        }
        Spanned sp = (Spanned) cs;
        for (int pos = from; pos < to; ) {
            int next = sp.nextSpanTransition(pos, to, URLSpanReplacement.class);
            if (sp.getSpans(pos, next, URLSpanReplacement.class).length == 0) {
                return false;
            }
            pos = next;
        }
        return true;
    }

    /** True if every character in [from, to) is covered by a date. Empty ranges are not covered. */
    public static boolean hasDate(CharSequence cs, int from, int to) {
        int n = cs == null ? 0 : cs.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to || !(cs instanceof Spanned)) {
            return false;
        }
        Spanned sp = (Spanned) cs;
        for (int pos = from; pos < to; ) {
            int next = sp.nextSpanTransition(pos, to, FormattedDateSpan.class);
            if (sp.getSpans(pos, next, FormattedDateSpan.class).length == 0) {
                return false;
            }
            pos = next;
        }
        return true;
    }

    private static final int[] STYLE_FLAGS = { BOLD, ITALIC, UNDERLINE, STRIKE, MONO, SPOILER, SUBSCRIPT, SUPERSCRIPT, MARKED };

    /** Combined inline style flags that fully cover [from, to). A flag is reported only when
     *  TextStyleSpans carrying it span the whole range with no gap. Links/dates are ignored —
     *  inline styles live in their own TextStyleSpan layer, independent of URL/date spans. */
    public static int stylesFullyCovering(CharSequence cs, int from, int to) {
        int result = 0;
        for (int flag : STYLE_FLAGS) {
            if (hasStyle(cs, from, to, flag)) result |= flag;
        }
        return result;
    }

    /** Adds or removes {@code flag} over [from, to), preserving the other inline styles. */
    public static void setStyle(Spannable sb, int from, int to, int flag, boolean add) {
        setStyle(sb, from, to, flag, add, null);
    }
    public static void setStyle(Spannable sb, int from, int to, int flag, boolean add, TL_iv.PageBlock block) {
        int n = sb.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to) {
            return;
        }
        for (TextStyleSpan span : sb.getSpans(from, to, TextStyleSpan.class)) {
            int s = sb.getSpanStart(span);
            int e = sb.getSpanEnd(span);
            int flags = span.getStyleFlags();
            sb.removeSpan(span);
            applyRun(sb, s, from, flags, block);
            applyRun(sb, to, e, flags, block);
            applyRun(sb, Math.max(s, from), Math.min(e, to), add ? (flags | flag) : (flags & ~flag), block);
        }
        if (add) {
            for (int pos = from; pos < to; ) {
                int next = sb.nextSpanTransition(pos, to, TextStyleSpan.class);
                if (flagsBetween(sb, pos, next) == 0) {
                    applyRun(sb, pos, next, flag, block);
                }
                pos = next;
            }
        }
    }

    /** Removes any link span over [from, to], keeping the surrounding parts intact. */
    public static void removeLink(android.text.Spannable sb, int from, int to) {
        int n = sb.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to) {
            return;
        }
        for (URLSpanReplacement span : sb.getSpans(from, to, URLSpanReplacement.class)) {
            int s = sb.getSpanStart(span);
            int e = sb.getSpanEnd(span);
            sb.removeSpan(span);
            if (s < from) sb.setSpan(new URLSpanReplacement(span.getURL()), s, from, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (e > to) sb.setSpan(new URLSpanReplacement(span.getURL()), to, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Removes any date span overlapping [from, to]. Dates wrap a single rendered token, so any
     *  overlapping date is dropped whole rather than split. */
    public static void removeDate(android.text.Spannable sb, int from, int to) {
        int n = sb.length();
        from = Math.max(0, Math.min(from, n));
        to = Math.max(0, Math.min(to, n));
        if (from >= to) {
            return;
        }
        for (FormattedDateSpan span : sb.getSpans(from, to, FormattedDateSpan.class)) {
            sb.removeSpan(span);
        }
    }

    private static void applyRun(Spannable sb, int start, int end, int flags, TL_iv.PageBlock block) {
        if (start >= end || flags == 0) {
            return;
        }
        sb.setSpan(spanFor(flags, block), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int flagsBetween(Spanned sp, int start, int end) {
        int flags = 0;
        for (TextStyleSpan span : sp.getSpans(start, end, TextStyleSpan.class)) {
            int f = span.getStyleFlags();
            if ((f & TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED) != 0) {
                f |= SPOILER;
            }
            flags |= f;
        }
        return flags & SUPPORTED;
    }

    private static Run runAt(Spanned sp, int start, int end) {
        Run run = new Run();
        run.flags = flagsBetween(sp, start, end);
        // inline `code` parsed from markdown is represented as URLSpanMono, not a TextStyleSpan
        if (sp.getSpans(start, end, URLSpanMono.class).length > 0) run.flags |= MONO;
        URLSpanReplacement[] urls = sp.getSpans(start, end, URLSpanReplacement.class);
        if (urls.length > 0) run.url = urls[0].getURL();
        FormattedDateSpan[] dates = sp.getSpans(start, end, FormattedDateSpan.class);
        if (dates.length > 0) run.date = dates[0];
        AnimatedEmojiSpan[] emoji = sp.getSpans(start, end, AnimatedEmojiSpan.class);
        if (emoji.length > 0) run.emojiDocId = emoji[0].getDocumentId();
        MathSpan[] math = sp.getSpans(start, end, MathSpan.class);
        if (math.length > 0) run.mathSource = math[0].source;
        return run;
    }

    private static TextStyleSpan spanFor(int flags, TL_iv.PageBlock block) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flags;
        run.header = (
            block instanceof TL_iv.pageBlockTitle ||
            block instanceof TL_iv.pageBlockSubheader ||
            block instanceof TL_iv.pageBlockHeader ||
            block instanceof TL_iv.pageBlockHeading1 ||
            block instanceof TL_iv.pageBlockHeading2 ||
            block instanceof TL_iv.pageBlockHeading3 ||
            block instanceof TL_iv.pageBlockHeading4 ||
            block instanceof TL_iv.pageBlockHeading5 ||
            block instanceof TL_iv.pageBlockHeading6
        );
        return new TextStyleSpan(run);
    }

    private static class Run {
        int flags;
        String url;
        FormattedDateSpan date;
        long emojiDocId;
        String mathSource;

        boolean equals(Run o) {
            if (emojiDocId != 0 || o.emojiDocId != 0) {
                return false;
            }
            // each math token is its own atomic run, never merged with neighbours
            if (mathSource != null || o.mathSource != null) {
                return false;
            }
            return flags == o.flags
                && (url == null ? o.url == null : url.equals(o.url))
                && date == o.date;
        }
    }
}
