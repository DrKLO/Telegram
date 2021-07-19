package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.widget.Button;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ProgressButton extends Button {

    private final RectF progressRect;
    private final Paint progressPaint;

    private boolean drawProgress;
    private float progressAlpha;
    private long lastUpdateTime;
    private int angle;

    public ProgressButton(Context context) {
        super(context);
        setAllCaps(false);
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(null);
        }

        ViewHelper.setPadding(this, 8, 0, 8, 0);
        final int minWidth = AndroidUtilities.dp(60);
        setMinWidth(minWidth);
        setMinimumWidth(minWidth);

        progressRect = new RectF();
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawProgress || progressAlpha != 0) {
            int x = getMeasuredWidth() - AndroidUtilities.dp(11);
            progressRect.set(x, AndroidUtilities.dp(3), x + AndroidUtilities.dp(8), AndroidUtilities.dp(8 + 3));
            progressPaint.setAlpha(Math.min(255, (int) (progressAlpha * 255)));
            canvas.drawArc(progressRect, angle, 220, false, progressPaint);
            long newTime = System.currentTimeMillis();
            if (Math.abs(lastUpdateTime - System.currentTimeMillis()) < 1000) {
                long delta = (newTime - lastUpdateTime);
                float dt = 360 * delta / 2000.0f;
                angle += dt;
                angle -= 360 * (angle / 360);
                if (drawProgress) {
                    if (progressAlpha < 1.0f) {
                        progressAlpha += delta / 200.0f;
                        if (progressAlpha > 1.0f) {
                            progressAlpha = 1.0f;
                        }
                    }
                } else {
                    if (progressAlpha > 0.0f) {
                        progressAlpha -= delta / 200.0f;
                        if (progressAlpha < 0.0f) {
                            progressAlpha = 0.0f;
                        }
                    }
                }
            }
            lastUpdateTime = newTime;
            postInvalidateOnAnimation();
        }
    }

    public void setBackgroundRoundRect(int backgroundColor, int pressedBackgroundColor) {
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), backgroundColor, pressedBackgroundColor));
    }

    public void setProgressColor(int progressColor) {
        progressPaint.setColor(progressColor);
    }

    public void setDrawProgress(boolean drawProgress, boolean animated) {
        if (this.drawProgress != drawProgress) {
            this.drawProgress = drawProgress;
            if (!animated) {
                progressAlpha = drawProgress ? 1.0f : 0.0f;
            }
            lastUpdateTime = System.currentTimeMillis();
            invalidate();
        }
    }
}
