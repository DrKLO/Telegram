package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
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
import java.util.HashMap;

public final class PersianFontApplier {

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
        CharSequence styledText = applyForPaint(editable, paint);
        if (styledText == editable) {
            return;
        }
        // Keep unchanged spans to avoid invalidating the entire input on every keystroke.
        HashMap<Long, PersianFontSpan> previous = new HashMap<>();
        for (PersianFontSpan span : editable.getSpans(0, editable.length(), PersianFontSpan.class)) {
            long key = rangeKey(editable.getSpanStart(span), editable.getSpanEnd(span));
            PersianFontSpan duplicate = previous.put(key, span);
            if (duplicate != null) {
                editable.removeSpan(duplicate);
            }
        }
        if (styledText instanceof Spanned) {
            Spanned styled = (Spanned) styledText;
            for (PersianFontSpan span : styled.getSpans(0, styled.length(), PersianFontSpan.class)) {
                int start = styled.getSpanStart(span);
                int end = styled.getSpanEnd(span);
                long key = rangeKey(start, end);
                PersianFontSpan existing = previous.get(key);
                if (existing != null && existing.matches(span)
                        && editable.getSpanFlags(existing) == styled.getSpanFlags(span)
                        && isLastStyleSpan(editable, existing)) {
                    previous.remove(key);
                } else {
                    editable.setSpan(span, start, end, styled.getSpanFlags(span));
                }
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
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        for (PersianFontSpan span : result.getSpans(0, result.length(), PersianFontSpan.class)) {
            result.removeSpan(span);
        }
        for (int[] range : ranges) {
            int start = range[0];
            while (start < range[1]) {
                int end = result.nextSpanTransition(start, range[1], CharacterStyle.class);
                applyStyledRange(result, start, end, weight, italic);
                start = end;
            }
        }
        // A second layout pass must not replace equivalent spans.
        if (text instanceof Spanned && sameFontSpans((Spanned) text, result)) {
            return text;
        }
        return result;
    }

    private static boolean sameFontSpans(Spanned original, Spanned styled) {
        PersianFontSpan[] oldSpans = original.getSpans(0, original.length(), PersianFontSpan.class);
        PersianFontSpan[] newSpans = styled.getSpans(0, styled.length(), PersianFontSpan.class);
        if (oldSpans.length != newSpans.length) {
            return false;
        }
        for (int i = 0; i < oldSpans.length; i++) {
            if (!oldSpans[i].matches(newSpans[i])
                    || original.getSpanStart(oldSpans[i]) != styled.getSpanStart(newSpans[i])
                    || original.getSpanEnd(oldSpans[i]) != styled.getSpanEnd(newSpans[i])
                    || original.getSpanFlags(oldSpans[i]) != styled.getSpanFlags(newSpans[i])
                    || !isLastStyleSpan(original, oldSpans[i])) {
                return false;
            }
        }
        return true;
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

    private static void applyStyledRange(SpannableStringBuilder text, int start, int end,
                                        int weight, boolean italic) {
        boolean keepOriginalFont = false;
        for (CharacterStyle span : text.getSpans(start, end, CharacterStyle.class)) {
            if (text.getSpanStart(span) == text.getSpanEnd(span)) {
                continue;
            }
            CharacterStyle underlying = span.getUnderlying();
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
        if (!keepOriginalFont) {
            Typeface typeface = weight >= 600 ? PersianFontManager.bold()
                    : weight >= 500 ? PersianFontManager.medium() : PersianFontManager.regular();
            text.setSpan(new PersianFontSpan(typeface, italic), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
