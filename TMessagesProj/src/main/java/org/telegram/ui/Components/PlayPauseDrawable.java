package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class PlayPauseDrawable extends Drawable {

    private final Paint paint;
    private final int size;

    private boolean pause;
    private float progress;
    private long lastUpdateTime;

    private View parent;

    private int alpha = 255;

    float duration = 300f;

    public PlayPauseDrawable(int size) {
        this.size = AndroidUtilities.dp(size);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        long newUpdateTime = AnimationUtils.currentAnimationTimeMillis();
        long dt = newUpdateTime - lastUpdateTime;
        lastUpdateTime = newUpdateTime;
        if (dt > 18) {
            dt = 16;
        }
        if (pause && progress < 1f) {
            progress += dt / duration;
            if (progress >= 1f) {
                progress = 1f;
            } else {
                if (parent != null) {
                    parent.invalidate();
                }
                invalidateSelf();
            }
        } else if (!pause && progress > 0f) {
            progress -= dt / duration;
            if (progress <= 0f) {
                progress = 0f;
            } else {
                if (parent != null) {
                    parent.invalidate();
                }
                invalidateSelf();
            }
        }
        final Rect bounds = getBounds();
        if (alpha == 255) {
            canvas.save();
        } else {
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha, Canvas.ALL_SAVE_FLAG);
        }
        canvas.translate(bounds.centerX() + AndroidUtilities.dp(1) * (1.0f - progress), bounds.centerY());
        final float ms = 500.0f * progress;
        final float rotation;
        if (ms < 100) {
            rotation = -5 * CubicBezierInterpolator.EASE_BOTH.getInterpolation(ms / 100.0f);
        } else if (ms < 484) {
            rotation = -5 + 95 * CubicBezierInterpolator.EASE_BOTH.getInterpolation((ms - 100) / 384);
        } else {
            rotation = 90;
        }
        canvas.scale(1.45f * size / AndroidUtilities.dp(28), 1.5f * size / AndroidUtilities.dp(28));
        canvas.rotate(rotation);
        if (Theme.playPauseAnimator != null) {
            Theme.playPauseAnimator.draw(canvas, paint, ms);
            canvas.scale(1.0f, -1.0f);
            Theme.playPauseAnimator.draw(canvas, paint, ms);
        }
        canvas.restore();
    }

    public void setPause(boolean pause) {
        setPause(pause, true);
    }

    public void setPause(boolean pause, boolean animated) {
        if (this.pause != pause) {
            this.pause = pause;
            if (!animated) {
                progress = pause ? 1f : 0f;
            }
            this.lastUpdateTime = AnimationUtils.currentAnimationTimeMillis();
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int i) {
        alpha = i;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    public void setParent(View parent) {
        this.parent = parent;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
