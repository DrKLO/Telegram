package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.ColorShader;
import org.telegram.ui.Components.blur3.utils.BitmapMemoizedMetadata;

import java.lang.ref.WeakReference;
import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.P)
public class MotionBackgroundPaint {
    private static final int MODE_POS = 1;
    private static final int MODE_NEG = 2;

    private final Paint paint = new Paint();
    private final Paint paintHwAgsl = new Paint();

    private final BitmapShaderState gradientShader = new BitmapShaderState(Shader.TileMode.CLAMP);
    private final BitmapShaderState gradientSoftLightShader = new BitmapShaderState(Shader.TileMode.CLAMP);
    private final BitmapShaderState patternShader = new BitmapShaderState(Shader.TileMode.REPEAT);
    private final ColorShaderState colorShader = new ColorShaderState();
    private final ColorShaderState alphaShader = new ColorShaderState();

    private final BitmapMemoizedMetadata<Bitmap> patterAlphaBitmapMemo = new BitmapMemoizedMetadata<>(MotionBackgroundPaint::getAlphaChannel);
    private final BitmapMemoizedSoftLight gradientSoftLightBitmapMemo = new BitmapMemoizedSoftLight();

    private int lastMode;
    private int lastModeHwAgsl;
    private float lastHwIntensity;

    private final RuntimeShaderState runtimeShaderNegative;
    private final RuntimeShaderState runtimeShaderPositive;

    public MotionBackgroundPaint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeShaderPositive = new RuntimeShaderState(R.raw.wallpaper_pos_intensity);
            runtimeShaderNegative = new RuntimeShaderState(R.raw.wallpaper_neg_intensity);
        } else {
            runtimeShaderPositive = runtimeShaderNegative = null;
        }

        final PorterDuffXfermode xRef = new PorterDuffXfermode(PorterDuff.Mode.SRC);
        paint.setFilterBitmap(true);
        paint.setXfermode(xRef);
        paintHwAgsl.setXfermode(xRef);
    }

    public Paint getPaint(@NonNull Bitmap gradient, @NonNull Bitmap pattern, final int patternColor, int patternAlpha, int intensity, boolean isHardwareAccelerated) {
        if (isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getPaintHwAgsl(gradient, pattern, patternColor, patternAlpha, intensity);
        } else {
            return getPaintSw(gradient, pattern, patternColor, patternAlpha, intensity);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private Paint getPaintHwAgsl(@NonNull Bitmap gradient, @NonNull Bitmap pattern,
                                 final int patternColor, int patternAlpha, int intensity) {
        boolean changed = false;
        changed |= gradientShader.setup(gradient);
        changed |= patternShader.setup(patterAlphaBitmapMemo.get(pattern));

        if (intensity >= 0) {
            final int colorForShader = ColorUtils.setAlphaComponent(patternColor, Color.alpha(patternColor) * patternAlpha * intensity / 25500);
            changed |= gradientSoftLightShader.setup(gradientSoftLightBitmapMemo.get(gradient, colorForShader));

            if (changed || lastModeHwAgsl != MODE_POS) {
                lastModeHwAgsl = MODE_POS;
                runtimeShaderPositive.shader.setInputShader("shaderPattern", patternShader.shader);
                runtimeShaderPositive.shader.setInputShader("shaderGradient", gradientShader.shader);
                runtimeShaderPositive.shader.setInputShader("shaderGradientSoftLight", gradientSoftLightShader.shader);
                runtimeShaderPositive.shader.setFloatUniform("transformGradient", runtimeShaderPositive.transformGradient);
                runtimeShaderPositive.shader.setFloatUniform("transformPattern", runtimeShaderPositive.transformPattern);
                paintHwAgsl.setShader(runtimeShaderPositive.shader);
            }
        } else {
            final float normalizedIntensity = MathUtils.clamp(patternAlpha * (-intensity) / 25500f, 0, 1);
            if (changed || lastHwIntensity != normalizedIntensity || lastModeHwAgsl != MODE_NEG) {
                lastModeHwAgsl = MODE_NEG;
                lastHwIntensity = normalizedIntensity;
                runtimeShaderNegative.shader.setInputShader("shaderPattern", patternShader.shader);
                runtimeShaderNegative.shader.setInputShader("shaderGradient", gradientShader.shader);
                runtimeShaderNegative.shader.setFloatUniform("intensity", normalizedIntensity);
                runtimeShaderNegative.shader.setFloatUniform("transformGradient", runtimeShaderNegative.transformGradient);
                runtimeShaderNegative.shader.setFloatUniform("transformPattern", runtimeShaderNegative.transformPattern);
                paintHwAgsl.setShader(runtimeShaderNegative.shader);
            }
        }

        return paintHwAgsl;
    }

    private Paint getPaintSw(@NonNull Bitmap gradient, @NonNull Bitmap pattern, final int patternColor, int patternAlpha, int intensity) {
        boolean changed = false;
        changed |= gradientShader.setup(gradient);
        changed |= patternShader.setup(patterAlphaBitmapMemo.get(pattern));

        if (intensity >= 0) {
            final int colorForShader = ColorUtils.setAlphaComponent(patternColor, Color.alpha(patternColor) * patternAlpha * intensity / 25500);
            changed |= gradientSoftLightShader.setup(gradientSoftLightBitmapMemo.get(gradient, colorForShader));

            if (changed || lastMode != MODE_POS) {
                lastMode = MODE_POS;
                paint.setShader(new ComposeShader(
                        gradientShader.shader,
                        new ComposeShader(
                                gradientSoftLightShader.shader,
                                patternShader.shader,
                                PorterDuff.Mode.DST_IN
                        ), PorterDuff.Mode.SRC_OVER));
            }
        } else {
            final int alphaForShader = patternAlpha * -intensity / 100;
            changed |= alphaShader.setup(ColorUtils.setAlphaComponent(Color.WHITE, alphaForShader));
            changed |= colorShader.setup(Color.BLACK);

            if (changed || lastMode != MODE_NEG) {
                lastMode = MODE_NEG;
                paint.setShader(new ComposeShader(
                        colorShader.shader,
                        new ComposeShader(
                                new ComposeShader(
                                        gradientShader.shader,
                                        patternShader.shader,
                                        PorterDuff.Mode.DST_IN
                                ),
                                alphaShader.shader,
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
        if (gradientSoftLightShader.shader != null) {
            gradientSoftLightShader.shader.setLocalMatrix(tmpMatrix);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            matrixToScaleTranslate(tmpMatrix, tmpOut);
            runtimeShaderPositive.setMiniMatrixGradient(tmpOut);
            runtimeShaderNegative.setMiniMatrixGradient(tmpOut);
        }
    }

    public void applyPatternMatrix(RectF bounds) {
        tmpRectF.set(0, 0, patternShader.width, patternShader.height);
        tmpMatrix.setRectToRect(tmpRectF, bounds, Matrix.ScaleToFit.FILL);
        applyPatternMatrix(tmpMatrix);
    }

    public void applyPatternMatrix(Matrix matrix) {
        matrixToScaleTranslate(matrix, tmpOut);

        patternShader.shader.setLocalMatrix(matrix);
        patternShader.setUseNearestInterpolation(isOne(tmpOut[0]) && isOne(tmpOut[1]));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeShaderPositive.setMiniMatrixPattern(tmpOut);
            runtimeShaderNegative.setMiniMatrixPattern(tmpOut);
        }
    }

    private static boolean isOne(float x) {
        return Math.abs(x - 1.0f) <= 1e-4f;
    }

    private final Matrix tmpMatrix = new Matrix();
    private final RectF tmpRectF = new RectF();

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class RuntimeShaderState {
        private final RuntimeShader shader;
        private final float[] transformGradient = new float[] { 1, 1, 0, 0 };
        private final float[] transformPattern = new float[] { 1, 1, 0, 0 };

        public RuntimeShaderState(@RawRes int res) {
            shader = new RuntimeShader(AndroidUtilities.readRes(res));
        }

        public void setMiniMatrixGradient(float[] miniMatrix) {
            if (!Arrays.equals(miniMatrix, transformGradient)) {
                System.arraycopy(miniMatrix, 0, transformGradient, 0, 4);
                shader.setFloatUniform("transformGradient", transformGradient);
            }
        }

        public void setMiniMatrixPattern(float[] miniMatrix) {
            if (!Arrays.equals(miniMatrix, transformPattern)) {
                System.arraycopy(miniMatrix, 0, transformPattern, 0, 4);
                shader.setFloatUniform("transformPattern", transformPattern);
            }
        }
    }

    private static class ColorShaderState {
        ColorShader shader;
        int color;

        public boolean setup(int color) {
            if (shader == null || this.color != color) {
                this.color = color;
                shader = new ColorShader(color);
                return true;
            }
            return false;
        }
    }

    private static class BitmapShaderState {
        final Shader.TileMode tileMode;
        boolean useNearestInterpolation;

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
                shader.setFilterMode(useNearestInterpolation ?
                    BitmapShader.FILTER_MODE_NEAREST :
                    BitmapShader.FILTER_MODE_LINEAR);
            }

            return true;
        }

        public void setUseNearestInterpolation(boolean useNearestInterpolation) {
            if (this.useNearestInterpolation != useNearestInterpolation) {
                this.useNearestInterpolation = useNearestInterpolation;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    shader.setFilterMode(useNearestInterpolation ?
                        BitmapShader.FILTER_MODE_NEAREST :
                        BitmapShader.FILTER_MODE_LINEAR);
                }
            }
        }
    }

    private static class BitmapMemoizedSoftLight {
        private WeakReference<Bitmap> ref;
        private long generationId;
        private int lastColor;
        private Bitmap memoized;

        public Bitmap get(@NonNull Bitmap bitmap, int color) {
            final Bitmap oldBitmap = ref != null ? ref.get() : null;
            final long generationIdToSet = !bitmap.isRecycled() ? bitmap.getGenerationId() : 0;

            if (oldBitmap == bitmap && generationIdToSet == generationId && color == lastColor) {
                return memoized;
            }

            ref = new WeakReference<>(bitmap);
            generationId = generationIdToSet;
            lastColor = color;

            if (memoized == null || memoized.getWidth() != bitmap.getWidth() || memoized.getHeight() != bitmap.getHeight()) {
                memoized = Bitmap.createBitmap(bitmap);
            }
            Utilities.applySoftLight(bitmap, memoized, color);

            return memoized;
        }
    }

    private static Bitmap getAlphaChannel(Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.ALPHA_8) {
            return bitmap;
        }
        return bitmap.extractAlpha();
    }

    private final static float[] tmpPts = new float[4];
    private final static float[] tmpOut = new float[4];
    private final static Matrix tmpInverse = new Matrix();

    private static void matrixToScaleTranslate(Matrix matrix, float[] out) {
        matrix.invert(tmpInverse);

        tmpPts[0] = 0;
        tmpPts[1] = 0;
        tmpPts[2] = 1;
        tmpPts[3] = 1;

        tmpInverse.mapPoints(tmpPts);
        out[0] = tmpPts[2] - tmpPts[0]; // scaleX
        out[1] = tmpPts[3] - tmpPts[1]; // scaleY
        out[2] = tmpPts[0];              // translateX
        out[3] = tmpPts[1];              // translateY
    }
}