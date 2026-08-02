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
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.ColorShader;
import org.telegram.ui.Components.blur3.utils.BitmapChangeTracker;
import org.telegram.ui.Components.blur3.utils.BitmapMemoizedMetadata;

import java.lang.ref.WeakReference;
import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.P)
public class MotionBackgroundPaint {
    private static final int MODE_POS = 1;
    private static final int MODE_NEG = 2;

    private final BitmapMemoizedMetadata<Bitmap> patterAlphaBitmapMemo = new BitmapMemoizedMetadata<>(MotionBackgroundPaint::getAlphaChannel);
    private final BitmapMemoizedSoftLight gradientSoftLightBitmapMemo = new BitmapMemoizedSoftLight();

    private final ShaderImpl shaderImpl = new ShaderImpl();
    private final @Nullable AgslImpl agslImpl;

    private int gradientWidth, gradientHeight;
    private int patternWidth, patternHeight;

    public MotionBackgroundPaint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            agslImpl = new AgslImpl();
        } else {
            agslImpl = null;
        }
    }

    public Paint getPaint(@NonNull Bitmap gradient, @NonNull Bitmap pattern, final int patternColor, int patternAlpha, int intensity, boolean isHardwareAccelerated) {
        final Bitmap patternAlphaBitmap = patterAlphaBitmapMemo.get(pattern);
        final Bitmap gradientSoftLight;
        if (intensity >= 0) {
            final int colorForShader = ColorUtils.setAlphaComponent(patternColor, Color.alpha(patternColor) * patternAlpha * intensity / 25500);
            gradientSoftLight = gradientSoftLightBitmapMemo.get(gradient, colorForShader);
        } else {
            gradientSoftLight = null;
        }

        gradientWidth = gradient.getWidth();
        gradientHeight = gradient.getHeight();
        patternWidth = patternAlphaBitmap.getWidth();
        patternHeight = patternAlphaBitmap.getHeight();

        if (agslImpl != null && isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return agslImpl.getPaint(gradient, patternAlphaBitmap, gradientSoftLight, patternAlpha, intensity);
        } else {
            return shaderImpl.getPaint(gradient, patternAlphaBitmap, gradientSoftLight, patternAlpha, intensity);
        }
    }



    /* Implementations */

    private static class ShaderImpl {
        private final Paint paint = new Paint();

        private final BitmapShaderState gradientShader = new BitmapShaderState(Shader.TileMode.CLAMP);
        private final BitmapShaderState gradientSoftLightShader = new BitmapShaderState(Shader.TileMode.CLAMP);
        private final BitmapShaderState patternShader = new BitmapShaderState(Shader.TileMode.REPEAT);
        private final ColorShaderState colorShader = new ColorShaderState();
        private final ColorShaderState alphaShader = new ColorShaderState();

        private final float[] tmpOut = new float[4];

        private int lastMode;

        ShaderImpl() {
            paint.setFilterBitmap(true);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }

        Paint getPaint(@NonNull Bitmap gradient, @NonNull Bitmap patternAlphaBitmap, @Nullable Bitmap gradientSoftLight, int patternAlpha, int intensity) {
            boolean changed = false;
            changed |= gradientShader.setup(gradient);
            changed |= patternShader.setup(patternAlphaBitmap);

            if (intensity >= 0) {
                changed |= gradientSoftLightShader.setup(gradientSoftLight);

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

        void applyGradientMatrix(Matrix matrix) {
            gradientShader.setLocalMatrix(matrix);
            gradientSoftLightShader.setLocalMatrix(matrix);
        }

        void applyPatternMatrix(Matrix matrix) {
            matrixToScaleTranslate(matrix, tmpOut);
            patternShader.setLocalMatrix(matrix);
            patternShader.setUseNearestInterpolation(isOne(tmpOut[0]) && isOne(tmpOut[1]));
        }

        private static class ColorShaderState {
            ColorShader shader;
            int color;

            boolean setup(int color) {
                if (shader == null || this.color != color) {
                    this.color = color;
                    shader = new ColorShader(color);
                    return true;
                }
                return false;
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class AgslImpl {
        private final Paint paint = new Paint();

        private final BitmapShaderState gradientShader = new BitmapShaderState(Shader.TileMode.CLAMP);
        private final BitmapShaderState gradientSoftLightShader = new BitmapShaderState(Shader.TileMode.CLAMP);
        private final BitmapShaderState patternShader = new BitmapShaderState(Shader.TileMode.REPEAT);

        private final RuntimeShaderState runtimeShaderPositive = new RuntimeShaderState(R.raw.wallpaper_pos_intensity);
        private final RuntimeShaderState runtimeShaderNegative = new RuntimeShaderState(R.raw.wallpaper_neg_intensity);

        private final float[] tmpOut = new float[4];

        private int lastMode;
        private float lastIntensity;

        AgslImpl() {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }

        Paint getPaint(@NonNull Bitmap gradient, @NonNull Bitmap patternAlphaBitmap, @Nullable Bitmap gradientSoftLight, int patternAlpha, int intensity) {
            boolean changed = false;
            changed |= gradientShader.setup(gradient);
            changed |= patternShader.setup(patternAlphaBitmap);

            if (intensity >= 0) {
                changed |= gradientSoftLightShader.setup(gradientSoftLight);

                if (changed || lastMode != MODE_POS) {
                    lastMode = MODE_POS;
                    runtimeShaderPositive.shader.setInputBuffer("shaderPattern", patternShader.shader);
                    runtimeShaderPositive.shader.setInputBuffer("shaderGradient", gradientShader.shader);
                    runtimeShaderPositive.shader.setInputBuffer("shaderGradientSoftLight", gradientSoftLightShader.shader);
                    runtimeShaderPositive.setMatrixUniforms();
                    paint.setShader(runtimeShaderPositive.shader);
                }
            } else {
                final float normalizedIntensity = MathUtils.clamp(patternAlpha * (-intensity) / 25500f, 0, 1);
                if (changed || lastIntensity != normalizedIntensity || lastMode != MODE_NEG) {
                    lastMode = MODE_NEG;
                    lastIntensity = normalizedIntensity;
                    runtimeShaderNegative.shader.setInputBuffer("shaderPattern", patternShader.shader);
                    runtimeShaderNegative.shader.setInputBuffer("shaderGradient", gradientShader.shader);
                    runtimeShaderNegative.shader.setFloatUniform("intensity", normalizedIntensity);
                    runtimeShaderNegative.setMatrixUniforms();
                    paint.setShader(runtimeShaderNegative.shader);
                }
            }

            return paint;
        }

        void applyGradientMatrix(Matrix matrix) {
            matrixToScaleTranslate(matrix, tmpOut);
            runtimeShaderPositive.setMiniMatrixGradient(tmpOut);
            runtimeShaderNegative.setMiniMatrixGradient(tmpOut);
        }

        void applyPatternMatrix(Matrix matrix) {
            matrixToScaleTranslate(matrix, tmpOut);
            patternShader.setUseNearestInterpolation(isOne(tmpOut[0]) && isOne(tmpOut[1]));
            runtimeShaderPositive.setMiniMatrixPattern(tmpOut);
            runtimeShaderNegative.setMiniMatrixPattern(tmpOut);
        }

        private static class RuntimeShaderState {
            private final RuntimeShader shader;
            private final float[] transformGradient = new float[] { 1, 1, 0, 0 };
            private final float[] transformPattern = new float[] { 1, 1, 0, 0 };

            RuntimeShaderState(@RawRes int res) {
                shader = new RuntimeShader(AndroidUtilities.readRes(res));
            }

            void setMiniMatrixGradient(float[] miniMatrix) {
                if (!Arrays.equals(miniMatrix, transformGradient)) {
                    System.arraycopy(miniMatrix, 0, transformGradient, 0, 4);
                    setMatrixUniformGradient();
                }
            }

            void setMiniMatrixPattern(float[] miniMatrix) {
                if (!Arrays.equals(miniMatrix, transformPattern)) {
                    System.arraycopy(miniMatrix, 0, transformPattern, 0, 4);
                    setMatrixUniformPattern();
                }
            }

            private void setMatrixUniformGradient() {
                shader.setFloatUniform("transformGradient", transformGradient);
            }

            private void setMatrixUniformPattern() {
                shader.setFloatUniform("transformPattern", transformPattern);
            }

            private void setMatrixUniforms() {
                setMatrixUniformGradient();
                setMatrixUniformPattern();
            }
        }
    }



    /* Local Matrix */

    private final Matrix tmpMatrix = new Matrix();
    private final RectF tmpRectF = new RectF();

    public void applyGradientMatrix(RectF bounds) {
        tmpRectF.set(0, 0, gradientWidth, gradientHeight);
        tmpMatrix.setRectToRect(tmpRectF, bounds, Matrix.ScaleToFit.FILL);

        shaderImpl.applyGradientMatrix(tmpMatrix);
        if (agslImpl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            agslImpl.applyGradientMatrix(tmpMatrix);
        }
    }

    public void applyPatternMatrix(RectF bounds) {
        tmpRectF.set(0, 0, patternWidth, patternHeight);
        tmpMatrix.setRectToRect(tmpRectF, bounds, Matrix.ScaleToFit.FILL);
        applyPatternMatrix(tmpMatrix);
    }

    public void applyPatternMatrix(Matrix matrix) {
        shaderImpl.applyPatternMatrix(matrix);
        if (agslImpl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            agslImpl.applyPatternMatrix(matrix);
        }
    }



    /* Utils */

    private static class BitmapShaderState {
        final Shader.TileMode tileMode;
        final Matrix localMatrix = new Matrix();
        boolean useNearestInterpolation;

        BitmapShader shader;
        WeakReference<Bitmap> bitmap;

        BitmapShaderState(Shader.TileMode tileMode) {
            this.tileMode = tileMode;
        }

        void setLocalMatrix(Matrix m) {
            localMatrix.set(m);
            if (shader != null) {
                shader.setLocalMatrix(m);
            }
        }

        boolean setup(Bitmap b) {
            if (bitmap != null && bitmap.get() == b) {
                return false;
            }

            bitmap = new WeakReference<>(b);
            shader = new BitmapShader(b, tileMode, tileMode);
            shader.setLocalMatrix(localMatrix);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.setFilterMode(useNearestInterpolation ?
                    BitmapShader.FILTER_MODE_NEAREST :
                    BitmapShader.FILTER_MODE_LINEAR);
            }

            return true;
        }

        void setUseNearestInterpolation(boolean useNearestInterpolation) {
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
        private final BitmapChangeTracker lastBitmap = new BitmapChangeTracker();
        private int lastColor;
        private Bitmap memoized;

        public Bitmap get(@NonNull Bitmap bitmap, int color) {
            final boolean changed = lastBitmap.isInvalidated(bitmap)
                || color != lastColor || memoized == null;

            if (changed) {
                if (memoized == null || memoized.getWidth() != bitmap.getWidth() || memoized.getHeight() != bitmap.getHeight()) {
                    memoized = Bitmap.createBitmap(bitmap);
                }
                Utilities.applySoftLight(bitmap, memoized, color);

                lastBitmap.set(bitmap);
                lastColor = color;
            }

            return memoized;
        }
    }

    private static Bitmap getAlphaChannel(Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.ALPHA_8) {
            return bitmap;
        }
        return bitmap.extractAlpha();
    }

    private static boolean isOne(float x) {
        return Math.abs(x - 1.0f) <= 1e-4f;
    }

    private final static float[] tmpPts = new float[4];
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