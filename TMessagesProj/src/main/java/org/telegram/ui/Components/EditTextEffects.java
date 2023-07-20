package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilersClickDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class EditTextEffects extends EditText {
    private final static int SPOILER_TIMEOUT = 10000;

    private List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private boolean isSpoilersRevealed;
    private boolean shouldRevealSpoilersByTouch = true;
    private SpoilersClickDetector clickDetector;
    public boolean suppressOnTextChanged;
    private Path path = new Path();
    private int selStart, selEnd;
    private float lastRippleX, lastRippleY;
    private boolean postedSpoilerTimeout;
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiDrawables;
    private ColorFilter animatedEmojiColorFilter;
    public boolean drawAnimatedEmojiDrawables = true;
    private Layout lastLayout = null;
    private int lastTextLength;

    private Runnable spoilerTimeout = () -> {
        postedSpoilerTimeout = false;
        isSpoilersRevealed = false;
        invalidateSpoilers();
        if (spoilers.isEmpty())
            return;

        spoilers.get(0).setOnRippleEndCallback(() -> post(() -> setSpoilersRevealed(false, true)));
        float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
        for (SpoilerEffect eff : spoilers) {
            eff.startRipple(lastRippleX, lastRippleY, rad, true);
        }
    };
    private Rect rect = new Rect();
    private boolean clipToPadding;

    public EditTextEffects(Context context) {
        super(context);

        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            clickDetector = new SpoilersClickDetector(this, spoilers, this::onSpoilerClicked);
        }
    }

    private void onSpoilerClicked(SpoilerEffect eff, float x, float y) {
        if (isSpoilersRevealed) return;

        lastRippleX = x;
        lastRippleY = y;

        postedSpoilerTimeout = false;
        removeCallbacks(spoilerTimeout);

        setSpoilersRevealed(true, false);
        eff.setOnRippleEndCallback(() -> post(() -> {
            invalidateSpoilers();
            checkSpoilerTimeout();
        }));

        float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
        for (SpoilerEffect ef : spoilers)
            ef.startRipple(x, y, rad);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (suppressOnTextChanged)
            return;

        this.selStart = selStart;
        this.selEnd = selEnd;

        checkSpoilerTimeout();
    }

    /**
     * Checks for spoiler timeout to be posted
     */
    private void checkSpoilerTimeout() {
        boolean onSpoiler = false;
        CharSequence cs = getLayout() != null ? getLayout().getText() : null;
        if (cs instanceof Spannable) {
            Spannable e = (Spannable) cs;
            TextStyleSpan[] spans = e.getSpans(0, e.length(), TextStyleSpan.class);
            for (TextStyleSpan span : spans) {
                int ss = e.getSpanStart(span), se = e.getSpanEnd(span);
                if (span.isSpoiler()) {
                    if (ss > selStart && se < selEnd || selStart > ss && selStart < se || selEnd > ss && selEnd < se) {
                        onSpoiler = true;
                        removeCallbacks(spoilerTimeout);
                        postedSpoilerTimeout = false;
                        break;
                    }
                }
            }
        }

        if (isSpoilersRevealed && !onSpoiler && !postedSpoilerTimeout) {
            postedSpoilerTimeout = true;
            postDelayed(spoilerTimeout, SPOILER_TIMEOUT);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        removeCallbacks(spoilerTimeout);
        AnimatedEmojiSpan.release(this, animatedEmojiDrawables);
    }

    public void recycleEmojis() {
        AnimatedEmojiSpan.release(this, animatedEmojiDrawables);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimatedEmoji(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateEffects();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (!suppressOnTextChanged) {
            invalidateEffects();

            try {
                Layout layout = getLayout();
                if (text instanceof Spannable && layout != null) {
                    int line = layout.getLineForOffset(start);
                    int x = (int) layout.getPrimaryHorizontal(start);
                    int y = (int) ((layout.getLineTop(line) + layout.getLineBottom(line)) / 2f);

                    for (SpoilerEffect eff : spoilers) {
                        if (eff.getBounds().contains(x, y)) {
                            int selOffset = lengthAfter - lengthBefore;
                            selStart += selOffset;
                            selEnd += selOffset;
                            onSpoilerClicked(eff, x, y);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        updateAnimatedEmoji(true);
        invalidate();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!suppressOnTextChanged) {
            isSpoilersRevealed = false;
            if (spoilersPool != null) // Constructor check
                spoilersPool.clear();
        }
        super.setText(text, type);
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        animatedEmojiColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateAnimatedEmoji(false);
    }

    /**
     * Sets if spoilers should be revealed by touch or not
     */
    public void setShouldRevealSpoilersByTouch(boolean shouldRevealSpoilersByTouch) {
        this.shouldRevealSpoilersByTouch = shouldRevealSpoilersByTouch;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean detector = false;
        if (shouldRevealSpoilersByTouch && clickDetector != null && clickDetector.onTouchEvent(event)) {
            int act = event.getActionMasked();
            if (act == MotionEvent.ACTION_UP) {
                MotionEvent c = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                super.dispatchTouchEvent(c);
                c.recycle();
            }
            detector = true;
        }
        return super.dispatchTouchEvent(event) || detector;
    }

    /**
     * Sets if spoiler are already revealed or not
     */
    public void setSpoilersRevealed(boolean spoilersRevealed, boolean notifyEffects) {
        isSpoilersRevealed = spoilersRevealed;
        Spannable text = getText();
        if (text != null) {
            TextStyleSpan[] spans = text.getSpans(0, text.length(), TextStyleSpan.class);
            for (TextStyleSpan span : spans) {
                if (span.isSpoiler()) {
                    span.setSpoilerRevealed(spoilersRevealed);
                }
            }
        }
        suppressOnTextChanged = true;
        setText(text, BufferType.EDITABLE);
        setSelection(selStart, selEnd);
        suppressOnTextChanged = false;

        if (notifyEffects) {
            invalidateSpoilers();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (clipToPadding && getScrollY() != 0) {
            canvas.clipRect(0, getScrollY(), getMeasuredWidth(), getMeasuredHeight() + getScrollY());
        }
        path.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect bounds = eff.getBounds();
            path.addRect(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW);
        }
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        updateAnimatedEmoji(false);
        super.onDraw(canvas);
        if (drawAnimatedEmojiDrawables && animatedEmojiDrawables != null) {
            canvas.save();
            canvas.translate(getPaddingLeft(), 0);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, getLayout(), animatedEmojiDrawables, 0, spoilers, computeVerticalScrollOffset() - AndroidUtilities.dp(6), computeVerticalScrollOffset() + computeVerticalScrollExtent(), 0, 1f, animatedEmojiColorFilter);
            canvas.restore();
        }
        canvas.restore();

        canvas.save();
        canvas.clipPath(path);
        path.rewind();
        if (!spoilers.isEmpty())
            spoilers.get(0).getRipplePath(path);
        canvas.clipPath(path);
        canvas.translate(0, -getPaddingTop());
        super.onDraw(canvas);
        canvas.restore();

        rect.set(0, getScrollY(), getWidth(), getScrollY() + getHeight() - getPaddingBottom());
        canvas.save();
        canvas.clipRect(rect);
        for (SpoilerEffect eff : spoilers) {
            Rect b = eff.getBounds();
            if (rect.top <= b.bottom && rect.bottom >= b.top || b.top <= rect.bottom && b.bottom >= rect.top) {
                eff.setColor(getPaint().getColor());
                eff.draw(canvas);
            }
        }
        canvas.restore();
    }

    public void updateAnimatedEmoji(boolean force) {
        if (!drawAnimatedEmojiDrawables) {
            return;
        }
        int newTextLength = (getLayout() == null || getLayout().getText() == null) ? 0 : getLayout().getText().length();
        if (force || lastLayout != getLayout() || lastTextLength != newTextLength) {
            animatedEmojiDrawables = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.getCacheTypeForEnterView(), this, animatedEmojiDrawables, getLayout());
            lastLayout = getLayout();
            lastTextLength = newTextLength;
        }
    }

    public void invalidateEffects() {
        Editable text = getText();
        if (text != null) {
            for (TextStyleSpan span : text.getSpans(0, text.length(), TextStyleSpan.class)) {
                if (span.isSpoiler()) {
                    span.setSpoilerRevealed(isSpoilersRevealed);
                }
            }
        }
        invalidateSpoilers();
    }

    protected void invalidateSpoilers() {
        if (spoilers == null) return; // A null-check for super constructor, because it calls onTextChanged
        spoilersPool.addAll(spoilers);
        spoilers.clear();

        if (isSpoilersRevealed) {
            invalidate();
            return;
        }

        Layout layout = getLayout();
        if (layout != null && layout.getText() instanceof Spannable) {
            if (drawAnimatedEmojiDrawables && animatedEmojiDrawables != null) {
                animatedEmojiDrawables.recordPositions(false);
            }
            SpoilerEffect.addSpoilers(this, spoilersPool, spoilers);
            if (drawAnimatedEmojiDrawables && animatedEmojiDrawables != null) {
                animatedEmojiDrawables.recordPositions(true);
            }
        }
        invalidate();
    }

    public void setClipToPadding(boolean clipToPadding) {
        this.clipToPadding = clipToPadding;
    }
}
