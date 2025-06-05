package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
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
import android.util.LongSparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.TreeSet;

public class QuoteSpan implements LeadingMarginSpan {

    public static int COLLAPSE_LINES = 3;

    public final boolean edit;
    public boolean adaptLineHeight = true;
    public int start, end;
    public boolean isCollapsing;
    public boolean singleLine, first, last;
    public boolean rtl;

    public final QuoteStyleSpan styleSpan;
    public QuoteCollapsedPart collapsedSpan;

    private final Drawable quoteDrawable;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] backgroundPathRadii = new float[8];
    private final Path backgroundPath = new Path();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] linePathRadii = new float[8];
    private final Path linePath = new Path();

    private int color = Color.WHITE;

    public QuoteSpan(boolean edit, boolean collapsing, QuoteStyleSpan helperSpan) {
        this.edit = edit;
        this.styleSpan = helperSpan;
        this.isCollapsing = collapsing;

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
        return dp(adaptLineHeight ? 8 : 10);
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
            tp.setTextSize(dp(span.edit ? 16 : SharedConfig.fontSize - 2));
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint tp) {
            tp.setTextSize(dp(span.edit ? 16 : SharedConfig.fontSize - 2));
            tp.setTextScaleX(span.edit ? 1.1f : 1f);
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
            if (span.adaptLineHeight) {
                final int dp = span.singleLine ? 7 : 2;
                if (start <= span.start) {
                    fm.ascent -= dp((span.last ? 2 : 0) + dp);
                    fm.top -= dp((span.last ? 2 : 0) + dp);
                }
                if (end >= span.end) {
                    fm.descent += dp(dp);
                    fm.bottom += dp(dp);
                }
            }
        }
    }

    public static int putQuote(Spannable spannable, int start, int end, boolean collapse) {
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
        final QuoteSpan quoteSpan = styleSpan.span = new QuoteSpan(false, collapse, styleSpan);
        quoteSpan.start = start;
        quoteSpan.end = end;
        spannable.setSpan(styleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return end;
    }

    public static int putQuoteToEditable(Editable editable, int start, int end, boolean collapsed) {
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
        final QuoteSpan quoteSpan = styleSpan.span = new QuoteSpan(true, collapsed, styleSpan);
        quoteSpan.start = start;
        quoteSpan.end = end;
        editable.setSpan(quoteSpan, Utilities.clamp(start, editable.length(), 0), Utilities.clamp(end, editable.length(), 0), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(styleSpan, Utilities.clamp(start, editable.length(), 0), Utilities.clamp(end, editable.length(), 0), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        editable.insert(Utilities.clamp(end, editable.length(), 0), "\uFEFF");
        editable.delete(Utilities.clamp(end, editable.length(), 0), Utilities.clamp(end + 1, editable.length(), 0));

        return selectEnd;
    }

    public static ArrayList<Block> updateQuoteBlocks(View view, Layout layout, ArrayList<Block> blocks) {
        return updateQuoteBlocks(view, layout, blocks, null);
    }

    public static ArrayList<Block> updateQuoteBlocks(View view, Layout layout, ArrayList<Block> blocks, boolean[] updateLayout) {
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
            Block block = new Block(view, layout, spannable, spans[i]);
            if (block.span.edit) {
                if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n')) {
                    spannable.removeSpan(spans[i]);
                    spannable.removeSpan(spans[i].styleSpan);
                    if (spans[i].collapsedSpan != null) {
                        spannable.removeSpan(spans[i].collapsedSpan);
                    }
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
                    block = new Block(view, layout, spannable, spans[i]);
                }

                if (spannable instanceof SpannableStringBuilder) {
                    SpannableStringBuilder ssb = (SpannableStringBuilder) spannable;
                    final boolean hasPad = block.span.end - 1 >= 0 && ssb.charAt(block.span.end - 1) == '\n';
                    final boolean needsPad = block.hasButton() && block.span.end - 2 >= 0 && layout.getLineRight(layout.getLineForOffset(block.span.end - 1)) - dp(12) > block.width - block.buttonWidth();
                    if (hasPad != needsPad) {
                        int newEnd = block.span.end;
                        if (hasPad) {
                            newEnd -= 1;
                            ssb.delete(block.span.end - 1, block.span.end);
                        } else {
                            newEnd += 2;
                            boolean selectingThis = Selection.getSelectionStart(ssb) == block.span.end && Selection.getSelectionStart(ssb) == Selection.getSelectionEnd(ssb);
                            ssb.insert(block.span.end, block.span.getNewlineHack());
                            if (selectingThis && Selection.getSelectionStart(ssb) != block.span.end) {
                                Selection.setSelection(ssb, block.span.end, block.span.end);
                            }
                        }
                        block.span.end = Math.min(newEnd, spannable.length());
                        spannable.removeSpan(spans[i]);
                        spannable.removeSpan(spans[i].styleSpan);
                        spannable.setSpan(spans[i], block.span.start, block.span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        spannable.setSpan(spans[i].styleSpan, block.span.start, block.span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (updateLayout != null) {
                            updateLayout[0] = true;
                        }
                    }
                }

                if (block.span.collapsedSpan != null) {
                    spannable.removeSpan(block.span.collapsedSpan);
                }
                if (block.span.isCollapsing) {
                    int startLine = layout.getLineForOffset(block.span.start);
                    int collapseStartLine = Math.min(startLine + COLLAPSE_LINES, layout.getLineCount());
                    int collapseStart = layout.getLineStart(collapseStartLine);
                    int collapseEnd = block.span.end;
                    if (collapseStart < collapseEnd) {
                        if (block.span.collapsedSpan == null) {
                            block.span.collapsedSpan = new QuoteCollapsedPart(block.span);
                        }
                        spannable.setSpan(block.span.collapsedSpan, collapseStart, collapseEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
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
            Block block = new Block(null, layout, spanned, spans[i]);
            if (blocks == null) {
                blocks = new ArrayList<>();
            }
            blocks.add(block);
        }
        return blocks;
    }

    private AnimatedFloat expandScale;
    private AnimatedTextView.AnimatedTextDrawable expandText;
    private int expandTextWidth;
    private boolean expandTextCollapsed;
    private ExpandDrawable expandDrawable;
    private int expandDrawableColor;
    private ButtonBounce expandBounce;
    private boolean expandPressed;

    public static class Block {

        public final View view;
        public final int top, bottom, width;
        public final @NonNull QuoteSpan span;
        public final TextPaint paint;

        public RectF collapseButtonBounds;

        public Block(View view, Layout layout, Spanned spanned, @NonNull QuoteSpan span) {
            this.view = view;
            this.span = span;
            this.paint = layout.getPaint();

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
                this.top = layout.getLineTop(lineStart) + dp(3 - (span.singleLine ? 0 : 3 + (span.first ? 2 : 0)));
                this.bottom = layout.getLineBottom(lineEnd) - dp(2 - (span.singleLine ? 0 : 3 + (span.last ? 2 : 0)));
            } else {
                this.top = layout.getLineTop(lineStart) + dp(3 - (span.singleLine ? 1 : 2));
                this.bottom = layout.getLineBottom(lineEnd) - dp(2 - (span.singleLine ? 1 : 2));
            }

            float width = 0;
            span.rtl = false;
            for (int line = lineStart; line <= lineEnd; ++line) {
                width = Math.max(width, layout.getLineRight(line));
                if (layout.getLineLeft(line) > 0)
                    span.rtl = true;
            }
            this.width = (int) Math.ceil(width);

            if (span.edit && view != null) {
                if (span.expandScale == null) {
                    span.expandScale = new AnimatedFloat(view, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
                }
                if (span.expandDrawable == null) {
                    span.expandDrawable = new ExpandDrawable(view);
                }
                if (span.expandText == null) {
                    span.expandText = new AnimatedTextView.AnimatedTextDrawable();
                    span.expandText.setTextSize(dp(11));
                    span.expandText.setHacks(true, true, true);
                    span.expandText.setCallback(view);
                    span.expandText.setOverrideFullWidth((int) (AndroidUtilities.displaySize.x * .3f));
                    span.expandText.setText(getString((span.expandTextCollapsed = false) ? R.string.QuoteExpand : R.string.QuoteCollapse), false);
                    span.expandTextWidth = (int) Math.ceil(Math.max(
                        span.expandText.getPaint().measureText(getString(R.string.QuoteExpand)),
                        span.expandText.getPaint().measureText(getString(R.string.QuoteCollapse))
                    ));
                }
                if (span.expandBounce == null) {
                    span.expandBounce = new ButtonBounce(view);
                }
            }
        }

        public void draw(Canvas canvas, float offsetY, int maxWidth, int color, float alpha, TextPaint paint) {
            span.setColor(color);

            int width = span.edit ? maxWidth : (this.width + dp(32));
            if (width >= maxWidth * 0.95) {
                width = maxWidth;
            }

            canvas.save();
            canvas.translate(0, offsetY);

            AndroidUtilities.rectTmp.set(0, top, width, bottom);
            span.backgroundPathRadii[0] = span.backgroundPathRadii[1] = span.backgroundPathRadii[6] = span.backgroundPathRadii[7] = 0; // left
            span.backgroundPathRadii[2] = span.backgroundPathRadii[3] = span.backgroundPathRadii[4] = span.backgroundPathRadii[5] = dp(4); // right
            span.backgroundPath.rewind();
            span.backgroundPath.addRoundRect(AndroidUtilities.rectTmp, span.backgroundPathRadii, Path.Direction.CW);
            canvas.drawPath(span.backgroundPath, span.backgroundPaint);

            if (span.edit && view != null) {
                if (span.isCollapsing != span.expandTextCollapsed) {
                    span.expandText.setText(getString((span.expandTextCollapsed = span.isCollapsing) ? R.string.QuoteExpand : R.string.QuoteCollapse), true);
                }
                final int buttonWidth = (int) (dp(6 + 11.66f + 6) + span.expandText.getCurrentWidth());
                final int buttonHeight = dp(17.66f);
                final int pad = dp(3.333f);
                if (collapseButtonBounds == null) {
                    collapseButtonBounds = new RectF();
                }
                collapseButtonBounds.set(width - pad - buttonWidth, bottom - pad - buttonHeight, width - pad, bottom - pad);
                final float s = span.expandScale.set(hasButton()) * span.expandBounce.getScale(0.02f);
                if (s > 0) {
                    canvas.save();
                    canvas.scale(s, s, width - pad, bottom - pad);
                    canvas.drawRoundRect(collapseButtonBounds, buttonHeight / 2f, buttonHeight / 2f, span.backgroundPaint);
                    span.expandText.setBounds((int) (collapseButtonBounds.left + dp(6)), (int) collapseButtonBounds.top, (int) (collapseButtonBounds.right - dp(17.66f)), (int) collapseButtonBounds.bottom);
                    span.expandText.setTextColor(color);
                    span.expandText.draw(canvas);
                    final int sz = dp(14);
                    span.expandDrawable.setBounds((int) (collapseButtonBounds.right - dp(3.33f) - sz), (int) (collapseButtonBounds.centerY() - sz / 2f + dp(.33f)), (int) (collapseButtonBounds.right - dp(3.33f)), (int) (collapseButtonBounds.centerY() + sz / 2f + dp(.33f)));
                    span.expandDrawable.setColor(color);
                    span.expandDrawable.setState(!span.isCollapsing);
                    span.expandDrawable.draw(canvas);
                    canvas.restore();
                }
            }

            AndroidUtilities.rectTmp.set(-dp(3), top, 0, bottom);
            span.linePathRadii[0] = span.linePathRadii[1] = span.linePathRadii[6] = span.linePathRadii[7] = dp(4); // left
            span.linePathRadii[2] = span.linePathRadii[3] = span.linePathRadii[4] = span.linePathRadii[5] = 0; // right
            span.linePath.rewind();
            span.linePath.addRoundRect(AndroidUtilities.rectTmp, span.linePathRadii, Path.Direction.CW);
            canvas.drawPath(span.linePath, span.linePaint);

            if (!span.rtl) {
                int quoteTop = (int) ((top + bottom - span.quoteDrawable.getIntrinsicHeight()) / 2f);
                if (quoteTop > top + dp(8)) {
                    quoteTop = top + dp(4);
                }
                span.quoteDrawable.setBounds(
                    width - span.quoteDrawable.getIntrinsicWidth() - dp(4),
                    quoteTop,
                    width - dp(4),
                    quoteTop + span.quoteDrawable.getIntrinsicHeight()
                );
                span.quoteDrawable.setAlpha((int) (0xFF * alpha));
                span.quoteDrawable.draw(canvas);
            }

            canvas.restore();
        }

        public int buttonWidth() {
            return (int) (dp(6 + 11.66f + 6) + span.expandTextWidth + 2 * dp(3.333f));
        }

        public boolean hasButton() {
            return span.edit && (bottom - top) > paint.getTextSize() * 1.3f * COLLAPSE_LINES;
        }
    }

    public static boolean onTouch(MotionEvent ev, int scrollY, ArrayList<Block> blocks, Runnable updateQuotes) {
        if (blocks == null) return false;
        boolean hasPressed = false;
        for (Block block : blocks) {
            final boolean hit = block.hasButton() && block.collapseButtonBounds.contains(ev.getX(), ev.getY() - scrollY);
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                block.span.expandPressed = hit;
                if (block.span.expandBounce != null) {
                    block.span.expandBounce.setPressed(block.span.expandPressed);
                }
            } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                if (block.span.expandPressed && hit) {
                    hasPressed = true;
                    block.span.isCollapsing = !block.span.isCollapsing;
                    if (updateQuotes != null) {
                        updateQuotes.run();
                    }
                }
                block.span.expandPressed = false;
                if (block.span.expandBounce != null) {
                    block.span.expandBounce.setPressed(block.span.expandPressed);
                }
            } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
                block.span.expandPressed = false;
                if (block.span.expandBounce != null) {
                    block.span.expandBounce.setPressed(block.span.expandPressed);
                }
            }
            hasPressed = block.span.expandPressed || hasPressed;
        }
        return hasPressed;
    }


    public static void mergeQuotes(SpannableStringBuilder text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || !(text instanceof Spanned)) {
            return;
        }

        final int QUOTE_START = 1;
        final int QUOTE_END = 2;
        final int CODE_START = 4;
        final int CODE_END = 8;
        final int QUOTE_START_COLLAPSE = 16;

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
                cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | (entity.collapsed ? QUOTE_START_COLLAPSE : QUOTE_START));
                cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);
            }
        }

        int from = 0;
        boolean quoteCollapse = false;
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
                    QuoteSpan.putQuoteToEditable(text, from, to, quoteCollapse);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0 || (type & QUOTE_START_COLLAPSE) != 0) {
                quoteCount++;
                quoteCollapse = (type & QUOTE_START_COLLAPSE) != 0;
            }
            if ((type & CODE_END) != 0) codeCount--;
            if ((type & CODE_START) != 0) codeCount++;
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuoteToEditable(text, from, text.length(), quoteCollapse);
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
        final int QUOTE_START_COLLAPSE = 16;

        final TreeSet<Integer> cutIndexes = new TreeSet<>();
        final HashMap<Integer, Integer> cutToType = new HashMap<>();

        QuoteSpan.QuoteStyleSpan[] quoteSpans = text.getSpans(0, text.length(), QuoteSpan.QuoteStyleSpan.class);
        for (int i = 0; i < quoteSpans.length; ++i) {
            QuoteSpan.QuoteStyleSpan span = quoteSpans[i];

            final int start = text.getSpanStart(span);
            final int end = text.getSpanEnd(span);

            cutIndexes.add(start);
            cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | (span.span.isCollapsing ? QUOTE_START_COLLAPSE : QUOTE_START));
            cutIndexes.add(end);
            cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

            text.removeSpan(span);
            text.removeSpan(span.span);
        }

        int from = 0;
        boolean quoteCollapse = false;
        int quoteCount = 0, codeCount = 0;
        for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
            int cutIndex = i.next();
            final int type = cutToType.get(cutIndex);

            if (from != cutIndex) {
                int to = cutIndex;
                if (cutIndex - 1 >= 0 && cutIndex - 1 < text.length() && text.charAt(cutIndex - 1) == '\n') {
                    to--;
                }

                if (quoteCount > 0) {
                    QuoteSpan.putQuoteToEditable(text, from, to, quoteCollapse);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0 || (type & QUOTE_START_COLLAPSE) != 0) {
                quoteCount++;
                quoteCollapse = (type & QUOTE_START_COLLAPSE) != 0;
            }
            if ((type & CODE_END) != 0) codeCount--;
            if ((type & CODE_START) != 0) codeCount++;
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuoteToEditable(text, from, text.length(), quoteCollapse);
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
        final int QUOTE_START_COLLAPSE = 16;

        final TreeSet<Integer> cutIndexes = new TreeSet<>();
        final HashMap<Integer, Integer> cutToType = new HashMap<>();

        QuoteSpan.QuoteStyleSpan[] quoteSpans = text.getSpans(0, text.length(), QuoteSpan.QuoteStyleSpan.class);
        for (int i = 0; i < quoteSpans.length; ++i) {
            QuoteSpan.QuoteStyleSpan span = quoteSpans[i];

            final int start = text.getSpanStart(span);
            final int end = text.getSpanEnd(span);

            cutIndexes.add(start);
            cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | (span.span.isCollapsing ? QUOTE_START_COLLAPSE : QUOTE_START));
            cutIndexes.add(end);
            cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

            text.removeSpan(span);
            text.removeSpan(span.span);
        }

        int from = 0;
        boolean quoteCollapse = false;
        int quoteCount = 0;
        for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
            int cutIndex = i.next();
            final int type = cutToType.get(cutIndex);

            if (
                (type & QUOTE_END) != 0 && (type & (QUOTE_START | QUOTE_START_COLLAPSE)) != 0 ||
                quoteCount > 0 && (type & (QUOTE_START | QUOTE_START_COLLAPSE)) != 0
            ) {
                continue;
            }

            if (from != cutIndex) {
                int to = cutIndex;
                if (cutIndex - 1 >= 0 && cutIndex - 1 < text.length() && text.charAt(cutIndex - 1) == '\n') {
                    to--;
                }

                if (quoteCount > 0) {
                    QuoteSpan.putQuote(text, from, to, quoteCollapse);
                }
                from = cutIndex;
                if (from + 1 < text.length() && text.charAt(from) == '\n') {
                    from++;
                }
            }

            if ((type & QUOTE_END) != 0) quoteCount--;
            if ((type & QUOTE_START) != 0 || (type & QUOTE_START_COLLAPSE) != 0) {
                quoteCount++;
                quoteCollapse = (type & QUOTE_START_COLLAPSE) != 0;
            }
        }
        if (from < text.length()) {
            if (quoteCount > 0) {
                QuoteSpan.putQuote(text, from, text.length(), quoteCollapse);
            }
        }
    }


    private SpannableString newline;
    public SpannableString getNewlineHack() {
        if (newline == null) {
            newline = new SpannableString("\n");
            newline.setSpan(new QuoteButtonNewLineSpan(), 0, newline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return newline;
    }
    public boolean isMyNewlineHack(CharSequence cs) {
        if (!(cs instanceof Spanned))
            return false;
        return ((Spanned) cs).getSpans(0, cs.length(), QuoteButtonNewLineSpan.class).length > 0;
    }

    public static CharSequence stripNewlineHacks(CharSequence cs) {
        if (cs == null) return null;
        if (!(cs instanceof Spanned)) return cs;
        final SpannableStringBuilder ssb = new SpannableStringBuilder(cs);
        final QuoteButtonNewLineSpan[] spans = ssb.getSpans(0, ssb.length(), QuoteButtonNewLineSpan.class);
        for (int i = spans.length - 1; i >= 0; i--) {
            final QuoteButtonNewLineSpan span = spans[i];
            final int start = ssb.getSpanStart(span);
            final int end = ssb.getSpanEnd(span);
            ssb.removeSpan(span);
            ssb.delete(start, end);
        }
        return ssb;
    }

    public static class QuoteButtonNewLineSpan extends CharacterStyle {

        @Override
        public void updateDrawState(TextPaint tp) {
            // NOP
        }
    }

    public static class QuoteCollapsedPart extends CharacterStyle {

        private final QuoteSpan span;

        public QuoteCollapsedPart(QuoteSpan span) {
            this.span = span;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(Theme.blendOver(Theme.multAlpha(tp.getColor(), 0.55f), Theme.multAlpha(span.color, 0.40f)));
        }
    }

    public static class ExpandDrawable extends Drawable {
        private final View view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int alpha = 0xFF;
        private boolean state;
        private final AnimatedFloat animatedState;
        public ExpandDrawable(View view) {
            this.view = view;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(1));
            animatedState = new AnimatedFloat(view, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

            final float aw = dpf2(4.66f);
            final float ah = dpf2(2.16f);
            path.rewind();
            path.moveTo(aw / 2f, 0);
            path.lineTo(-aw / 2f, 0);
            path.lineTo(-aw / 2f + ah, -ah);
            path.moveTo(-aw / 2f, 0);
            path.lineTo(-aw / 2f + ah, ah);
        }

        public void setColor(int color) {
            paint.setColor(color);
            paint.setAlpha(this.alpha);
        }

        public void setState(boolean expand) {
            if (this.state != expand) {
                this.state = expand;
                view.invalidate();
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int cx = getBounds().centerX();
            final int cy = getBounds().centerY();
            final float expand = animatedState.set(state);
            final float p = dpf2(2.51f);

            canvas.save();
            canvas.translate(cx, cy);

            canvas.save();
            canvas.translate(p, p);
            canvas.rotate(45f);
            canvas.scale(lerp(-1f, 1f, expand), 1f);
            canvas.drawPath(path, paint);
            canvas.restore();

            canvas.save();
            canvas.translate(-p, -p);
            canvas.rotate(45f + 180);
            canvas.scale(lerp(-1f, 1f, expand), 1f);
            canvas.drawPath(path, paint);
            canvas.restore();

            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(this.alpha = alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}
