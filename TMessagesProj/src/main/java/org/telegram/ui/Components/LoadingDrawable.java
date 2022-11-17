package org.telegram.ui.Components;


import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class LoadingDrawable extends Drawable {

    private Theme.ResourcesProvider resourcesProvider;
    public LoadingDrawable(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    private long start = -1;
    private LinearGradient gradient;
    private int gradientColor1, gradientColor2;
    public String colorKey1 = Theme.key_dialogBackground;
    public String colorKey2 = Theme.key_dialogBackgroundGray;
    private int gradientWidth;

    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private RectF[] rects;

    private void setPathRects(RectF[] rects) {
        this.rects = rects;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (getPaintAlpha() <= 0) {
            return;
        }
        int gwidth = Math.min(AndroidUtilities.dp(400), bounds.width());
        int color1 = Theme.getColor(colorKey1, resourcesProvider);
        int color2 = Theme.getColor(colorKey2, resourcesProvider);
        if (gradient == null || gwidth != gradientWidth || color1 != gradientColor1 || color2 != gradientColor2) {
            gradientWidth = gwidth;
            gradientColor1 = color1;
            gradientColor2 = color2;
            gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[] { gradientColor1, gradientColor2, gradientColor1 }, new float[] { 0f, .67f, 1f }, Shader.TileMode.REPEAT);
            paint.setShader(gradient);
        }

        long now = SystemClock.elapsedRealtime();
        if (start < 0) {
            start = now;
        }
        float offset = gradientWidth - (((now - start) / 1000f * gradientWidth) % gradientWidth);

        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(-offset, 0);
        path.reset();
        if (rects == null) {
            path.addRect(bounds.left + offset, bounds.top, bounds.right + offset, bounds.bottom, Path.Direction.CW);
        } else {
            for (int i = 0; i < rects.length; ++i) {
                RectF r = rects[i];
                if (r != null) {
                    path.addRect(r.left + offset, r.top, r.right + offset, r.bottom, Path.Direction.CW);
                }
            }
        }
        canvas.drawPath(path, paint);
        canvas.translate(offset, 0);
        canvas.restore();

        invalidateSelf();
    }

    public int getPaintAlpha() {
        return paint.getAlpha();
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
        if (i > 0) {
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
