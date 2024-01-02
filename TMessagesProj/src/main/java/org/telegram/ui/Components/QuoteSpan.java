package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ParagraphStyle;
import android.text.style.ReplacementSpan;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

public class QuoteSpan implements LeadingMarginSpan {

    public final boolean edit;
    public boolean adaptLineHeight = true;
    public int start, end;
    public boolean singleLine, first, last;
    public boolean rtl;

    public final QuoteStyleSpan styleSpan;

    private final Drawable quoteDrawable;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] backgroundPathRadii = new float[8];
    private final Path backgroundPath = new Path();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] linePathRadii = new float[8];
    private final Path linePath = new Path();

    private int color = Color.WHITE;

    public QuoteSpan(boolean edit, QuoteStyleSpan helperSpan) {
        this.edit = edit;
        this.styleSpan = helperSpan;

        quoteDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.mini_quote).mutate();
        linePaint.setColor(color);
        backgroundPaint.setColor(ColorUtils.setAlphaComponent(color, 0x1e));
    }

    public void setColor(int color) {
        if (this.color != color) {
            quoteDrawable.setColorFilter(new PorterDuffColorFilter(this.color = color, PorterDuff.Mode.SRC_IN));
            linePaint.setColor(color);
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(color, 0x1e));
        }
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return AndroidUtilities.dp(adaptLineHeight ? 8 : 10);
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {

    }

    public static class QuoteStyleSpan extends MetricAffectingSpan implements LineHeightSpan {

        @NonNull
        public QuoteSpan span;

        @Override
        public void updateDrawState(TextPaint tp) {
            if (tp == null) {
                return;
            }
            tp.setTextSize(AndroidUtilities.dp(span.edit ? 16 : SharedConfig.fontSize - 2));
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint tp) {
            tp.setTextSize(AndroidUtilities.dp(span.edit ? 16 : SharedConfig.fontSize - 2));
            tp.setTextScaleX(span.edit ? 1.1f : 1f);
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
            if (span.adaptLineHeight) {
                final int dp = span.singleLine ? 7 : 2;
                if (start <= span.start) {
                    fm.ascent -= AndroidUtilities.dp((span.last ? 2 : 0) + dp);
                    fm.top -= AndroidUtilities.dp((span.last ? 2 : 0) + dp);
                }
                if (end >= span.end) {
                    fm.descent += AndroidUtilities.dp(dp);
                    fm.bottom += AndroidUtilities.dp(dp);
                }
            }
        }
    }

    public static int putQuote(Spannable spannable, int start, int end) {
        if (spannable == null) {
            return -1;
        }
        QuoteSpan[] existingSpans = spannable.getSpans(start, end, QuoteSpan.class);
        if (existingSpans != null && existingSpans.length > 0) {
            return -1;
        }
        start = Utilities.clamp(start, spannable.length(), 0);
        end = Utilities.clamp(end, spannable.length(), 0);
        final QuoteStyleSpan styleSpan = new QuoteStyleSpan();
        final QuoteSpan quoteSpan = styleSpan.span = new QuoteSpan(false, styleSpan);
        quoteSpan.start = start;
        quoteSpan.end = end;
        spannable.setSpan(styleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return end;
    }

    public static int putQuoteToEditable(Editable editable, int start, int end) {
        if (editable == null) {
            return -1;
        }
        start = Utilities.clamp(start, editable.length(), 0);
        end = Utilities.clamp(end, editable.length(), 0);
        if (start > 0 && editable.charAt(start - 1) != '\n') {
            editable.insert(start, "\n");
            start++;
            end++;
        }
        int selectEnd = end + 1;
        if (end >= editable.length() || editable.charAt(end) != '\n') {
            editable.insert(end, "\n");
        }
        final QuoteStyleSpan styleSpan = new QuoteStyleSpan();
        final QuoteSpan quoteSpan = styleSpan.span = new QuoteSpan(true, styleSpan);
        quoteSpan.start = start;
        quoteSpan.end = end;
        editable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(styleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        editable.insert(end, "\uFEFF");
        editable.delete(end, end + 1);

        return selectEnd;
    }

    public static ArrayList<Block> updateQuoteBlocks(Layout layout, ArrayList<Block> blocks) {
        return updateQuoteBlocks(layout, blocks, null);
    }

    public static ArrayList<Block> updateQuoteBlocks(Layout layout, ArrayList<Block> blocks, boolean[] updateLayout) {
        if (layout == null) {
            if (blocks != null) {
                blocks.clear();
            }
            return blocks;
        }
        CharSequence text = layout.getText();
        if (text == null || !(text instanceof Spannable)) {
            if (blocks != null) {
                blocks.clear();
            }
            return blocks;
        }
        Spannable spannable = (Spannable) text;
        if (blocks != null) {
            blocks.clear();
        }
        QuoteSpan[] spans = spannable.getSpans(0, spannable.length(), QuoteSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            boolean wasLast = spans[i].last;
            Block block = new Block(layout, spannable, spans[i]);
            if (block.span.edit) {
                if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n')) {
                    spannable.removeSpan(spans[i]);
                    spannable.removeSpan(spans[i].styleSpan);
                    continue;
                }
                if (!(block.span.end == text.length() || text.charAt(block.span.end) == '\n')) {
                    // new line was removed after a quote, finding a new quote end
                    int newEnd = block.span.end;
                    for (; newEnd <= text.length() && !(newEnd == text.length() || text.charAt(newEnd) == '\n'); ++newEnd);
                    spannable.removeSpan(spans[i]);
                    spannable.removeSpan(spans[i].styleSpan);
                    spannable.setSpan(spans[i], block.span.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(spans[i].styleSpan, block.span.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    block = new Block(layout, spannable, spans[i]);
                }
            }
            if (blocks == null) {
                blocks = new ArrayList<>();
            }
            if (spans[i].last != wasLast && updateLayout != null) {
                updateLayout[0] = true;
            }
            blocks.add(block);
        }
        return blocks;
    }

    public static ArrayList<Block> updateQuoteBlocksSpanned(Layout layout, ArrayList<Block> blocks) {
        if (layout == null) {
            if (blocks != null) {
                blocks.clear();
            }
            return blocks;
        }
        CharSequence text = layout.getText();
        if (text == null || !(text instanceof Spanned)) {
            if (blocks != null) {
                blocks.clear();
            }
            return blocks;
        }
        Spanned spanned = (Spanned) text;
        if (blocks != null) {
            blocks.clear();
        }
        QuoteSpan[] spans = spanned.getSpans(0, spanned.length(), QuoteSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            boolean wasLast = spans[i].last;
            Block block = new Block(layout, spanned, spans[i]);
            if (blocks == null) {
                blocks = new ArrayList<>();
            }
            blocks.add(block);
        }
        return blocks;
    }

    public static class Block {
        public final int top, bottom, width;
        public final @NonNull QuoteSpan span;

        public Block(Layout layout, Spanned spanned, @NonNull QuoteSpan span) {
            this.span = span;
            span.start = spanned.getSpanStart(span);
            span.end = spanned.getSpanEnd(span);
            if (span.end - 1 >= 0 && span.end < spanned.length() && spanned.charAt(span.end) != '\n' && spanned.charAt(span.end - 1) == '\n') {
                span.end--;
            }
            final int lineStart = layout.getLineForOffset(span.start);
            final int lineEnd = layout.getLineForOffset(span.end);
            span.singleLine = lineEnd - lineStart < 1;
            span.first = lineStart <= 0;
            span.last = lineEnd + 1 >= layout.getLineCount();

            if (span.edit) {
                this.top = layout.getLineTop(lineStart) + AndroidUtilities.dp(3 - (span.singleLine ? 0 : 3 + (span.first ? 2 : 0)));
                this.bottom = layout.getLineBottom(lineEnd) - AndroidUtilities.dp(2 - (span.singleLine ? 0 : 3 + (span.last ? 2 : 0)));
            } else {
                this.top = layout.getLineTop(lineStart) + AndroidUtilities.dp(3 - (span.singleLine ? 1 : 2));
                this.bottom = layout.getLineBottom(lineEnd) - AndroidUtilities.dp(2 - (span.singleLine ? 1 : 2));
            }

            float width = 0;
            span.rtl = false;
            for (int line = lineStart; line <= lineEnd; ++line) {
                width = Math.max(width, layout.getLineRight(line));
                if (layout.getLineLeft(line) > 0)
                    span.rtl = true;
            }
            this.width = (int) Math.ceil(width);
        }

        public void draw(Canvas canvas, float offsetY, int maxWidth, int color, float alpha) {
            span.setColor(color);

            int width = span.edit ? maxWidth : (this.width + AndroidUtilities.dp(32));
            if (width >= maxWidth * 0.95) {
                width = maxWidth;
            }

            canvas.save();
            canvas.translate(0, offsetY);

            AndroidUtilities.rectTmp.set(0, top, width, bottom);
            span.backgroundPathRadii[0] = span.backgroundPathRadii[1] = span.backgroundPathRadii[6] = span.backgroundPathRadii[7] = 0; // left
            span.backgroundPathRadii[2] = span.backgroundPathRadii[3] = span.backgroundPathRadii[4] = span.backgroundPathRadii[5] = AndroidUtilities.dp(4); // right
            span.backgroundPath.rewind();
            span.backgroundPath.addRoundRect(AndroidUtilities.rectTmp, span.backgroundPathRadii, Path.Direction.CW);
            canvas.drawPath(span.backgroundPath, span.backgroundPaint);

            AndroidUtilities.rectTmp.set(-AndroidUtilities.dp(3), top, 0, bottom);
            span.linePathRadii[0] = span.linePathRadii[1] = span.linePathRadii[6] = span.linePathRadii[7] = AndroidUtilities.dp(4); // left
            span.linePathRadii[2] = span.linePathRadii[3] = span.linePathRadii[4] = span.linePathRadii[5] = 0; // right
            span.linePath.rewind();
            span.linePath.addRoundRect(AndroidUtilities.rectTmp, span.linePathRadii, Path.Direction.CW);
            canvas.drawPath(span.linePath, span.linePaint);

            if (!span.rtl) {
                int quoteTop = (int) ((top + bottom - span.quoteDrawable.getIntrinsicHeight()) / 2f);
                if (quoteTop > top + AndroidUtilities.dp(8)) {
                    quoteTop = top + AndroidUtilities.dp(4);
                }
                span.quoteDrawable.setBounds(
                    width - span.quoteDrawable.getIntrinsicWidth() - AndroidUtilities.dp(4),
                    quoteTop,
                    width - AndroidUtilities.dp(4),
                    quoteTop + span.quoteDrawable.getIntrinsicHeight()
                );
                span.quoteDrawable.setAlpha((int) (0xFF * alpha));
                span.quoteDrawable.draw(canvas);
            }

            canvas.restore();
        }
    }


    public static void mergeQuotes(SpannableStringBuilder text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || !(text instanceof Spanned)) {
            return;
        }

        final int QUOTE_START = 1;
        final int QUOTE_END = 2;
        final int CODE_START = 4;
        final int CODE_END = 8;

        final TreeSet<Integer> cutIndexes = new TreeSet<>();
        final HashMap<Integer, Integer> cutToType = new HashMap<>();

        for (int i = 0; i < entities.size(); ++i) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (entity.offset + entity.length > text.length()) {
                continue;
            }
            final int start = entity.offset;
            final int end = entity.offset + entity.length;

            if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                cutIndexes.add(start);
                cutIndexes.add(end);
                cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | QUOTE_START);
                cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);
            }
        }

        int from = 0;
        int quoteCount = 0, codeCount = 0;
        for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
            int cutIndex = i.next();
            final int type = cutToType.get(cutIndex);

            if (from != cutIndex) {
                int to = cutIndex;
                if (cutIndex - 1 >= 0 && cutIndex - 1 < text.length() && text.charAt(cutIndex - 1) == '\n') {
                    to--;
                }

                final boolean isQuote = quoteCount > 0;
                if (isQuote) {
                    QuoteSpan.putQuoteToEditable(text, from, to);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0) quoteCount++;
            if ((type & CODE_END) != 0) codeCount--;
            if ((type & CODE_START) != 0) codeCount++;
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuoteToEditable(text, from, text.length());
            }
        }
    }

    public static void normalizeQuotes(Editable text) {
        if (text == null) {
            return;
        }

        final int QUOTE_START = 1;
        final int QUOTE_END = 2;
        final int CODE_START = 4;
        final int CODE_END = 8;

        final TreeSet<Integer> cutIndexes = new TreeSet<>();
        final HashMap<Integer, Integer> cutToType = new HashMap<>();

        QuoteSpan.QuoteStyleSpan[] quoteSpans = text.getSpans(0, text.length(), QuoteSpan.QuoteStyleSpan.class);
        for (int i = 0; i < quoteSpans.length; ++i) {
            QuoteSpan.QuoteStyleSpan span = quoteSpans[i];

            final int start = text.getSpanStart(span);
            final int end = text.getSpanEnd(span);

            cutIndexes.add(start);
            cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | QUOTE_START);
            cutIndexes.add(end);
            cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

            text.removeSpan(span);
            text.removeSpan(span.span);
        }

        int from = 0;
        int quoteCount = 0, codeCount = 0;
        for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
            int cutIndex = i.next();
            final int type = cutToType.get(cutIndex);

            if (from != cutIndex) {
                int to = cutIndex;
                if (cutIndex - 1 >= 0 && cutIndex - 1 < text.length() && text.charAt(cutIndex - 1) == '\n') {
                    to--;
                }

                final boolean isQuote = quoteCount > 0;
                if (isQuote) {
                    QuoteSpan.putQuoteToEditable(text, from, to);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0) quoteCount++;
            if ((type & CODE_END) != 0) codeCount--;
            if ((type & CODE_START) != 0) codeCount++;
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuoteToEditable(text, from, text.length());
            }
        }
    }

    public static void normalizeQuotes(Spannable text) {
        if (text == null) {
            return;
        }

        final int QUOTE_START = 1;
        final int QUOTE_END = 2;
        final int CODE_START = 4;
        final int CODE_END = 8;

        final TreeSet<Integer> cutIndexes = new TreeSet<>();
        final HashMap<Integer, Integer> cutToType = new HashMap<>();

        QuoteSpan.QuoteStyleSpan[] quoteSpans = text.getSpans(0, text.length(), QuoteSpan.QuoteStyleSpan.class);
        for (int i = 0; i < quoteSpans.length; ++i) {
            QuoteSpan.QuoteStyleSpan span = quoteSpans[i];

            final int start = text.getSpanStart(span);
            final int end = text.getSpanEnd(span);

            cutIndexes.add(start);
            cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | QUOTE_START);
            cutIndexes.add(end);
            cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

            text.removeSpan(span);
            text.removeSpan(span.span);
        }

        int from = 0;
        int quoteCount = 0;
        for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
            int cutIndex = i.next();
            final int type = cutToType.get(cutIndex);

            if ((type & QUOTE_END) != 0 && (type & QUOTE_START) != 0 || quoteCount > 0 && (type & QUOTE_START) != 0) {
                continue;
            }

            if (from != cutIndex) {
                int to = cutIndex;
                if (cutIndex - 1 >= 0 && cutIndex - 1 < text.length() && text.charAt(cutIndex - 1) == '\n') {
                    to--;
                }

                final boolean isQuote = quoteCount > 0;
                if (isQuote) {
                    QuoteSpan.putQuote(text, from, to);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0) quoteCount++;
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuote(text, from, text.length());
            }
        }
    }
}
