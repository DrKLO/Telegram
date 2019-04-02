package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;

public class VideoForwardDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path1 = new Path();
    private boolean leftSide;

    private final static int[] playPath = new int[] {10, 7, 26, 16, 10, 25};

    private float animationProgress;
    private boolean animating;

    private long lastAnimationTime;
    private VideoForwardDrawableDelegate delegate;

    public interface VideoForwardDrawableDelegate {
        void onAnimationEnd();
        void invalidate();
    }

    public VideoForwardDrawable() {
        paint.setColor(0xffffffff);

        path1.reset();
        for (int a = 0; a < playPath.length / 2; a++) {
            if (a == 0) {
                path1.moveTo(AndroidUtilities.dp(playPath[a * 2]), AndroidUtilities.dp(playPath[a * 2 + 1]));
            } else {
                path1.lineTo(AndroidUtilities.dp(playPath[a * 2]), AndroidUtilities.dp(playPath[a * 2 + 1]));
            }
        }
        path1.close();
    }

    public boolean isAnimating() {
        return animating;
    }

    public void startAnimation() {
        animating = true;
        animationProgress = 0.0f;
        invalidateSelf();
    }

    public void setLeftSide(boolean value) {
        if (leftSide == value && animationProgress >= 1.0f) {
            return;
        }
        leftSide = value;
        startAnimation();
    }

    public void setDelegate(VideoForwardDrawableDelegate videoForwardDrawableDelegate) {
        delegate = videoForwardDrawableDelegate;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    public void setColor(int value) {
        paint.setColor(value);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect rect = getBounds();

        int x = rect.left + (rect.width() - getIntrinsicWidth()) / 2;
        int y = rect.top + (rect.height() - getIntrinsicHeight()) / 2;
        if (leftSide) {
            x -= rect.width() / 4 - AndroidUtilities.dp(16);
        } else {
            x += rect.width() / 4 + AndroidUtilities.dp(16);
        }

        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom);
        if (animationProgress <= 0.7f) {
            paint.setAlpha((int) (80 * Math.min(1.0f, animationProgress / 0.3f)));
        } else {
            paint.setAlpha((int) (80 * (1.0f - (animationProgress - 0.7f) / 0.3f)));
        }
        canvas.drawCircle(x + Math.max(rect.width(), rect.height()) / 4 * (leftSide ? -1 : 1), y + AndroidUtilities.dp(16), Math.max(rect.width(), rect.height()) / 2, paint);
        canvas.restore();

        canvas.save();
        if (leftSide) {
            canvas.rotate(180, x, y + getIntrinsicHeight() / 2);
        }
        canvas.translate(x, y);
        if (animationProgress <= 0.6f) {
            if (animationProgress < 0.4f) {
                paint.setAlpha(Math.min(255, (int) (255 * animationProgress / 0.2f)));
            } else {
                paint.setAlpha((int) (255 * (1.0f - (animationProgress - 0.4f) / 0.2f)));
            }
            canvas.drawPath(path1, paint);
        }
        canvas.translate(AndroidUtilities.dp(18), 0);
        if (animationProgress >= 0.2f && animationProgress <= 0.8f) {
            float progress = animationProgress - 0.2f;
            if (progress < 0.4f) {
                paint.setAlpha(Math.min(255, (int) (255 * progress / 0.2f)));
            } else {
                paint.setAlpha((int) (255 * (1.0f - (progress - 0.4f) / 0.2f)));
            }
            canvas.drawPath(path1, paint);
        }
        canvas.translate(AndroidUtilities.dp(18), 0);
        if (animationProgress >= 0.4f && animationProgress <= 1.0f) {
            float progress = animationProgress - 0.4f;
            if (progress < 0.4f) {
                paint.setAlpha(Math.min(255, (int) (255 * progress / 0.2f)));
            } else {
                paint.setAlpha((int) (255 * (1.0f - (progress - 0.4f) / 0.2f)));
            }
            canvas.drawPath(path1, paint);
        }
        canvas.restore();

        if (animating) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastAnimationTime;
            if (dt > 17) {
                dt = 17;
            }
            lastAnimationTime = newTime;
            if (animationProgress < 1.0f) {
                animationProgress += dt / 800.0f;
                if (animationProgress >= 1.0f) {
                    animationProgress = 0.0f;
                    animating = false;
                    if (delegate != null) {
                        delegate.onAnimationEnd();
                    }
                }
                if (delegate != null) {
                    delegate.invalidate();
                } else {
                    invalidateSelf();
                }
            }
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(32);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(32);
    }

    @Override
    public int getMinimumWidth() {
        return AndroidUtilities.dp(32);
    }

    @Override
    public int getMinimumHeight() {
        return AndroidUtilities.dp(32);
    }
}
