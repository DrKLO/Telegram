package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class MotionPhotoDrawable extends Drawable {

    private final Path play = new Path();

    public final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint playPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MotionPhotoDrawable() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xFFFFFFFF);

        playPaint.setPathEffect(new CornerPathEffect(dpf2(2)));
        playPaint.setColor(0xFFFFFFFF);

        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        play.moveTo(-dpf2(3.75f), -dpf2(5.4166f));
        play.lineTo(dpf2(3.75f), 0);
        play.lineTo(-dpf2(3.75f), dpf2(5.4166f));
        play.close();
    }

    private boolean disabled;
    private final AnimatedFloat animatedDisabled = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    public void setDisabled(boolean disabled, boolean animated) {
        this.disabled = disabled;
        if (!animated) {
            animatedDisabled.force(disabled);
        }
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        strokePaint.setStrokeWidth(dpf2(1.66f));
        clearPaint.setStrokeWidth(dpf2(1.66f + 1.66f));

        final float d = animatedDisabled.set(disabled);

        final float cx = getBounds().centerX();
        final float cy = getBounds().centerY();
        final float r = dpf2(10.66f);

        AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(8.33f), dpf2(8.33f), strokePaint);

        if (d > 0) {
            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 0xFF, Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }

        canvas.save();
        canvas.translate(cx + dpf2(1), cy - dpf2(0.5f));
        canvas.drawPath(play, playPaint);
        canvas.restore();

        if (d > 0) {
            if (disabled) {
                canvas.drawLine(
                    cx - dpf2(8.33f),
                    cy - dpf2(8.33f),
                    cx - dpf2(8.33f) + dpf2(16.66f) * d,
                    cy - dpf2(8.33f) + dpf2(16.66f) * d,
                    clearPaint
                );
                canvas.drawLine(
                    cx - dpf2(8.33f),
                    cy - dpf2(8.33f),
                    cx - dpf2(8.33f) + dpf2(16.66f) * d,
                    cy - dpf2(8.33f) + dpf2(16.66f) * d,
                    strokePaint
                );
            } else {
                canvas.drawLine(
                    cx + dpf2(8.33f),
                    cy + dpf2(8.33f),
                    cx + dpf2(8.33f) - dpf2(16.66f) * d,
                    cy + dpf2(8.33f) - dpf2(16.66f) * d,
                    clearPaint
                );
                canvas.drawLine(
                    cx + dpf2(8.33f),
                    cy + dpf2(8.33f),
                    cx + dpf2(8.33f) - dpf2(16.66f) * d,
                    cy + dpf2(8.33f) - dpf2(16.66f) * d,
                    strokePaint
                );
            }
        }

        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }

//    private float alpha = 1.0f;
    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        playPaint.setAlpha(alpha);
//        this.alpha = alpha / 255.0f;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        playPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
