package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AnimatedEmojiSpan extends ReplacementSpan {
    private static boolean lockPositionChanging;

    public long documentId;
    public TLRPC.Document document;
    public String emoji;
    private float scale;
    public float extraScale = 1f;
    public boolean standard;
    public boolean full = false;
    public boolean top = false;
    public boolean invert = false;

    private Paint.FontMetricsInt fontMetrics;
    public float size = AndroidUtilities.dp(20);
    public int cacheType = -1;
    public String documentAbsolutePath;
    protected int measuredSize;

    boolean spanDrawn;
    boolean positionChanged;
    float lastDrawnCx;
    float lastDrawnCy;
    private boolean recordPositions = true;
    public boolean fromEmojiKeyboard;
    private boolean isAdded;
    private boolean isRemoved;
    private Runnable removedAction;
    private boolean animateChanges;
    private ValueAnimator moveAnimator;
    private ValueAnimator scaleAnimator;

    /**
     * To correctly move emoji to a new line, we need to return the final size in {@link #getSize}.
     * However, this approach causes flickering. So fix this using {@link #lockPositionChanging} flag.
     */
    public void setAdded() {
        isAdded = true;
        extraScale = 0f;
    }

    public void setAnimateChanges() {
        this.animateChanges = true;
    }

    public void setRemoved(Runnable action) {
        removedAction = action;
        isRemoved = true;
        extraScale = 1f;
    }

    public float getExtraScale() {
        if (isAdded) {
            lockPositionChanging = true;
            isAdded = false;
            extraScale = 0f;
            if (scaleAnimator != null) {
                scaleAnimator.removeAllListeners();
                scaleAnimator.cancel();
            }
            scaleAnimator = ValueAnimator.ofFloat(extraScale, 1f);
            scaleAnimator.addUpdateListener(animator -> {
                extraScale = (float) animator.getAnimatedValue();
                scale = AndroidUtilities.lerp(.2f, 1f, extraScale);
                lockPositionChanging = false;
            });
            scaleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scaleAnimator = null;
                    lockPositionChanging = false;
                }
            });
            scaleAnimator.setDuration(130);
            scaleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            scaleAnimator.start();
        } else if (isRemoved) {
            isRemoved = false;
            extraScale = 1f;
            if (scaleAnimator != null) {
                scaleAnimator.removeAllListeners();
                scaleAnimator.cancel();
            }
            scaleAnimator = ValueAnimator.ofFloat(extraScale, 0f);
            scaleAnimator.addUpdateListener(animator -> {
                extraScale = (float) animator.getAnimatedValue();
                scale = AndroidUtilities.lerp(0f, 1f, extraScale);
            });
            scaleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scaleAnimator = null;
                    if (removedAction != null) {
                        removedAction.run();
                        removedAction = null;
                    }
                }
            });
            scaleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            scaleAnimator.setDuration(130);
            scaleAnimator.start();
        }
        return extraScale;
    }

    public AnimatedEmojiSpan(@NonNull TLRPC.Document document, Paint.FontMetricsInt fontMetrics) {
        this(document.id, 1.2f, fontMetrics);
        this.document = document;
    }

    public AnimatedEmojiSpan(@NonNull TLRPC.Document document, float scale, Paint.FontMetricsInt fontMetrics) {
        this(document.id, scale, fontMetrics);
        this.document = document;
    }

    public AnimatedEmojiSpan(long documentId, Paint.FontMetricsInt fontMetrics) {
        this(documentId, 1.2f, fontMetrics);
    }

    public AnimatedEmojiSpan(long documentId, float scale, Paint.FontMetricsInt fontMetrics) {
        this.documentId = documentId;
        this.scale = scale;
        this.fontMetrics = fontMetrics;
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) {
                size = AndroidUtilities.dp(20);
            }
        }
    }

    public static void applyFontMetricsForString(CharSequence text, Paint textPaint) {
        if (text instanceof Spannable) {
            AnimatedEmojiSpan[] spans = ((Spannable) text).getSpans(0, text.length(), AnimatedEmojiSpan.class);
            if (spans != null) {
                for (int k = 0; k < spans.length; ++k) {
                    spans[k].applyFontMetrics(textPaint.getFontMetricsInt());
                }
            }
        }
    }

    public long getDocumentId() {
        return document != null ? document.id : documentId;
    }

    public void replaceFontMetrics(Paint.FontMetricsInt newMetrics) {
        this.fontMetrics = newMetrics;
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) {
                size = AndroidUtilities.dp(20);
            }
        }
    }

    public void replaceFontMetrics(Paint.FontMetricsInt newMetrics, int newSize, int cacheType) {
        fontMetrics = newMetrics;
        size = newSize;
        this.cacheType = cacheType;
    }

    public void applyFontMetrics(Paint.FontMetricsInt newMetrics, int cacheType) {
        fontMetrics = newMetrics;
        this.cacheType = cacheType;
    }

    public void applyFontMetrics(Paint.FontMetricsInt newMetrics) {
        fontMetrics = newMetrics;
    }


    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm == null && top) {
            fm = paint.getFontMetricsInt();
        }
        int ascent = fm == null ? 0 : fm.ascent, descent = fm == null ? 0 : fm.descent;
        if (fontMetrics == null) {
            int sz = (int) size;

            int offset = AndroidUtilities.dp(8);
            int w = AndroidUtilities.dp(10);

            if (fm != null) {
                fm.top = (int) ((-w - offset) * scale);
                fm.bottom = (int) ((w - offset) * scale);
                fm.ascent = (int) ((-w - offset) * scale);
                fm.descent = (int) ((w - offset) * scale);
                fm.leading = 0;
            }

            measuredSize = (int) (sz * scale);
        } else {
            measuredSize = (int) (size * scale);

            if (fm != null) {
                if (!full) {
                    fm.ascent = (int) (fontMetrics.ascent);
                    fm.descent = (int) (fontMetrics.descent);

                    fm.top = (int) (fontMetrics.top);
                    fm.bottom = (int) (fontMetrics.bottom);
                } else {
                    float height = Math.abs(fontMetrics.bottom) + Math.abs(fontMetrics.top);

                    fm.ascent = (int) Math.ceil(fontMetrics.top / height * measuredSize);
                    fm.descent = (int) Math.ceil(fontMetrics.bottom / height * measuredSize);

                    fm.top = (int) Math.ceil(fontMetrics.top / height * measuredSize);
                    fm.bottom = (int) Math.ceil(fontMetrics.bottom / height * measuredSize);
                }
            }
        }
        if (fm != null && top) {
            int diff = ((ascent - fm.ascent) + (descent - fm.descent)) / 2;
            fm.ascent += diff;
            fm.descent -= diff;
        }
        return Math.max(0, measuredSize - 1);
    }

    private boolean isAnimating() {
        return moveAnimator != null || scaleAnimator != null;
    }

    private boolean animateChanges(float cx, float cy) {
        if (moveAnimator != null) {
            return true;
        }
        if (!animateChanges) {
            return false;
        }
        animateChanges = false;
        final float fromCx = lastDrawnCx;
        final float fromCy = lastDrawnCy;
        final float toCx = cx;
        final float toCy = cy;
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.addUpdateListener(animator -> {
            float percent = (float) animator.getAnimatedValue();
            lastDrawnCy = AndroidUtilities.lerp(fromCy, toCy, percent);
            lastDrawnCx = AndroidUtilities.lerp(fromCx, toCx, percent);
        });
        moveAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                moveAnimator = null;
            }
        });
        moveAnimator.setDuration(140);
        moveAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        moveAnimator.start();
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (recordPositions) {
            spanDrawn = true;
            float cx = x + measuredSize / 2f;
            float cy = top + (bottom - top) / 2f;
            if ((cy != lastDrawnCy && lastDrawnCy != 0 || cx != lastDrawnCx && lastDrawnCx != 0) && animateChanges(cx, cy)) {
                return;
            }
            if (lockPositionChanging) {
                return;
            }
            if (cx != lastDrawnCx || cy != lastDrawnCy) {
                lastDrawnCx = cx;
                lastDrawnCy = cy;
                positionChanged = true;
            }
        }
    }

    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<SpoilerEffect> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha) {
        drawAnimatedEmojis(canvas, layout, stack, offset, spoilers, boundTop, boundBottom, drawingYOffset, alpha, null);
    }

    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<SpoilerEffect> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha, ColorFilter colorFilter) {
        if (canvas == null || layout == null || stack == null) {
            return;
        }


        boolean needRestore = false;
        if (Emoji.emojiDrawingYOffset != 0 || offset != 0) {
            needRestore = true;
            canvas.save();
            canvas.translate(0, Emoji.emojiDrawingYOffset + AndroidUtilities.dp(20 * offset));
        }

        long time = System.currentTimeMillis();
        for (int k = 0; k < stack.backgroundDrawingArray.size(); k++) {
            SpansChunk chunk = stack.backgroundDrawingArray.get(k);
            if (chunk.layout == layout) {
                chunk.draw(canvas, spoilers, time, boundTop, boundBottom, drawingYOffset, alpha, colorFilter);
                break;
            }
        }

        if (needRestore) {
            canvas.restore();
        }
    }

    private static boolean isInsideSpoiler(Layout layout, int start, int end) {
        if (layout == null || !(layout.getText() instanceof Spanned)) {
            return false;
        }
        start = Math.max(0, start);
        end = Math.min(layout.getText().length() - 1, end);
        TextStyleSpan[] spans = ((Spanned) layout.getText()).getSpans(start, end, TextStyleSpan.class);
        for (int i = 0; spans != null && i < spans.length; ++i) {
            if (spans[i] != null && spans[i].isSpoiler())
                return true;
        }
        return false;
    }

    // ===
    // stack
    // ===

    public static class AnimatedEmojiHolder implements InvalidateHolder {
        private final View view;
        private final boolean invalidateInParent;
        public Layout layout;
        public AnimatedEmojiSpan span;
        public Rect drawableBounds;
        @Nullable
        public AnimatedEmojiDrawable drawable;
        public Drawable thumbDrawable;
        public boolean skipDraw;
        public float drawingYOffset;
        public float alpha;
        public SpansChunk spansChunk;
        public boolean insideSpoiler;
        private int rawIndex;

        private ImageReceiver.BackgroundThreadDrawHolder[] backgroundDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];

        public AnimatedEmojiHolder(View view, boolean invalidateInParent) {
            this.view = view;
            this.invalidateInParent = invalidateInParent;
        }

        public boolean outOfBounds(float boundTop, float boundBottom) {
            return drawableBounds.bottom < boundTop || drawableBounds.top > boundBottom;
        }

        public void prepareForBackgroundDraw(long updateTime, int threadIndex) {
            if (drawable == null) {
                return;
            }
            ImageReceiver imageReceiver = drawable.getImageReceiver();
            drawable.update(updateTime);
            drawable.setBounds(drawableBounds);
            if (imageReceiver != null) {
                if (span != null && span.document == null && drawable.getDocument() != null) {
                    span.document = drawable.getDocument();
                }
                imageReceiver.setAlpha(alpha);
                imageReceiver.setImageCoords(drawableBounds);
                backgroundDrawHolder[threadIndex] = imageReceiver.setDrawInBackgroundThread(backgroundDrawHolder[threadIndex], threadIndex);
                backgroundDrawHolder[threadIndex].overrideAlpha = alpha;
                backgroundDrawHolder[threadIndex].setBounds(drawableBounds);
                backgroundDrawHolder[threadIndex].time = updateTime;
            }
        }

        public void releaseDrawInBackground(int threadIndex) {
            if (backgroundDrawHolder[threadIndex] != null) {
                backgroundDrawHolder[threadIndex].release();
            }
        }

        public void draw(Canvas canvas, long time, float boundTop, float boundBottom, float alpha, ColorFilter colorFilter) {
            if ((boundTop != 0 || boundBottom != 0) && outOfBounds(boundTop, boundBottom)) {
                skipDraw = true;
                return;
            } else {
                skipDraw = false;
            }

            if (drawable == null) {
                if (thumbDrawable != null) {
                    float scale = span.getExtraScale();
                    thumbDrawable.setAlpha((int) (0xFF * alpha * this.alpha));
                    thumbDrawable.setBounds(drawableBounds);
                    if (scale != 1f || span.invert) {
                        canvas.save();
                        canvas.scale(scale * (span.invert ? -1 : 1), scale, drawableBounds.centerX(), drawableBounds.centerY());
                        thumbDrawable.draw(canvas);
                        canvas.restore();
                    } else {
                        thumbDrawable.draw(canvas);
                    }
                }
                return;
            }
            if (drawable.getImageReceiver() != null) {
                drawable.setColorFilter(colorFilter == null ? Theme.chat_animatedEmojiTextColorFilter : colorFilter);
                drawable.setTime(time);
                float scale = span.getExtraScale();
                if (scale != 1f || span.invert) {
                    canvas.save();
                    canvas.scale(scale * (span.invert ? -1 : 1), scale, drawableBounds.centerX(), drawableBounds.centerY());
                    drawable.draw(canvas, drawableBounds, alpha * this.alpha);
                    canvas.restore();
                } else {
                    drawable.draw(canvas, drawableBounds, alpha * this.alpha);
                }
                if (span.isAnimating()) {
                    invalidate();
                }
            }
        }

        public void invalidate() {
            if (view != null) {
                if (invalidateInParent && view.getParent() != null) {
                    ((View) view.getParent()).invalidate();
                } else {
                    view.invalidate();
                }
            }
        }
    }

    public static EmojiGroupedSpans update(int cacheType, View view, EmojiGroupedSpans prev, ArrayList<MessageObject.TextLayoutBlock> blockLayouts) {
        return update(cacheType, view, prev, blockLayouts, false);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, EmojiGroupedSpans prev, ArrayList<MessageObject.TextLayoutBlock> blockLayouts, boolean clone) {
        return update(cacheType, view, false, prev, blockLayouts, clone);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, ArrayList<MessageObject.TextLayoutBlock> blockLayouts) {
        return update(cacheType, view, invalidateParent, prev, blockLayouts, false);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, ArrayList<MessageObject.TextLayoutBlock> blockLayouts, boolean clone) {
        Layout[] layouts = new Layout[blockLayouts == null ? 0 : blockLayouts.size()];
        if (blockLayouts != null) {
            for (int i = 0; i < blockLayouts.size(); ++i) {
                layouts[i] = blockLayouts.get(i).textLayout;
            }
        }
        return update(cacheType, view, invalidateParent, prev, clone, layouts);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, EmojiGroupedSpans prev, Layout... layouts) {
        return update(cacheType, view, false, prev, layouts);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, Layout... layouts) {
        return update(cacheType, view, invalidateParent, prev, false, layouts);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, boolean clone, Layout... layouts) {
        if (layouts == null || layouts.length <= 0) {
            if (prev != null) {
                prev.holders.clear();
                prev.release();
            }
            return null;
        }

        for (int l = 0; l < layouts.length; ++l) {
            Layout textLayout = layouts[l];
            AnimatedEmojiSpan[] spans = null;
            if (textLayout != null && textLayout.getText() instanceof Spanned) {

                Spanned spanned = (Spanned) textLayout.getText();
                spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);

                for (int i = 0; spans != null && i < spans.length; ++i) {
                    AnimatedEmojiSpan span = spans[i];
                    if (span == null) {
                        continue;
                    }
                    if (clone && textLayout.getText() instanceof Spannable) {
                        int start = spanned.getSpanStart(span), end = spanned.getSpanEnd(span);
                        ((Spannable) spanned).removeSpan(span);
                        ((Spannable) spanned).setSpan(span = cloneSpan(span, null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    AnimatedEmojiHolder holder = null;
                    if (prev == null) {
                        prev = new EmojiGroupedSpans();
                    }
                    for (int j = 0; j < prev.holders.size(); ++j) {
                        if (prev.holders.get(j).span == span &&
                            prev.holders.get(j).layout == textLayout) {
                            holder = prev.holders.get(j);
                            break;
                        }
                    }
                    if (holder == null) {
                        holder = new AnimatedEmojiHolder(view, invalidateParent);
                        holder.layout = textLayout;
                        int localCacheType = span.standard ? AnimatedEmojiDrawable.STANDARD_LOTTIE_FRAME : (span.cacheType < 0 ? cacheType : span.cacheType);
                        if (span.documentAbsolutePath != null) {
                            holder.drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.getDocumentId(), span.documentAbsolutePath);
                        } else if (span.document != null) {
                            holder.drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.document);
                        } else if (span.documentId != 0) {
                            holder.drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.documentId, null);
                        }
                        if ((span.cacheType == AnimatedEmojiDrawable.CACHE_TYPE_STANDARD_EMOJI || span.cacheType == AnimatedEmojiDrawable.CACHE_TYPE_ALERT_STANDARD_EMOJI) && !TextUtils.isEmpty(span.emoji)) {
                            if (holder.drawable != null) {
                                holder.drawable.setupEmojiThumb(span.emoji);
                            } else {
                                holder.thumbDrawable = Emoji.getEmojiDrawable(span.emoji);
                            }
                        }
                        holder.insideSpoiler = isInsideSpoiler(textLayout, spanned.getSpanStart(span), spanned.getSpanEnd(span));
                        holder.drawableBounds = new Rect();
                        holder.span = span;
                        prev.add(textLayout, holder);
                    } else {
                        holder.insideSpoiler = isInsideSpoiler(textLayout, spanned.getSpanStart(span), spanned.getSpanEnd(span));
                    }
                }
            }

            if (prev != null) {
                for (int i = 0; i < prev.holders.size(); ++i) {
                    AnimatedEmojiHolder holder = prev.holders.get(i);
                    if (holder.layout == textLayout) {
                        AnimatedEmojiSpan span = prev.holders.get(i).span;
                        boolean found = false;
                        for (int j = 0; spans != null && j < spans.length; ++j) {
                            if (spans[j] == span) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            prev.remove(i);
                            i--;
                        }
                    }
                }
            }
        }

        if (prev != null) {
            for (int i = 0; i < prev.holders.size(); ++i) {
                Layout layout = prev.holders.get(i).layout;
                boolean found = false;
                for (int l = 0; l < layouts.length; ++l) {
                    if (layouts[l] == layout) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    prev.remove(i);
                    i--;
                }
            }
        }

        return prev;
    }

    public static LongSparseArray<AnimatedEmojiDrawable> update(View holder, AnimatedEmojiSpan[] spans, LongSparseArray<AnimatedEmojiDrawable> prev) {
        return update(0, holder, spans, prev);
    }

    public static LongSparseArray<AnimatedEmojiDrawable> update(int cacheType, View holder, AnimatedEmojiSpan[] spans, LongSparseArray<AnimatedEmojiDrawable> prev) {
        if (spans == null) {
            return prev;
        }
        if (prev == null) {
            prev = new LongSparseArray<>();
        }

        // Remove useless emojis
        for (int i = 0; i < prev.size(); ++i) {
            long documentId = prev.keyAt(i);
            AnimatedEmojiDrawable d = prev.get(documentId);
            if (d == null) {
                prev.remove(documentId);
                i--;
            } else {
                boolean found = false;
                if (spans != null) {
                    for (int j = 0; j < spans.length; ++j) {
                        if (spans[j] != null && spans[j].getDocumentId() == documentId) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    d.removeView(holder);
                    prev.remove(documentId);
                    i--;
                }
            }
        }

        // Add new emojis
        for (int i = 0; i < spans.length; ++i) {
            AnimatedEmojiSpan span = spans[i];
            if (span != null) {
                if (prev.get(span.getDocumentId()) == null) {
                    AnimatedEmojiDrawable drawable;
                    int localCacheType = span.standard ? AnimatedEmojiDrawable.STANDARD_LOTTIE_FRAME : (span.cacheType < 0 ? cacheType : span.cacheType);
                    if (span.document != null) {
                        drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.document);
                    } else {
                        drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.documentId);
                    }
                    drawable.addView(holder);
                    prev.put(span.getDocumentId(), drawable);
                }
            }
        }

        return prev;
    }

    public static LongSparseArray<AnimatedEmojiDrawable> update(View holder, ArrayList<AnimatedEmojiSpan> spans, LongSparseArray<AnimatedEmojiDrawable> prev) {
        return update(0, holder, spans, prev);
    }

    public static LongSparseArray<AnimatedEmojiDrawable> update(int cacheType, View holder, ArrayList<AnimatedEmojiSpan> spans, LongSparseArray<AnimatedEmojiDrawable> prev) {
        if (spans == null) {
            return prev;
        }
        if (prev == null) {
            prev = new LongSparseArray<>();
        }

        // Remove useless emojis
        for (int i = 0; i < prev.size(); ++i) {
            long documentId = prev.keyAt(i);
            AnimatedEmojiDrawable d = prev.get(documentId);
            if (d == null) {
                prev.remove(documentId);
                i--;
            } else {
                boolean found = false;
                for (int j = 0; j < spans.size(); ++j) {
                    if (spans.get(j) != null && spans.get(j).getDocumentId() == documentId) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    d.addView(holder);
                    prev.remove(documentId);
                    i--;
                }
            }
        }

        // Add new emojis
        for (int i = 0; i < spans.size(); ++i) {
            AnimatedEmojiSpan span = spans.get(i);
            if (span != null) {
                if (prev.get(span.getDocumentId()) == null) {
                    AnimatedEmojiDrawable drawable;
                    int localCacheType = span.standard ? AnimatedEmojiDrawable.STANDARD_LOTTIE_FRAME : (span.cacheType < 0 ? cacheType : span.cacheType);
                    drawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, localCacheType, span.documentId);
                    drawable.addView(holder);
                    prev.put(span.getDocumentId(), drawable);
                }
            }
        }

        return prev;
    }

    public static void release(View holder, LongSparseArray<AnimatedEmojiDrawable> arr) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); ++i) {
            AnimatedEmojiDrawable d = arr.valueAt(i);
            if (d != null) {
                d.removeView(holder);
            }
        }
        arr.clear();
    }

    public static void release(View holder, EmojiGroupedSpans spans) {
        if (spans == null) {
            return;
        }
        spans.release();
    }


    public static class EmojiGroupedSpans {
        public ArrayList<AnimatedEmojiHolder> holders = new ArrayList<>();
        HashMap<Layout, SpansChunk> groupedByLayout = new HashMap<>();
        ArrayList<SpansChunk> backgroundDrawingArray = new ArrayList<>();
        private int rawIndex;

        public boolean isEmpty() {
            return holders.isEmpty();
        }

        public void add(Layout layout, AnimatedEmojiHolder holder) {
            holders.add(holder);
            SpansChunk chunkByLayout = groupedByLayout.get(layout);
            if (chunkByLayout == null) {
                chunkByLayout = new SpansChunk(holder.view, layout, holder.invalidateInParent);
                groupedByLayout.put(layout, chunkByLayout);
                backgroundDrawingArray.add(chunkByLayout);
            }
            chunkByLayout.add(holder);
            if (holder.drawable != null) {
                holder.drawable.addView(holder);
            }
        }

        public boolean hasLayout(Layout layout) {
            return groupedByLayout.containsKey(layout);
        }

        public void remove(Layout layout, AnimatedEmojiHolder holder) {
            holders.remove(holder);
            SpansChunk chunkByLayout = groupedByLayout.get(layout);
            if (chunkByLayout != null) {
                chunkByLayout.remove(holder);
                if (chunkByLayout.holders.isEmpty()) {
                    groupedByLayout.remove(layout);
                    backgroundDrawingArray.remove(chunkByLayout);
                }
            }
            if (holder.drawable != null) {
                holder.drawable.removeView(holder);
            }
        }

        public void remove(int i) {
            AnimatedEmojiHolder holder = holders.remove(i);
            SpansChunk chunkByLayout = groupedByLayout.get(holder.layout);
            if (chunkByLayout != null) {
                chunkByLayout.remove(holder);
                if (chunkByLayout.holders.isEmpty()) {
                    groupedByLayout.remove(holder.layout);
                    backgroundDrawingArray.remove(chunkByLayout);
                }
            } else {
                throw new RuntimeException("!!!");
            }
            if (holder.drawable != null) {
                holder.drawable.removeView(holder);
            }
        }

        public void release() {
            for (int i = 0; i < holders.size(); i++) {
                remove(i);
                i--;
            }
        }

        public void clearPositions() {
            for (int i = 0; i < holders.size(); i++) {
                holders.get(i).span.spanDrawn = false;
            }
        }

        public void recordPositions(boolean record) {
            for (int i = 0; i < holders.size(); i++) {
                holders.get(i).span.recordPositions = record;
            }
        }

        public void replaceLayout(Layout newText, Layout oldText) {
            if (oldText != null) {
                SpansChunk chunk = groupedByLayout.remove(oldText);
                if (chunk != null) {
                    chunk.layout = newText;
                    for (int i = 0; i < chunk.holders.size(); i++) {
                        chunk.holders.get(i).layout = newText;
                    }
                    groupedByLayout.put(newText, chunk);
                }
            }
        }
    }

    private static class SpansChunk {

        Layout layout;
        final View view;
        ArrayList<AnimatedEmojiHolder> holders = new ArrayList<>();
        DrawingInBackgroundThreadDrawable backgroundThreadDrawable;
        private boolean allowBackgroundRendering;

        public SpansChunk(View view, Layout layout, boolean allowBackgroundRendering) {
            this.layout = layout;
            this.view = view;
            this.allowBackgroundRendering = allowBackgroundRendering;
        }

        public void add(AnimatedEmojiHolder holder) {
            holders.add(holder);
            holder.spansChunk = this;
            checkBackgroundRendering();
        }

        public void remove(AnimatedEmojiHolder holder) {
            holders.remove(holder);
            holder.spansChunk = null;
            checkBackgroundRendering();
        }

        private void checkBackgroundRendering() {
            if (allowBackgroundRendering && holders.size() >= 10 && backgroundThreadDrawable == null && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD)) {
                backgroundThreadDrawable = new DrawingInBackgroundThreadDrawable() {

                    private final ArrayList<AnimatedEmojiHolder> backgroundHolders = new ArrayList<>();

                    @Override
                    public void drawInBackground(Canvas canvas) {
                        for (int i = 0; i < backgroundHolders.size(); i++) {
                            AnimatedEmojiHolder holder = backgroundHolders.get(i);
                            if (holder != null && holder.drawable != null && holder.backgroundDrawHolder[threadIndex] != null) {
                                holder.drawable.draw(canvas, holder.backgroundDrawHolder[threadIndex], true);
                            }
                        }
                    }

                    @Override
                    public void drawInUiThread(Canvas canvas, float alpha) {
                        long time = System.currentTimeMillis();
                        for (int i = 0; i < holders.size(); ++i) {
                            AnimatedEmojiHolder holder = holders.get(i);
                            if (!holder.span.spanDrawn) {
                                continue;
                            }
                            holder.draw(canvas, time, 0, 0, alpha, null);
                        }
                    }

                    @Override
                    public void prepareDraw(long time) {
                        backgroundHolders.clear();
                        backgroundHolders.addAll(holders);
                        for (int i = 0; i < backgroundHolders.size(); i++) {
                            AnimatedEmojiHolder holder = backgroundHolders.get(i);
                            if (!holder.span.spanDrawn) {
                                backgroundHolders.remove(i--);
                                continue;
                            }
                            holder.prepareForBackgroundDraw(time, threadIndex);
                        }
                    }

                    @Override
                    public void onFrameReady() {
                        for (int i = 0; i < backgroundHolders.size(); i++) {
                            if (backgroundHolders.get(i) != null) {
                                backgroundHolders.get(i).releaseDrawInBackground(threadIndex);
                            }
                        }
                        backgroundHolders.clear();
                        if (view != null && view.getParent() != null) {
                            ((View) view.getParent()).invalidate();
                        }
                    }

                    @Override
                    public void onPaused() {
                        super.onPaused();
                    }

                    @Override
                    public void onResume() {
                        if (view != null && view.getParent() != null) {
                            ((View) view.getParent()).invalidate();
                        }
                    }
                };
                backgroundThreadDrawable.padding = AndroidUtilities.dp(3);
                backgroundThreadDrawable.onAttachToWindow();
            } else if (holders.size() < 10 && backgroundThreadDrawable != null) {
                backgroundThreadDrawable.onDetachFromWindow();
                backgroundThreadDrawable = null;
            }
        }

        public void release() {
            holders.clear();
            checkBackgroundRendering();
        }

        public void draw(Canvas canvas, List<SpoilerEffect> spoilers, long time, float boundTop, float boundBottom, float drawingYOffset, float alpha, ColorFilter colorFilter) {
            for (int i = 0; i < holders.size(); ++i) {
                AnimatedEmojiHolder holder = holders.get(i);

                if (holder == null) {
                    continue;
                }
                AnimatedEmojiDrawable drawable = holder.drawable;
                if (drawable != null) {
                    drawable.setColorFilter(colorFilter);
                }
                if (!holder.span.spanDrawn) {
                    continue;
                }

                float halfSide = holder.span.measuredSize / 2f;
                float cx, cy;
                cx = holder.span.lastDrawnCx;
                cy = holder.span.lastDrawnCy;
                holder.drawableBounds.set((int) (cx - halfSide), (int) (cy - halfSide), (int) (cx + halfSide), (int) (cy + halfSide));

                float spoilerAlpha = 1f;
                if (spoilers != null && !spoilers.isEmpty() && holder.insideSpoiler) {
                    spoilerAlpha = Math.max(0, spoilers.get(0).getRippleProgress());
                }

                holder.drawingYOffset = drawingYOffset;
                holder.alpha = spoilerAlpha;

                if (backgroundThreadDrawable == null) {
                    holder.draw(canvas, time, boundTop, boundBottom, alpha, colorFilter);
                }
            }
            if (backgroundThreadDrawable != null) {
                backgroundThreadDrawable.draw(canvas, time, layout.getWidth(), layout.getHeight() + AndroidUtilities.dp(2), alpha);
            }
        }
    }

    public static AnimatedEmojiSpan cloneSpan(AnimatedEmojiSpan span, Paint.FontMetricsInt fontMetricsInt) {
        AnimatedEmojiSpan animatedEmojiSpan;
        if (span.document != null) {
            animatedEmojiSpan = new AnimatedEmojiSpan(span.document, fontMetricsInt != null ? fontMetricsInt : span.fontMetrics);
        } else {
            animatedEmojiSpan = new AnimatedEmojiSpan(span.documentId, span.scale, fontMetricsInt != null ? fontMetricsInt : span.fontMetrics);
        }
        if (fontMetricsInt != null) {
            animatedEmojiSpan.size = span.size;
        }
        animatedEmojiSpan.fromEmojiKeyboard = span.fromEmojiKeyboard;
        animatedEmojiSpan.isAdded = span.isAdded;
        animatedEmojiSpan.isRemoved = span.isRemoved;
        return animatedEmojiSpan;
    }

    public static CharSequence cloneSpans(CharSequence text) {
        return cloneSpans(text, -1, null);
    }

    public static CharSequence cloneSpans(CharSequence text, int newCacheType) {
        return cloneSpans(text, newCacheType, null);
    }

    public static CharSequence cloneSpans(CharSequence text, int newCacheType, Paint.FontMetricsInt fontMetricsInt) {
        return cloneSpans(text, newCacheType, fontMetricsInt, 1.0f);
    }

    public static CharSequence cloneSpans(CharSequence text, int newCacheType, Paint.FontMetricsInt fontMetricsInt, float scale) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        Spanned spanned = (Spanned) text;
        CharacterStyle[] spans = spanned.getSpans(0, spanned.length(), CharacterStyle.class);
        if (spans == null || spans.length <= 0) {
            return text;
        }
        AnimatedEmojiSpan[] aspans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
        if (aspans != null && aspans.length <= 0) {
            return text;
        }
        SpannableString newText = new SpannableString(spanned);
        for (int i = 0; i < spans.length; ++i) {
            if (spans[i] == null) {
                continue;
            }

            if (spans[i] instanceof AnimatedEmojiSpan) {
                int start = spanned.getSpanStart(spans[i]);
                int end = spanned.getSpanEnd(spans[i]);

                AnimatedEmojiSpan oldSpan = (AnimatedEmojiSpan) spans[i];
                newText.removeSpan(oldSpan);
                AnimatedEmojiSpan newSpan = cloneSpan(oldSpan, fontMetricsInt);
                if (newCacheType != -1) {
                    newSpan.cacheType = newCacheType;
                }
                newSpan.scale = oldSpan.scale * scale;
                newText.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
//                newText.setSpan(spans[i], start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return newText;
    }

    public static CharSequence onlyEmojiSpans(CharSequence text) {
        if (text == null) return null;
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        CharacterStyle[] spans = result.getSpans(0, result.length(), CharacterStyle.class);
        for (int i = 0; i < spans.length; ++i) {
            if (!(spans[i] instanceof AnimatedEmojiSpan) && !(spans[i] instanceof Emoji.EmojiSpan)) {
                result.removeSpan(spans[i]);
            }
        }
        return result;
    }

    public static class TextViewEmojis extends TextView {
        private int cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
        public TextViewEmojis(Context context) {
            super(context);
        }

        private ColorFilter emojiColorFilter;
        public void setEmojiColor(int emojiColor) {
            emojiColorFilter = new PorterDuffColorFilter(emojiColor, PorterDuff.Mode.SRC_IN);
        }

        public void setCacheType(int cacheType) {
            if (this.cacheType == cacheType) return;
            this.cacheType = cacheType;
            stack = AnimatedEmojiSpan.update(cacheType, this, stack, getLayout());
        }

        AnimatedEmojiSpan.EmojiGroupedSpans stack;

        @Override
        public void setText(CharSequence text, TextView.BufferType type) {
            super.setText(text, type);
            stack = AnimatedEmojiSpan.update(cacheType, this, stack, getLayout());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            stack = AnimatedEmojiSpan.update(cacheType, this, stack, getLayout());
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            stack = AnimatedEmojiSpan.update(cacheType, this, stack, getLayout());
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            AnimatedEmojiSpan.release(this, stack);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float offsetY = (getGravity() & Gravity.CENTER_VERTICAL) != 0 && getLayout() != null ? getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - getLayout().getHeight()) / 2f : 0;
            float offsetX = LocaleController.isRTL ? getPaddingRight() : getPaddingLeft();
            if (offsetY != 0 || offsetX != 0) {
                canvas.save();
                canvas.translate(offsetX, offsetY);
            }
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, getLayout(), stack, 0, null, 0, 0, 0, 1f, emojiColorFilter);
            if (offsetY != 0 || offsetX != 0) {
                canvas.restore();
            }
        }
    }

    public interface InvalidateHolder {
        void invalidate();
    }
}
