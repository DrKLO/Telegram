package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;

public class AnimatedArrowDrawable extends Drawable {

    private Paint paint;
    private Path path = new Path();
    private float animProgress;
    private float animateToProgress;
    private long lastUpdateTime;
    private boolean isSmall;

    public AnimatedArrowDrawable(int color, boolean small) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        isSmall = small;

        updatePath();
    }

    @Override
    public void draw(Canvas c) {
        c.drawPath(path, paint);
        checkAnimation();
    }

    private void updatePath() {
        path.reset();
        float p = animProgress * 2 - 1;
        if (isSmall) {
            path.moveTo(AndroidUtilities.dp(3), AndroidUtilities.dp(6) - AndroidUtilities.dp(2) * p);
            path.lineTo(AndroidUtilities.dp(8), AndroidUtilities.dp(6) + AndroidUtilities.dp(2) * p);
            path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(6) - AndroidUtilities.dp(2) * p);
        } else {
            path.moveTo(AndroidUtilities.dp(4.5f), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(12) + AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(21.5f), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
        }
    }

    @Keep
    public void setAnimationProgress(float progress) {
        animProgress = progress;
        animateToProgress = progress;
        updatePath();
        invalidateSelf();
    }

    public void setAnimationProgressAnimated(float progress) {
        if (animateToProgress == progress) {
            return;
        }
        animateToProgress = progress;
        lastUpdateTime = SystemClock.elapsedRealtime();
        invalidateSelf();
    }

    private void checkAnimation() {
        if (animateToProgress != animProgress) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            if (animProgress < animateToProgress) {
                animProgress += dt / 180.0f;
                if (animProgress > animateToProgress) {
                    animProgress = animateToProgress;
                }
            } else {
                animProgress -= dt / 180.0f;
                if (animProgress < animateToProgress) {
                    animProgress = animateToProgress;
                }
            }
            updatePath();
            invalidateSelf();
        }
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public float getAnimationProgress() {
        return animProgress;
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(26);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(26);
    }
}
