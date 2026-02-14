package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

public class BlurredBackgroundWithFadeDrawable extends Drawable {
    private final Paint maskFadeGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurredBackgroundDrawable drawable;

    private final Matrix matrix = new Matrix();
    private Shader shader;

    private final Matrix matrixTmp = new Matrix();

    private Shader gradientShader;
    private BitmapShader bitmapShader;
    private Shader composeShader;
    private final Matrix bitmapMatrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap lastBitmap;

    private int fadeHeight;
    private boolean opacity;

    public BlurredBackgroundWithFadeDrawable(BlurredBackgroundDrawable drawable) {
        this.drawable = drawable;
        maskFadeGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        bitmapPaint.setFilterBitmap(true);
        setFadeHeight(dp(40), false);
    }

    public void setFadeHeight(int fadeHeight, boolean opacity) {
        this.fadeHeight = fadeHeight;
        this.opacity = opacity;
        maskFadeGradientPaint.setShader(shader = createGradient(Color.BLACK, opacity));
        colorStaticPaint.setShader(null);

        matrix.reset();
        matrix.setScale(1, fadeHeight);
        if (fadeHeight < 0) {
            matrix.postTranslate(0, -fadeHeight);
        }
        shader.setLocalMatrix(matrix);
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
            if (colorStaticLast != color ||gradientShader == null) {
                gradientShader = createGradient(color, opacity);
                colorStaticLast = color;
                colorStaticPaint.setShader(gradientShader);
            }

            int offset = 0;
            if (fadeHeight < 0) {
                offset = bounds.height() + fadeHeight;
            }

            matrixTmp.set(matrix);
            matrixTmp.postTranslate(bounds.left, bounds.top + offset);
            gradientShader.setLocalMatrix(matrixTmp);

            canvas.drawRect(bounds, colorStaticPaint);
            return;
        }

        if (source instanceof BlurredBackgroundSourceBitmap) {
            // fast way - just draw gradient

            final BlurredBackgroundSourceBitmap s = (BlurredBackgroundSourceBitmap) source;
            final Bitmap bitmap = s.getBitmap();
            if (bitmap == null) {
                return;
            }

            final int color = Color.BLACK;
            boolean changed = false;
            if (colorStaticLast != color || gradientShader == null) {
                gradientShader = createGradient(color, opacity);
                colorStaticLast = color;
                changed = true;
            }

            if (bitmapShader == null || lastBitmap != bitmap) {
                lastBitmap = bitmap;
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                }
                changed = true;
            }

            if (changed || composeShader == null) {
                composeShader = new ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN);
                bitmapPaint.setShader(composeShader);
            }

            int offset = 0;
            if (fadeHeight < 0) {
                offset = bounds.height() + fadeHeight;
            }

            matrixTmp.set(matrix);
            matrixTmp.postTranslate(bounds.left, bounds.top + offset);
            gradientShader.setLocalMatrix(matrixTmp);

            bitmapMatrix.set(s.getMatrix());
            bitmapMatrix.postTranslate(-drawable.getSourceOffsetX(), -drawable.getSourceOffsetY());
            bitmapShader.setLocalMatrix(bitmapMatrix);

            canvas.drawRect(bounds, bitmapPaint);
            return;
        }

        if (source instanceof BlurredBackgroundSourceRenderNode) {
            return;
        }
        final int save = canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, null);
        int offset = 0;
        if (fadeHeight < 0) {
            offset = bounds.height() + fadeHeight;
        }

        drawable.draw(canvas);
        canvas.translate(bounds.left, bounds.top + offset);
        canvas.drawRect(0, - offset, bounds.width(), bounds.height() - offset, maskFadeGradientPaint);
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

    private static LinearGradient createGradient(int color, boolean opacity) {
        final int alpha = Color.alpha(color);

        if (opacity) {
            return new LinearGradient(0, 0, 0, 1, new int[]{
                ColorUtils.setAlphaComponent(color, 0),
                ColorUtils.setAlphaComponent(color, 0x60 * alpha / 255),
                ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 255),
                ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            }, null, Shader.TileMode.CLAMP);
        }

        return new LinearGradient(0, 0, 0, 1, new int[]{
            ColorUtils.setAlphaComponent(color, 0),
            ColorUtils.setAlphaComponent(color, 0x60 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xFF * alpha / 255),
        }, null, Shader.TileMode.CLAMP);
    }




    public static void fillAlphaGradientColors(
        int[] outColors,
        int baseColor,
        int alphaStart,
        int alphaEnd,
        Interpolator interpolator
    ) {
        if (outColors == null || outColors.length == 0) {
            return;
        }

        final int count = outColors.length;
        if (count == 1) {
            outColors[0] = ColorUtils.setAlphaComponent(baseColor, (alphaStart + alphaEnd) / 2);
            return;
        }

        for (int i = 0; i < count; i++) {
            final float t = (float) i / (count - 1);
            final float it = interpolator != null ? interpolator.getInterpolation(t) : t;

            final int alpha = MathUtils.clamp(lerp(alphaStart, alphaEnd, it), 0, 255);

            outColors[i] = ColorUtils.setAlphaComponent(baseColor, alpha);
        }
    }
}
