package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

public class BlurredBackgroundWithFadeDrawable extends Drawable {
    private final Paint maskFadeGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurredBackgroundDrawable drawable;

    private int fadeHeight;
    private boolean opacity;

    public BlurredBackgroundWithFadeDrawable(BlurredBackgroundDrawable drawable) {
        this.drawable = drawable;
        maskFadeGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setFadeHeight(dp(40), false);
    }

    public void setFadeHeight(int fadeHeight, boolean opacity) {
        this.fadeHeight = fadeHeight;
        this.opacity = opacity;
        maskFadeGradientPaint.setShader(createGradient(Color.BLACK, fadeHeight, opacity));
        colorStaticPaint.setShader(null);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        drawable.setBounds(bounds);
    }

    private final Paint colorStaticPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorStaticLast;

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }

        BlurredBackgroundSource source = drawable.getUnwrappedSource();
        if (source instanceof BlurredBackgroundSourceColor) {
            // fast way - just draw gradient

            final int color = ((BlurredBackgroundSourceColor) source).getColor();
            if (colorStaticLast != color || colorStaticPaint.getShader() == null) {
                colorStaticLast = color;
                colorStaticPaint.setShader(createGradient(color, fadeHeight, opacity));
            }

            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), colorStaticPaint);
            canvas.restore();
            return;
        }
        colorStaticPaint.setShader(null);

        final int save = canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, null);
        drawable.draw(canvas);
        canvas.translate(bounds.left, bounds.top);
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), maskFadeGradientPaint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        //
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        //
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    private static LinearGradient createGradient(int color, int fadeHeight, boolean opacity) {
        final int alpha = Color.alpha(color);

        if (opacity) {
            return new LinearGradient(0, 0, 0, fadeHeight, new int[]{
                ColorUtils.setAlphaComponent(color, 0),
                ColorUtils.setAlphaComponent(color, 0x60 * alpha / 255),
                ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 255),
                ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            }, null, Shader.TileMode.CLAMP);
        }

        return new LinearGradient(0, 0, 0, fadeHeight, new int[]{
            ColorUtils.setAlphaComponent(color, 0),
            ColorUtils.setAlphaComponent(color, 0x60 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xFF * alpha / 255),
        }, null, Shader.TileMode.CLAMP);
    }
}
