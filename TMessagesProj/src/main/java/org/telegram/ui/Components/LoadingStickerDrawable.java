package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;

public class LoadingStickerDrawable extends Drawable {

    private Bitmap bitmap;
    private Paint placeholderPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private long lastUpdateTime;
    private LinearGradient placeholderGradient;
    private Matrix placeholderMatrix;
    private float totalTranslation;
    private float gradientWidth;
    private View parentView;

    int currentColor0;
    int currentColor1;

    public LoadingStickerDrawable(View parent, String svg, int w, int h) {
        bitmap = SvgHelper.getBitmapByPathOnly(svg,512,512, w, h);
        parentView = parent;
        placeholderMatrix = new Matrix();
    }

    public void setColors(String key1, String key2) {
        int color0 = Theme.getColor(key1);
        int color1 = Theme.getColor(key2);
        if (currentColor0 != color0 || currentColor1 != color1) {
            currentColor0 = color0;
            currentColor1 = color1;
            color0 = AndroidUtilities.getAverageColor(color1, color0);
            placeholderPaint.setColor(color1);
            placeholderGradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(500), 0, new int[]{color1, color0, color1}, new float[]{0.0f, 0.18f, 0.36f}, Shader.TileMode.REPEAT);
            placeholderGradient.setLocalMatrix(placeholderMatrix);
            Shader shaderB = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            placeholderPaint.setShader(new ComposeShader(placeholderGradient, shaderB, PorterDuff.Mode.MULTIPLY));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (bitmap == null) {
            return;
        }
        setColors(Theme.key_dialogBackground, Theme.key_dialogBackgroundGray);
        android.graphics.Rect bounds = getBounds();
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, placeholderPaint);

        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = Math.abs(lastUpdateTime - newUpdateTime);
        if (dt > 17) {
            dt = 16;
        }
        lastUpdateTime = newUpdateTime;
        totalTranslation += dt * gradientWidth / 1800.0f;
        while (totalTranslation >= gradientWidth * 2) {
            totalTranslation -= gradientWidth * 2;
        }
        placeholderMatrix.setTranslate(totalTranslation, 0);
        placeholderGradient.setLocalMatrix(placeholderMatrix);
        parentView.invalidate();
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
