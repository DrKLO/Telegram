package org.telegram.ui.Components.spoilers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SpoilersTextView extends TextView {
    private SpoilersClickDetector clickDetector;
    private List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private boolean isSpoilersRevealed;
    private Path path = new Path();
    private Paint xRefPaint;

    public SpoilersTextView(Context context) {
        super(context);

        clickDetector = new SpoilersClickDetector(this, spoilers, (eff, x, y) -> {
            if (isSpoilersRevealed) return;

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
        if (clickDetector.onTouchEvent(event))
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
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateSpoilers();
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

    private void invalidateSpoilers() {
        if (spoilers == null) return; // Check for a super constructor
        spoilersPool.addAll(spoilers);
        spoilers.clear();

        if (isSpoilersRevealed) {
            invalidate();
            return;
        }

        Layout layout = getLayout();
        if (layout != null && getText() instanceof Spannable) {
            SpoilerEffect.addSpoilers(this, spoilersPool, spoilers);
        }
        invalidate();
    }
}
