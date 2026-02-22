package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.utils.ColorShader;

import java.lang.ref.WeakReference;

@RequiresApi(api = Build.VERSION_CODES.P)
public class MotionBackgroundPaint {
    private final Paint paint = new Paint();

    private final BitmapShaderState gradientShader = new BitmapShaderState(Shader.TileMode.CLAMP);
    private final BitmapShaderState patternShader = new BitmapShaderState(Shader.TileMode.REPEAT);
    private ColorShader colorShader;
    private int colorShaderLastColor;

    private ColorShader alphaShader;
    private int alphaShaderLastAlpha;

    public MotionBackgroundPaint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.setBlendMode(BlendMode.SRC);
        } else {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }
        paint.setFilterBitmap(true);
    }

    public Paint getPaint(@NonNull Bitmap gradient, @NonNull Bitmap pattern, int patternColor, int patternAlpha, int intensity) {
        final int colorForShader, alphaForShader;
        if (intensity >= 0) {
            colorForShader = ColorUtils.setAlphaComponent(patternColor, Color.alpha(patternColor) * patternAlpha * intensity / 25500);
            alphaForShader = 255;
        } else {
            colorForShader = Color.BLACK;
            alphaForShader = patternAlpha * -intensity / 100;
        }

        boolean changed = false;

        if (colorShaderLastColor != colorForShader || colorShader == null) {
            colorShaderLastColor = colorForShader;
            colorShader = new ColorShader(colorForShader);
            changed = true;
        }
        if (alphaShaderLastAlpha != alphaForShader || alphaShader == null) {
            alphaShaderLastAlpha = alphaForShader;
            alphaShader = new ColorShader(ColorUtils.setAlphaComponent(Color.WHITE, alphaForShader));
            changed = true;
        }

        changed |= gradientShader.setup(gradient);
        changed |= patternShader.setup(pattern);

        if (changed) {
            if (intensity >= 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    paint.setShader(new ComposeShader(
                        gradientShader.shader,
                        new ComposeShader(
                            colorShader,
                            patternShader.shader,
                            PorterDuff.Mode.DST_IN
                        ), BlendMode.SOFT_LIGHT));
                } else {
                    paint.setShader(new ComposeShader(
                        gradientShader.shader,
                        new ComposeShader(
                                colorShader,
                                patternShader.shader,
                                PorterDuff.Mode.DST_IN
                        ), PorterDuff.Mode.SRC_OVER));
                }
            } else {
                paint.setShader(new ComposeShader(
                    colorShader,
                    new ComposeShader(
                        new ComposeShader(
                            gradientShader.shader,
                            patternShader.shader,
                            PorterDuff.Mode.DST_IN
                        ),
                        alphaShader,
                        PorterDuff.Mode.MULTIPLY
                    ), PorterDuff.Mode.SRC_OVER));
            }
        }

        return paint;
    }

    public void applyGradientMatrix(RectF bounds) {
        tmpRectF.set(0, 0, gradientShader.width, gradientShader.height);
        tmpMatrix.setRectToRect(tmpRectF, bounds, Matrix.ScaleToFit.FILL);
        gradientShader.shader.setLocalMatrix(tmpMatrix);
    }

    public void applyPatternMatrix(RectF bounds) {
        tmpRectF.set(0, 0, patternShader.width, patternShader.height);
        tmpMatrix.setRectToRect(tmpRectF, bounds, Matrix.ScaleToFit.FILL);
        patternShader.shader.setLocalMatrix(tmpMatrix);
    }

    public void applyPatternMatrix(Matrix matrix) {
        patternShader.shader.setLocalMatrix(matrix);
    }



    private final Matrix tmpMatrix = new Matrix();
    private final RectF tmpRectF = new RectF();

    private static class BitmapShaderState {
        final Shader.TileMode tileMode;

        BitmapShader shader;
        WeakReference<Bitmap> bitmap;
        int width, height;

        public BitmapShaderState(Shader.TileMode tileMode) {
            this.tileMode = tileMode;
        }

        public boolean setup(Bitmap b) {
            width = b.getWidth();
            height = b.getHeight();

            if (bitmap != null && bitmap.get() == b) {
                return false;
            }

            bitmap = new WeakReference<>(b);
            shader = new BitmapShader(b, tileMode, tileMode);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }

            return true;
        }
    }
}
