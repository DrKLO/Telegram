package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.NoCopySpan;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PersianFontManager;
import org.telegram.messenger.PersianTextUtils;
import org.telegram.messenger.SharedConfig;
import org.telegram.utils.code.highlight.CodeHighlighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class PersianFontApplier {

    private static final int STYLE_ITALIC = 4;

    private PersianFontApplier() {
    }

    public static CharSequence applyRegular(CharSequence text) {
        return apply(text, 400, false);
    }

    public static CharSequence applyMedium(CharSequence text) {
        return apply(text, 500, false);
    }

    public static CharSequence applyBold(CharSequence text) {
        return apply(text, 700, false);
    }

    public static CharSequence applyForPaint(CharSequence text, TextPaint paint) {
        if (text == null || text.length() == 0 || !SharedConfig.useVazirmatnFont) {
            return removeFontSpans(text);
        }
        Typeface original = paint.getTypeface();
        if (isMonospace(original)) {
            return removeFontSpans(text);
        }
        int weight = paint.isFakeBoldText() ? 700 : getWeight(original);
        return apply(text, weight, paint.getTextSkewX() != 0 || isItalic(original));
    }

    public static void applyToEditable(Editable editable, TextPaint paint) {
        if (editable == null) {
            return;
        }
        Typeface original = paint.getTypeface();
        if (!SharedConfig.useVazirmatnFont || isMonospace(original)) {
            // Disabling does not need a temporary copy of the entire input.
            for (PersianFontSpan span : editable.getSpans(0, editable.length(), PersianFontSpan.class)) {
                editable.removeSpan(span);
            }
            return;
        }
        int weight = paint.isFakeBoldText() ? 700 : getWeight(original);
        ArrayList<int[]> ranges = PersianTextUtils.findPersianOrArabicRanges(editable);
        if (ranges.isEmpty()) {
            for (PersianFontSpan span : editable.getSpans(0, editable.length(), PersianFontSpan.class)) {
                editable.removeSpan(span);
            }
            return;
        }
        FontPlan plan = createPlan(editable, ranges, weight, paint.getTextSkewX() != 0 || isItalic(original));
        if (plan.matches()) {
            return;
        }
        // Keep unchanged spans to avoid invalidating the entire input on every keystroke.
        HashMap<Long, PersianFontSpan> previous = new HashMap<>();
        for (int i = 0; i < plan.existing.size(); i++) {
            PersianFontSpan span = plan.existing.get(i);
            long key = rangeKey(editable.getSpanStart(span), editable.getSpanEnd(span));
            PersianFontSpan duplicate = previous.put(key, span);
            if (duplicate != null) {
                editable.removeSpan(duplicate);
            }
        }
        for (int offset = 0; offset < plan.runCount * 3; offset += 3) {
            int start = plan.runs[offset];
            int end = plan.runs[offset + 1];
            int style = plan.runs[offset + 2];
            long key = rangeKey(start, end);
            PersianFontSpan existing = previous.get(key);
            if (existing != null && existing.matches(plan.typefaceForStyle(style), isItalicStyle(style))
                    && editable.getSpanFlags(existing) == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    && isLastStyleSpan(editable, existing)) {
                previous.remove(key);
            } else {
                editable.setSpan(new PersianFontSpan(plan.typefaceForStyle(style), isItalicStyle(style)),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        for (PersianFontSpan span : previous.values()) {
            editable.removeSpan(span);
        }
    }

    private static long rangeKey(int start, int end) {
        return ((long) start << 32) | (end & 0xffffffffL);
    }

    private static CharSequence removeFontSpans(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        PersianFontSpan[] spans = ((Spanned) text).getSpans(0, text.length(), PersianFontSpan.class);
        if (spans.length == 0) {
            return text;
        }
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        for (PersianFontSpan span : spans) {
            result.removeSpan(span);
        }
        return result;
    }

    private static CharSequence apply(CharSequence text, int weight, boolean italic) {
        if (text == null || !SharedConfig.useVazirmatnFont) {
            return removeFontSpans(text);
        }
        ArrayList<int[]> ranges = PersianTextUtils.findPersianOrArabicRanges(text);
        if (ranges.isEmpty()) {
            return removeFontSpans(text);
        }
        if (!(text instanceof Spanned)) {
            // Plain text has no formatting or previous font spans to reconcile.
            SpannableStringBuilder result = new SpannableStringBuilder(text);
            Typeface typeface = weight >= 600 ? PersianFontManager.bold()
                    : weight >= 500 ? PersianFontManager.medium() : PersianFontManager.regular();
            for (int[] range : ranges) {
                result.setSpan(new PersianFontSpan(typeface, italic), range[0], range[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return result;
        }
        FontPlan plan = createPlan(text, ranges, weight, italic);
        // Compare the desired ranges before copying text or allocating font spans.
        if (plan.matches()) {
            return text;
        }
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        for (int i = 0; i < plan.existing.size(); i++) {
            result.removeSpan(plan.existing.get(i));
        }
        for (int offset = 0; offset < plan.runCount * 3; offset += 3) {
            int style = plan.runs[offset + 2];
            result.setSpan(new PersianFontSpan(plan.typefaceForStyle(style), isItalicStyle(style)),
                    plan.runs[offset], plan.runs[offset + 1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    private static FontPlan createPlan(CharSequence text, ArrayList<int[]> ranges, int weight, boolean italic) {
        FontPlan plan = new FontPlan(text, ranges.size());
        for (int[] range : ranges) {
            int start = range[0];
            while (start < range[1]) {
                int end = range[1];
                for (int i = 0; i < plan.formatting.size(); i++) {
                    FormattingSpan span = plan.formatting.get(i);
                    if (span.span instanceof NoCopySpan) {
                        continue;
                    }
                    if (span.start > start && span.start < end) end = span.start;
                    if (span.end > start && span.end < end) end = span.end;
                }
                int style = resolveStyle(plan.formatting, start, end, weight, italic);
                if (style != 0) {
                    plan.addRun(start, end, style);
                }
                start = end;
            }
        }
        return plan;
    }

    private static final class FormattingSpan {
        final CharacterStyle span;
        final int start, end, fontsBefore;

        FormattingSpan(CharacterStyle span, Spanned text, int fontsBefore) {
            this.span = span;
            start = text.getSpanStart(span);
            end = text.getSpanEnd(span);
            this.fontsBefore = fontsBefore;
        }

        boolean overlaps(int runStart, int runEnd) {
            return start < runEnd && end > runStart && start != end;
        }
    }

    private static final class FontPlan {
        final Spanned source;
        // Compact triples avoid one object per word on every measurement pass.
        int[] runs;
        int runCount;
        final ArrayList<PersianFontSpan> existing = new ArrayList<>();
        final ArrayList<FormattingSpan> formatting = new ArrayList<>();
        private final Typeface[] typefaces = new Typeface[4];

        FontPlan(CharSequence text, int rangeCount) {
            runs = new int[Math.max(3, rangeCount * 3)];
            source = text instanceof Spanned ? (Spanned) text : null;
            if (source != null) {
                // One ordered snapshot also records which styles follow each font.
                for (CharacterStyle span : source.getSpans(0, source.length(), CharacterStyle.class)) {
                    if (span instanceof PersianFontSpan) {
                        existing.add((PersianFontSpan) span);
                    } else {
                        formatting.add(new FormattingSpan(span, source, existing.size()));
                    }
                }
            }
        }

        boolean matches() {
            if (existing.size() != runCount) {
                return false;
            }
            for (int i = 0; i < runCount; i++) {
                int offset = i * 3;
                int start = runs[offset];
                int end = runs[offset + 1];
                int style = runs[offset + 2];
                PersianFontSpan span = existing.get(i);
                if (source.getSpanStart(span) != start || source.getSpanEnd(span) != end
                        || source.getSpanFlags(span) != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        || !span.matches(typefaceForStyle(style), isItalicStyle(style))) {
                    return false;
                }
                for (int j = 0; j < formatting.size(); j++) {
                    FormattingSpan formattingSpan = formatting.get(j);
                    if (formattingSpan.fontsBefore > i && formattingSpan.overlaps(start, end)) {
                        return false;
                    }
                }
            }
            return true;
        }

        void addRun(int start, int end, int style) {
            int offset = runCount * 3;
            if (offset + 3 > runs.length) {
                runs = Arrays.copyOf(runs, Math.max(offset + 3, runs.length * 2));
            }
            runs[offset] = start;
            runs[offset + 1] = end;
            runs[offset + 2] = style;
            runCount++;
        }

        Typeface typefaceForStyle(int style) {
            int weight = style & ~STYLE_ITALIC;
            if (typefaces[weight] == null) {
                typefaces[weight] = weight == 3 ? PersianFontManager.bold()
                        : weight == 2 ? PersianFontManager.medium() : PersianFontManager.regular();
            }
            return typefaces[weight];
        }
    }

    private static boolean isLastStyleSpan(Spanned text, PersianFontSpan fontSpan) {
        boolean found = false;
        for (CharacterStyle span : text.getSpans(text.getSpanStart(fontSpan),
                text.getSpanEnd(fontSpan), CharacterStyle.class)) {
            if (span == fontSpan) {
                found = true;
            } else if (found && !(span instanceof PersianFontSpan)
                    && text.getSpanStart(span) != text.getSpanEnd(span)) {
                return false;
            }
        }
        return found;
    }

    private static int resolveStyle(ArrayList<FormattingSpan> formatting, int start, int end,
                                    int weight, boolean italic) {
        boolean keepOriginalFont = false;
        for (int i = 0; i < formatting.size(); i++) {
            FormattingSpan span = formatting.get(i);
            if (!span.overlaps(start, end) || span.span instanceof NoCopySpan) {
                continue;
            }
            CharacterStyle underlying = span.span.getUnderlying();
            if (underlying instanceof URLSpanMono || underlying instanceof CodeHighlighting.Span
                    || underlying instanceof ReplacementSpan && !(underlying instanceof EmptyStubSpan)) {
                keepOriginalFont = true;
                continue;
            }
            TextStyleSpan.TextStyleRun run = getTextStyleRun(underlying);
            if (run != null) {
                // Match Telegram's actual typeface: rich bold uses its Medium asset.
                Typeface runTypeface = run.getTypeface();
                if (runTypeface != null) {
                    weight = getWeight(runTypeface);
                }
                italic |= (run.flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0;
                keepOriginalFont |= (run.flags & (TextStyleSpan.FLAG_STYLE_MONO
                        | TextStyleSpan.FLAG_STYLE_CODE)) != 0;
            }
            if (underlying instanceof URLSpanNoUnderlineBold) {
                weight = getWeight(AndroidUtilities.bold());
            }
            Typeface explicitTypeface = null;
            if (underlying instanceof TypefaceSpan) {
                explicitTypeface = ((TypefaceSpan) underlying).getTypeface();
            } else if (underlying instanceof TextPaintSpan) {
                TextPaint spanPaint = new TextPaint();
                ((TextPaintSpan) underlying).updateMeasureState(spanPaint);
                explicitTypeface = spanPaint.getTypeface();
            } else if (underlying instanceof android.text.style.TypefaceSpan) {
                android.text.style.TypefaceSpan fontSpan = (android.text.style.TypefaceSpan) underlying;
                keepOriginalFont |= "monospace".equals(fontSpan.getFamily());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    explicitTypeface = fontSpan.getTypeface();
                }
            }
            if (explicitTypeface != null) {
                keepOriginalFont |= isMonospace(explicitTypeface);
                weight = getWeight(explicitTypeface);
                italic = isItalic(explicitTypeface);
            }
            if (underlying instanceof StyleSpan) {
                int style = ((StyleSpan) underlying).getStyle();
                if ((style & Typeface.BOLD) != 0) {
                    weight = 700;
                }
                italic |= (style & Typeface.ITALIC) != 0;
            }
        }
        return keepOriginalFont ? 0 : (weight >= 600 ? 3 : weight >= 500 ? 2 : 1)
                | (italic ? STYLE_ITALIC : 0);
    }

    private static boolean isItalicStyle(int style) {
        return (style & STYLE_ITALIC) != 0;
    }

    private static boolean isMonospace(Typeface typeface) {
        return typeface != null && (typeface.equals(Typeface.create(Typeface.MONOSPACE, typeface.getStyle()))
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && typeface.equals(Typeface.create(Typeface.MONOSPACE, typeface.getWeight(), typeface.isItalic()))
                || typeface == AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)
                || typeface == AndroidUtilities.getTypeface("fonts/rmono_var.ttf"));
    }

    private static boolean isItalic(Typeface typeface) {
        return typeface != null && (typeface.isItalic()
                || typeface == AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC));
    }

    private static int getWeight(Typeface typeface) {
        if (typeface == null) {
            return 400;
        }
        // Telegram marks its Medium assets as weight 700 in the Android font cache.
        if (typeface == AndroidUtilities.bold()
                || typeface == AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
                || typeface == AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC)) {
            return 500;
        }
        int weight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? typeface.getWeight() : 400;
        return typeface.isBold() ? Math.max(700, weight) : weight;
    }

    private static TextStyleSpan.TextStyleRun getTextStyleRun(CharacterStyle span) {
        if (span instanceof TextStyleSpan) {
            return ((TextStyleSpan) span).getTextStyleRun();
        }
        if (span instanceof URLSpanNoUnderline) {
            return ((URLSpanNoUnderline) span).getTextStyleRun();
        }
        if (span instanceof URLSpanReplacement) {
            return ((URLSpanReplacement) span).getTextStyleRun();
        }
        if (span instanceof URLSpanBrowser) {
            return ((URLSpanBrowser) span).getStyle();
        }
        if (span instanceof FormattedDateSpan) {
            return ((FormattedDateSpan) span).style;
        }
        return null;
    }
}
