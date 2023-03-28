package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class PipettePickerView extends View {
    private final static float PIXELS_OFFSET = 3.5f;

    private Bitmap bitmap;

    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float positionX = 0.5f;
    private float positionY = 0.5f;

    private Path path = new Path();
    private Rect srcRect = new Rect();
    private RectF dstRect = new RectF();

    private int mColor;

    private boolean isDisappeared;
    private Consumer<Integer> colorListener;

    private float appearProgress;

    public PipettePickerView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(4));
        outlinePaint.setColor(Color.WHITE);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setColor(0x99FFFFFF);

        colorPaint.setStyle(Paint.Style.STROKE);
        colorPaint.setStrokeWidth(AndroidUtilities.dp(12));
    }

    public void setColorListener(Consumer<Integer> colorListener) {
        this.colorListener = colorListener;
    }

    public void animateShow() {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(150);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
        onStartPipette();
    }

    public void animateDisappear(boolean saveColor) {
        if (isDisappeared) {
            return;
        }
        isDisappeared = true;

        ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(150);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(animation -> {
            appearProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onStopPipette();

                if (saveColor) {
                    colorListener.accept(mColor);
                }

                if (getParent() != null) {
                    ((ViewGroup) getParent()).removeView(PipettePickerView.this);
                }
            }
        });
        animator.start();
    }

    protected void onStartPipette() {}
    protected void onStopPipette() {}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        onStopPipette();

        bitmap.recycle();
        bitmap = null;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                updatePosition(event);
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                updatePosition(event);
                break;
            case MotionEvent.ACTION_UP:
                animateDisappear(true);
                break;
            case MotionEvent.ACTION_CANCEL:
                animateDisappear(false);
                break;
        }
        return true;
    }

    private void updatePosition(MotionEvent event) {
        positionX = event.getX() / getWidth();
        positionY = event.getY() / getHeight();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w != 0 && h != 0 && oldw != 0 && oldh != 0 && isLaidOut()) {
            positionX = (oldw * positionX) / w;
            positionY = (oldh * positionY) / h;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float radius = Math.min(getWidth(), getHeight()) * 0.2f;

        float cx = positionX * getWidth(), cy = positionY * getHeight();

        int bx = Math.round(positionX * bitmap.getWidth()), by = Math.round(positionY * bitmap.getHeight());
        mColor = bitmap.getPixel(
                Utilities.clamp(bx, bitmap.getWidth(), 0),
                Utilities.clamp(by, bitmap.getHeight(), 0)
        );
        colorPaint.setColor(mColor);

        if (appearProgress != 0f && appearProgress != 1f) {
            AndroidUtilities.rectTmp.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (0xFF * appearProgress), Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }
        float scale = 0.5f + appearProgress * 0.5f;
        canvas.scale(scale, scale, cx, cy);

        path.rewind();
        path.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(path);

        int offsetInt = Math.round(PIXELS_OFFSET);
        srcRect.set(bx - offsetInt, by - offsetInt, bx + offsetInt, by + offsetInt);
        dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawBitmap(bitmap, srcRect, dstRect, null);

        radius -= colorPaint.getStrokeWidth() / 2f;
        canvas.drawCircle(cx, cy, radius, colorPaint);
        radius -= colorPaint.getStrokeWidth() / 2f;

        radius -= outlinePaint.getStrokeWidth() / 2f;
        canvas.drawCircle(cx, cy, radius, outlinePaint);
        radius -= outlinePaint.getStrokeWidth() / 2f;

        path.rewind();
        path.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(path);

        float pixelSize = (radius * 2) / (PIXELS_OFFSET * 2 + 1);

        path.rewind();
        for (float x = -PIXELS_OFFSET; x < PIXELS_OFFSET + 1; x++) {
            path.moveTo(cx + x * pixelSize, cy - radius);
            path.lineTo(cx + x * pixelSize, cy + radius);
        }
        for (float y = -PIXELS_OFFSET; y < PIXELS_OFFSET + 1; y++) {
            path.moveTo(cx - radius, cy + y * pixelSize);
            path.lineTo(cx + radius, cy + y * pixelSize);
        }
        canvas.drawPath(path, linePaint);

        dstRect.set(cx - pixelSize / 2, cy - pixelSize / 2, cx + pixelSize / 2, cy + pixelSize / 2);
        canvas.drawRoundRect(dstRect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), outlinePaint);

        canvas.restore();
    }
}
