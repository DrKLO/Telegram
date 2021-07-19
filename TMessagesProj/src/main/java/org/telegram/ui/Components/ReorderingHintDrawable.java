package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class ReorderingHintDrawable extends Drawable {

    private static final int DURATION_DELAY1 = 300;
    private static final int DURATION_STAGE1 = 150;
    private static final int DURATION_DELAY2 = 300;
    private static final int DURATION_STAGE2 = 200;
    private static final int DURATION_DELAY3 = 300;
    private static final int DURATION_STAGE3 = 150;
    private static final int DURATION_DELAY4 = 100;

    public static final int DURATION = DURATION_DELAY1 + DURATION_STAGE1 + DURATION_DELAY2 + DURATION_STAGE2 + DURATION_DELAY3 + DURATION_STAGE3 + DURATION_DELAY4;

    private final Rect tempRect = new Rect();

    private final Interpolator interpolator = Easings.easeInOutSine;

    private final int intrinsicWidth = AndroidUtilities.dp(24);
    private final int intrinsicHeight = AndroidUtilities.dp(24);

    private final RectDrawable primaryRectDrawable;
    private final RectDrawable secondaryRectDrawable;

    private long startedTime = -1;

    private float scaleX;
    private float scaleY;

    public ReorderingHintDrawable() {
        primaryRectDrawable = new RectDrawable();
        primaryRectDrawable.setColor(0x80FFFFFF);
        secondaryRectDrawable = new RectDrawable();
        secondaryRectDrawable.setColor(0x80FFFFFF);
    }

    public void startAnimation() {
        startedTime = System.currentTimeMillis();
        invalidateSelf();
    }

    public void resetAnimation() {
        startedTime = -1;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        scaleX = bounds.width() / (float) intrinsicWidth;
        scaleY = bounds.height() / (float) intrinsicHeight;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (startedTime > 0) {
            int passedTime = (int) (System.currentTimeMillis() - startedTime) - DURATION_DELAY1;
            if (passedTime >= 0) {
                if (passedTime < DURATION_STAGE1) {
                    drawStage1(canvas, passedTime / (float) DURATION_STAGE1);
                } else {
                    passedTime -= DURATION_STAGE1 + DURATION_DELAY2;
                    if (passedTime >= 0) {
                        if (passedTime < DURATION_STAGE2) {
                            drawStage2(canvas, passedTime / (float) DURATION_STAGE2);
                        } else {
                            passedTime -= DURATION_STAGE2 + DURATION_DELAY3;
                            if (passedTime >= 0) {
                                if (passedTime < DURATION_STAGE3) {
                                    drawStage3(canvas, passedTime / (float) DURATION_STAGE3);
                                } else {
                                    drawStage3(canvas, 1f);
                                    if (passedTime - DURATION_STAGE3 >= DURATION_DELAY4) {
                                        startedTime = System.currentTimeMillis();
                                    }
                                }
                            } else {
                                drawStage2(canvas, 1f);
                            }
                        }
                    } else {
                        drawStage1(canvas, 1f);
                    }
                }
            } else {
                drawStage1(canvas, 0f);
            }
            invalidateSelf();
        } else {
            drawStage1(canvas, 0f);
        }
    }

    private void drawStage1(Canvas canvas, float progress) {
        final Rect bounds = getBounds();

        progress = interpolator.getInterpolation(progress);

        tempRect.left = (int) (AndroidUtilities.dp(2) * scaleX);
        tempRect.bottom = bounds.bottom - ((int) (AndroidUtilities.dp(6) * scaleY));
        tempRect.right = bounds.right - tempRect.left;
        tempRect.top = tempRect.bottom - ((int) (AndroidUtilities.dp(4) * scaleY));
        secondaryRectDrawable.setBounds(tempRect);
        secondaryRectDrawable.draw(canvas);

        tempRect.left = tempRect.right = AndroidUtilities.dp(12);
        tempRect.top = tempRect.bottom = AndroidUtilities.dp(8);
        tempRect.inset(-AndroidUtilities.dp(AndroidUtilities.lerp(10, 11, progress)), -AndroidUtilities.dp(AndroidUtilities.lerp(2, 3, progress)));
        primaryRectDrawable.setBounds(tempRect);
        primaryRectDrawable.setAlpha((int) AndroidUtilities.lerp(128, 255, progress));
        primaryRectDrawable.draw(canvas);
    }

    private void drawStage2(Canvas canvas, float progress) {
        final Rect bounds = getBounds();

        progress = interpolator.getInterpolation(progress);

        tempRect.left = (int) (AndroidUtilities.dp(2) * scaleX);
        tempRect.bottom = bounds.bottom - ((int) (AndroidUtilities.dp(6) * scaleY));
        tempRect.right = bounds.right - tempRect.left;
        tempRect.top = tempRect.bottom - ((int) (AndroidUtilities.dp(4) * scaleY));
        tempRect.offset(0, AndroidUtilities.dp(AndroidUtilities.lerp(0, -8, progress)));
        secondaryRectDrawable.setBounds(tempRect);
        secondaryRectDrawable.draw(canvas);

        tempRect.left = (int) (AndroidUtilities.dpf2(AndroidUtilities.lerp(1, 2, progress)) * scaleX);
        tempRect.top = (int) (AndroidUtilities.dpf2(AndroidUtilities.lerp(5, 6, progress)) * scaleY);
        tempRect.right = bounds.right - tempRect.left;
        tempRect.bottom = tempRect.top + (int) (AndroidUtilities.dpf2(AndroidUtilities.lerp(6, 4, progress)) * scaleY);
        tempRect.offset(0, AndroidUtilities.dp(AndroidUtilities.lerp(0, 8, progress)));
        primaryRectDrawable.setBounds(tempRect);
        primaryRectDrawable.setAlpha(255);
        primaryRectDrawable.draw(canvas);
    }

    private void drawStage3(Canvas canvas, float progress) {
        final Rect bounds = getBounds();

        progress = interpolator.getInterpolation(progress);

        tempRect.left = (int) (AndroidUtilities.dp(2) * scaleX);
        tempRect.bottom = bounds.bottom - ((int) (AndroidUtilities.dp(6) * scaleY));
        tempRect.right = bounds.right - tempRect.left;
        tempRect.top = tempRect.bottom - ((int) (AndroidUtilities.dp(4) * scaleY));
        tempRect.offset(0, AndroidUtilities.dp(-8));
        secondaryRectDrawable.setBounds(tempRect);
        secondaryRectDrawable.draw(canvas);

        tempRect.left = (int) (AndroidUtilities.dpf2(2) * scaleX);
        tempRect.top = (int) (AndroidUtilities.dpf2(6) * scaleY);
        tempRect.right = bounds.right - tempRect.left;
        tempRect.bottom = tempRect.top + (int) (AndroidUtilities.dpf2(4) * scaleY);
        tempRect.offset(0, AndroidUtilities.dp(8));
        primaryRectDrawable.setBounds(tempRect);
        primaryRectDrawable.setAlpha((int) AndroidUtilities.lerp(255, 128, progress));
        primaryRectDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        primaryRectDrawable.setColorFilter(colorFilter);
        secondaryRectDrawable.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    protected static class RectDrawable extends Drawable {

        private final RectF tempRect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(@NonNull Canvas canvas) {
            tempRect.set(getBounds());
            final float radius = tempRect.height() * 0.2f;
            canvas.drawRoundRect(tempRect, radius, radius, paint);
        }

        public void setColor(int color) {
            paint.setColor(color);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
