package org.telegram.ui.Components.spoilers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SpoilersTextView extends TextView implements TextSelectionHelper.SimpleSelectabeleView {
    private SpoilersClickDetector clickDetector;
    protected List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private boolean isSpoilersRevealed;
    private Path path = new Path();
    private Paint xRefPaint;
    public boolean allowClickSpoilers = true;

    public int cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmoji;

    public SpoilersTextView(Context context) {
        this(context, true);
    }

    public SpoilersTextView(Context context, boolean revealOnClick) {
        super(context);

        clickDetector = new SpoilersClickDetector(this, spoilers, (eff, x, y) -> {
            if (isSpoilersRevealed || !revealOnClick) return;

            eff.setOnRippleEndCallback(()->post(()->{
                isSpoilersRevealed = true;
                invalidateSpoilers();
            }));

            float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
            for (SpoilerEffect ef : spoilers)
                ef.startRipple(x, y, rad);
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (allowClickSpoilers && clickDetector.onTouchEvent(event))
            return true;
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        isSpoilersRevealed = false;
        super.setText(text, type);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        invalidateSpoilers();
        updateAnimatedEmoji(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateSpoilers();
    }

    private ColorFilter animatedEmojiColorFilter;
    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        animatedEmojiColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int pl = getPaddingLeft(), pt = getPaddingTop();

        canvas.save();
        path.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect bounds = eff.getBounds();
            path.addRect(bounds.left + pl, bounds.top + pt, bounds.right + pl, bounds.bottom + pt, Path.Direction.CW);
        }
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        super.onDraw(canvas);
        canvas.restore();

        canvas.save();
        canvas.clipPath(path);
        path.rewind();
        if (!spoilers.isEmpty()) {
            spoilers.get(0).getRipplePath(path);
        }
        canvas.clipPath(path);
        super.onDraw(canvas);
        canvas.restore();

        updateAnimatedEmoji(false);
        if (animatedEmoji != null) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, getLayout(), animatedEmoji, 0, spoilers, 0, getHeight(), 0, 1f, animatedEmojiColorFilter);
            canvas.restore();
        }

        if (!spoilers.isEmpty()) {
            boolean useAlphaLayer = spoilers.get(0).getRippleProgress() != -1;
            if (useAlphaLayer) {
                canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), null, canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(getPaddingLeft(), getPaddingTop() + AndroidUtilities.dp(2));
            for (SpoilerEffect eff : spoilers) {
                eff.setColor(getPaint().getColor());
                eff.draw(canvas);
            }

            if (useAlphaLayer) {
                path.rewind();
                spoilers.get(0).getRipplePath(path);
                if (xRefPaint == null) {
                    xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    xRefPaint.setColor(0xff000000);
                    xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                canvas.drawPath(path, xRefPaint);
            }
            canvas.restore();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimatedEmoji(true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        invalidateSpoilers();
    }

    private Layout lastLayout = null;
    private int lastTextLength;
    public void updateAnimatedEmoji(boolean force) {
        int newTextLength = (getLayout() == null || getLayout().getText() == null) ? 0 : getLayout().getText().length();
        if (force || lastLayout != getLayout() || lastTextLength != newTextLength) {
            animatedEmoji = AnimatedEmojiSpan.update(cacheType, this, animatedEmoji, getLayout());
            lastLayout = getLayout();
            lastTextLength = newTextLength;
        }
    }

    private void invalidateSpoilers() {
        if (spoilers == null) return; // Check for a super constructor
        spoilersPool.addAll(spoilers);
        spoilers.clear();

        if (isSpoilersRevealed) {
            invalidate();
            return;
        }

        Layout layout = getLayout();
        if (layout != null && getText() instanceof Spanned) {
            SpoilerEffect.addSpoilers(this, spoilersPool, spoilers);
        }
        invalidate();
    }

    @Override
    public Layout getStaticTextLayout() {
        return getLayout();
    }
}
