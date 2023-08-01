package org.telegram.ui.Stories;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class UploadingDotsSpannable extends ReplacementSpan {
    private String text = "…";

    private View parent;
    int swapPosition1 = 1;
    int swapPosition2 = 2;
    float swapProgress;
    boolean waitForNextAnimation;
    long lastTime;

    CubicBezierInterpolator circle = new CubicBezierInterpolator(0, 0.5f, 0.5f, 1f);
    private boolean isMediumTypeface;

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return (int) paint.measureText(this.text);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {

        TextPaint textPaint = (TextPaint) paint;
        float characterWidth = paint.measureText(this.text) / 3;
        float baseline = -textPaint.getFontMetrics().top;

        float textThickness = (float) ((textPaint.getFontMetrics().bottom - textPaint.getFontMetrics().top) * (isMediumTypeface ? 0.05f : 0.0365f));
        baseline -= textThickness;

        if (waitForNextAnimation) {
            if (System.currentTimeMillis() - lastTime > 1000) {
                waitForNextAnimation = false;
            }
        } else {
            swapProgress += 16 / 300f;
            if (swapProgress > 1) {
                swapProgress = 0;
                swapPosition1--;
                swapPosition2--;
                if (swapPosition1 < 0) {
                    swapPosition1 = 1;
                    swapPosition2 = 2;
                    waitForNextAnimation = true;
                    lastTime = System.currentTimeMillis();
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            float cx = characterWidth * i + x + characterWidth / 2f;
            float cy = baseline;
            if (i == swapPosition1) {
                float fromX = cx;
                float toX = characterWidth * (i + 1) + x + characterWidth / 2f;
                cx = AndroidUtilities.lerp(fromX, toX, swapProgress);
                float swapProgressHalf = swapProgress < 0.5f ? swapProgress / 0.5f : 1f - (swapProgress - 0.5f) / 0.5f;
                cy = AndroidUtilities.lerp(cy, cy - characterWidth / 2f, circle.getInterpolation(swapProgressHalf));
            } else if (i == swapPosition2) {
                float fromX = cx;
                float toX = characterWidth * (i - 1) + x + characterWidth / 2f;
                cx = AndroidUtilities.lerp(fromX, toX, swapProgress);
            }
            canvas.drawCircle(cx, cy, textThickness, paint);
        }
        if (parent != null) {
            parent.invalidate();
        }
    }

    public void setParent(View parent, boolean isMediumTypeface) {
        this.parent = parent;
        this.isMediumTypeface = isMediumTypeface;
    }
}