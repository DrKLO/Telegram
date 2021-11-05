package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.lang.ref.WeakReference;

public class MotionBackgroundDrawable extends Drawable {

    private final static int ANIMATION_CACHE_BITMAPS_COUNT = 3;

    private static final boolean useLegacyBitmap = Build.VERSION.SDK_INT < 28;
    private static final boolean useSoftLight = Build.VERSION.SDK_INT >= 29;
    private static boolean errorWhileGenerateLegacyBitmap = false;
    private static float legacyBitmapScale = 0.7f;

    private int[] colors = new int[]{
            0xff426D57,
            0xffF7E48B,
            0xff87A284,
            0xffFDF6CA
    };

    private long lastUpdateTime;
    private WeakReference<View> parentView;

    private final CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0.33, 0.0, 0.0, 1.0);

    private int translationY;

    private boolean isPreview;

    private float posAnimationProgress = 1.0f;
    private int phase;

    private RectF rect = new RectF();
    private Bitmap currentBitmap;
    private Bitmap gradientFromBitmap;
    private Bitmap[] gradientToBitmap = new Bitmap[ANIMATION_CACHE_BITMAPS_COUNT];
    private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Paint paint2 = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Paint paint3 = new Paint();
    private int intensity = 100;
    private Canvas gradientCanvas;
    private Canvas gradientFromCanvas;

    private boolean postInvalidateParent;

    private Bitmap patternBitmap;
    private BitmapShader bitmapShader;
    private BitmapShader gradientShader;
    private Matrix matrix;

    private boolean fastAnimation;

    private Canvas legacyCanvas;
    private Bitmap legacyBitmap;
    private Canvas legacyCanvas2;
    private Bitmap legacyBitmap2;
    private boolean invalidateLegacy;

    private boolean rotationBack;

    private boolean rotatingPreview;

    private Runnable updateAnimationRunnable = this::updateAnimation;

    private android.graphics.Rect patternBounds = new android.graphics.Rect();

    private ColorFilter patternColorFilter;
    private int roundRadius;
    private float patternAlpha = 1f;
    private int alpha = 255;

    private ColorFilter legacyBitmapColorFilter;
    private int legacyBitmapColor;

    public MotionBackgroundDrawable() {
        super();
        init();
    }

    public MotionBackgroundDrawable(int c1, int c2, int c3, int c4, boolean preview) {
        super();
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        isPreview = preview;
        init();
    }

    @SuppressLint("NewApi")
    private void init() {
        currentBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < ANIMATION_CACHE_BITMAPS_COUNT; i++) {
            gradientToBitmap[i] = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
        }
        gradientCanvas = new Canvas(currentBitmap);

        gradientFromBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
        gradientFromCanvas = new Canvas(gradientFromBitmap);

        Utilities.generateGradient(currentBitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
        if (useSoftLight) {
            paint2.setBlendMode(BlendMode.SOFT_LIGHT);
        }
    }

    public void setRoundRadius(int rad) {
        roundRadius = rad;
        matrix = new Matrix();
        bitmapShader = new BitmapShader(currentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(bitmapShader);
        invalidateParent();
    }

    public BitmapShader getBitmapShader() {
        return bitmapShader;
    }

    public Bitmap getBitmap() {
        return currentBitmap;
    }

    public int getIntensity() {
        return intensity;
    }

    public static boolean isDark(int color1, int color2, int color3, int color4) {
        int averageColor = AndroidUtilities.getAverageColor(color1, color2);
        if (color3 != 0) {
            averageColor = AndroidUtilities.getAverageColor(averageColor, color3);
        }
        if (color4 != 0) {
            averageColor = AndroidUtilities.getAverageColor(averageColor, color4);
        }
        float[] hsb = AndroidUtilities.RGBtoHSB(Color.red(averageColor), Color.green(averageColor), Color.blue(averageColor));
        return hsb[2] < 0.3f;
    }

    @Override
    public void setBounds(Rect bounds) {
        super.setBounds(bounds);
        patternBounds.set(bounds);
    }

    public void setPatternBounds(int left, int top, int right, int bottom) {
        patternBounds.set(left, top, right, bottom);
    }

    public static int getPatternColor(int color1, int color2, int color3, int color4) {
        if (isDark(color1, color2, color3, color4)) {
            return !useSoftLight ? 0x7fffffff : 0xffffffff;
        } else {
            if (!useSoftLight) {
                int averageColor = AndroidUtilities.getAverageColor(color3, AndroidUtilities.getAverageColor(color1, color2));
                if (color4 != 0) {
                    averageColor = AndroidUtilities.getAverageColor(color4, averageColor);
                }
                return (AndroidUtilities.getPatternColor(averageColor, true) & 0x00ffffff) | 0x64000000;
            } else {
                return 0xff000000;
            }
        }
    }

    public int getPatternColor() {
        return getPatternColor(colors[0], colors[1], colors[2], colors[3]);
    }

    public int getPhase() {
        return phase;
    }

    public void setPostInvalidateParent(boolean value) {
        postInvalidateParent = value;
    }

    public void rotatePreview(boolean back) {
        if (posAnimationProgress < 1.0f) {
            return;
        }
        rotatingPreview = true;
        posAnimationProgress = 0.0f;
        rotationBack = back;
        invalidateParent();
    }

    public void setPhase(int value) {
        phase = value;
        if (phase < 0) {
            phase = 0;
        } else if (phase > 7) {
            phase = 7;
        }
        Utilities.generateGradient(currentBitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
    }

    public void switchToNextPosition() {
        switchToNextPosition(false);
    }

    public void switchToNextPosition(boolean fast) {
        if (posAnimationProgress < 1.0f) {
            return;
        }
        rotatingPreview = false;
        rotationBack = false;
        fastAnimation = fast;
        posAnimationProgress = 0.0f;
        phase--;
        if (phase < 0) {
            phase = 7;
        }
        invalidateParent();
        gradientFromCanvas.drawBitmap(currentBitmap, 0, 0, null);
        generateNextGradient();
    }

    private void generateNextGradient() {
        if (useLegacyBitmap && intensity < 0) {
            try {
                if (legacyBitmap != null) {
                    if (legacyBitmap2 == null || legacyBitmap2.getHeight() != legacyBitmap.getHeight() || legacyBitmap2.getWidth() != legacyBitmap.getWidth()) {
                        if (legacyBitmap2 != null) {
                            legacyBitmap2.recycle();
                        }
                        legacyBitmap2 = Bitmap.createBitmap(legacyBitmap.getWidth(), legacyBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        legacyCanvas2 = new Canvas(legacyBitmap2);
                    } else {
                        legacyBitmap2.eraseColor(Color.TRANSPARENT);
                    }
                    legacyCanvas2.drawBitmap(legacyBitmap, 0, 0, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (legacyBitmap2 != null) {
                    legacyBitmap2.recycle();
                    legacyBitmap2 = null;
                }
            }

            Utilities.generateGradient(currentBitmap, true, phase, 1f, currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
            invalidateLegacy = true;
        }
        for (int i = 0; i < ANIMATION_CACHE_BITMAPS_COUNT; i++) {
            float p = (i + 1) / (float) ANIMATION_CACHE_BITMAPS_COUNT;
            Utilities.generateGradient(gradientToBitmap[i], true, phase, p, currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
        }
    }

    public void switchToPrevPosition(boolean fast) {
        if (posAnimationProgress < 1.0f) {
            return;
        }
        rotatingPreview = false;
        fastAnimation = fast;
        rotationBack = true;
        posAnimationProgress = 0.0f;
        invalidateParent();
        Utilities.generateGradient(gradientFromBitmap, true, phase, 0, currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
        generateNextGradient();
    }

    public int[] getColors() {
        return colors;
    }

    public void setParentView(View view) {
        parentView = new WeakReference<>(view);
    }

    public void setColors(int c1, int c2, int c3, int c4) {
        setColors(c1, c2, c3, c4, true);
    }

    public void setColors(int c1, int c2, int c3, int c4, Bitmap bitmap) {
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        Utilities.generateGradient(bitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
    }

    public void setColors(int c1, int c2, int c3, int c4, boolean invalidate) {
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        Utilities.generateGradient(currentBitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
        if (invalidate) {
            invalidateParent();
        }
    }

    private void invalidateParent() {
        invalidateSelf();
        if (parentView != null && parentView.get() != null) {
            parentView.get().invalidate();
        }
        if (postInvalidateParent) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.invalidateMotionBackground);
            updateAnimation();
            AndroidUtilities.cancelRunOnUIThread(updateAnimationRunnable);
            AndroidUtilities.runOnUIThread(updateAnimationRunnable, 16);
        }
    }

    public boolean hasPattern() {
        return patternBitmap != null;
    }

    @Override
    public int getIntrinsicWidth() {
        if (patternBitmap != null) {
            return patternBitmap.getWidth();
        }
        return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (patternBitmap != null) {
            return patternBitmap.getHeight();
        }
        return super.getIntrinsicHeight();
    }

    public void setTranslationY(int y) {
        translationY = y;
    }

    public void setPatternBitmap(int intensity) {
        setPatternBitmap(intensity, patternBitmap);

    }

    @SuppressLint("NewApi")
    public void setPatternBitmap(int intensity, Bitmap bitmap) {
        this.intensity = intensity;
        patternBitmap = bitmap;
        invalidateLegacy = true;
        if (patternBitmap == null) {
            return;
        }
        if (useSoftLight) {
            if (intensity >= 0) {
                paint2.setBlendMode(BlendMode.SOFT_LIGHT);
            } else {
                paint2.setBlendMode(null);
            }
        }
        if (intensity < 0) {
            if (!useLegacyBitmap) {
                bitmapShader = new BitmapShader(currentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                gradientShader = new BitmapShader(patternBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint2.setShader(new ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN));
                matrix = new Matrix();
            } else {
                createLegacyBitmap();
                if (!errorWhileGenerateLegacyBitmap) {
                    paint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                } else {
                    paint2.setXfermode(null);
                }
            }
        } else {
            if (!useLegacyBitmap) {

            } else {
                paint2.setXfermode(null);
            }
        }
    }

    public void setPatternColorFilter(int color) {
        patternColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        invalidateParent();
    }

    public void setPatternAlpha(float alpha) {
        this.patternAlpha = alpha;
        invalidateParent();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        patternBounds.set(left, top, right, bottom);
        createLegacyBitmap();
    }

    private void createLegacyBitmap() {
        if (useLegacyBitmap && intensity < 0 && !errorWhileGenerateLegacyBitmap) {
            int w = (int) (patternBounds.width() * legacyBitmapScale);
            int h = (int) (patternBounds.height() * legacyBitmapScale);
            if (w > 0 && h > 0 && (legacyBitmap == null || legacyBitmap.getWidth() != w || legacyBitmap.getHeight() != h)) {
                if (legacyBitmap != null) {
                    legacyBitmap.recycle();
                }
                if (legacyBitmap2 != null) {
                    legacyBitmap2.recycle();
                    legacyBitmap2 = null;
                }
                try {

                    legacyBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    legacyCanvas = new Canvas(legacyBitmap);
                    invalidateLegacy = true;
                } catch (Exception e) {
                    if (legacyBitmap != null) {
                        legacyBitmap.recycle();
                        legacyBitmap = null;
                    }
                    FileLog.e(e);
                    errorWhileGenerateLegacyBitmap = true;
                    paint2.setXfermode(null);
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        canvas.save();
        float tr = patternBitmap != null ? bounds.top : translationY;
        int bitmapWidth = currentBitmap.getWidth();
        int bitmapHeight = currentBitmap.getHeight();
        float w = bounds.width();
        float h = bounds.height();
        float maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
        float width = bitmapWidth * maxScale;
        float height = bitmapHeight * maxScale;
        float x = (w - width) / 2;
        float y = (h - height) / 2;
        if (isPreview) {
            x += bounds.left;
            y += bounds.top;
            canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom);
        }
        if (intensity < 0) {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, alpha));
            if (patternBitmap != null) {
                if (useLegacyBitmap) {
                    if (errorWhileGenerateLegacyBitmap) {
                        bitmapWidth = patternBitmap.getWidth();
                        bitmapHeight = patternBitmap.getHeight();
                        maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                        width = bitmapWidth * maxScale;
                        height = bitmapHeight * maxScale;
                        x = (w - width) / 2;
                        y = (h - height) / 2;
                        rect.set(x, y, x + width, y + height);

                        int averageColor = AndroidUtilities.getAverageColor(colors[2], AndroidUtilities.getAverageColor(colors[0], colors[1]));
                        if (colors[3] != 0) {
                            averageColor = AndroidUtilities.getAverageColor(colors[3], averageColor);
                        }
                        if (legacyBitmapColorFilter == null || averageColor != legacyBitmapColor) {
                            legacyBitmapColor = averageColor;
                            legacyBitmapColorFilter = new PorterDuffColorFilter(averageColor, PorterDuff.Mode.SRC_IN);
                        }
                        paint2.setColorFilter(legacyBitmapColorFilter);
                        paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * alpha * patternAlpha));
                        canvas.translate(0, tr);
                        canvas.drawBitmap(patternBitmap, null, rect, paint2);
                    } else if (legacyBitmap != null) {
                        if (invalidateLegacy) {
                            rect.set(0, 0, legacyBitmap.getWidth(), legacyBitmap.getHeight());
                            int oldAlpha = paint.getAlpha();
                            paint.setAlpha(255);
                            legacyCanvas.drawBitmap(currentBitmap, null, rect, paint);
                            paint.setAlpha(oldAlpha);

                            bitmapWidth = patternBitmap.getWidth();
                            bitmapHeight = patternBitmap.getHeight();
                            maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                            width = bitmapWidth * maxScale;
                            height = bitmapHeight * maxScale;
                            x = (w - width) / 2;
                            y = (h - height) / 2;
                            rect.set(x, y, x + width, y + height);

                            paint2.setColorFilter(null);
                            paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * 255));
                            legacyCanvas.save();
                            legacyCanvas.scale(legacyBitmapScale, legacyBitmapScale);
                            legacyCanvas.drawBitmap(patternBitmap, null, rect, paint2);
                            legacyCanvas.restore();
                            invalidateLegacy = false;
                        }

                        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                        if (legacyBitmap2 != null && posAnimationProgress != 1f) {
                            paint.setAlpha((int) (alpha * patternAlpha * (1f - posAnimationProgress)));
                            canvas.drawBitmap(legacyBitmap2, null, rect, paint);

                            paint.setAlpha((int) (alpha * patternAlpha * posAnimationProgress));
                            canvas.drawBitmap(legacyBitmap, null, rect, paint);
                            paint.setAlpha(alpha);
                        } else {
                            canvas.drawBitmap(legacyBitmap, null, rect, paint);
                        }
                    }
                } else {
                    if (matrix == null) {
                        matrix = new Matrix();
                    }
                    matrix.reset();
                    matrix.setTranslate(x, y + tr);
                    float scaleW = (currentBitmap.getWidth() / (float) bounds.width());
                    float scaleH = (currentBitmap.getHeight() / (float) bounds.height());
                    float scale = 1.0f / Math.min(scaleW, scaleH);
                    matrix.preScale(scale, scale);
                    bitmapShader.setLocalMatrix(matrix);

                    matrix.reset();
                    bitmapWidth = patternBitmap.getWidth();
                    bitmapHeight = patternBitmap.getHeight();
                    maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                    width = bitmapWidth * maxScale;
                    height = bitmapHeight * maxScale;
                    x = (w - width) / 2;
                    y = (h - height) / 2;
                    matrix.setTranslate(x, y + tr);
                    matrix.preScale(maxScale, maxScale);
                    gradientShader.setLocalMatrix(matrix);
                    paint2.setColorFilter(null);
                    paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * alpha * patternAlpha));
                    rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                    canvas.drawRoundRect(rect, roundRadius, roundRadius, paint2);
                }
            }
        } else {
            if (roundRadius != 0) {
                matrix.reset();
                matrix.setTranslate(x, y);
                float scaleW = (currentBitmap.getWidth() / (float) bounds.width());
                float scaleH = (currentBitmap.getHeight() / (float) bounds.height());
                float scale = 1.0f / Math.min(scaleW, scaleH);
                matrix.preScale(scale, scale);
                bitmapShader.setLocalMatrix(matrix);

                rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                canvas.drawRoundRect(rect, roundRadius, roundRadius, paint);
            } else {
                canvas.translate(0, tr);
                rect.set(x, y, x + width, y + height);
                canvas.drawBitmap(currentBitmap, null, rect, paint);
            }

            if (patternBitmap != null) {
                bitmapWidth = patternBitmap.getWidth();
                bitmapHeight = patternBitmap.getHeight();
                maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                width = bitmapWidth * maxScale;
                height = bitmapHeight * maxScale;
                x = (w - width) / 2;
                y = (h - height) / 2;
                rect.set(x, y, x + width, y + height);

                paint2.setColorFilter(patternColorFilter);
                paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * alpha * patternAlpha));
                canvas.drawBitmap(patternBitmap, null, rect, paint2);
            }
        }
        canvas.restore();

        updateAnimation();
    }

    private void updateAnimation() {
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        if (dt > 20) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        if (dt <= 1) {
            return;
        }

        if (posAnimationProgress < 1.0f) {
            float progress;
            if (rotatingPreview) {
                int stageBefore;
                float progressBefore = interpolator.getInterpolation(posAnimationProgress);
                if (progressBefore <= 0.25f) {
                    stageBefore = 0;
                } else if (progressBefore <= 0.5f) {
                    stageBefore = 1;
                } else if (progressBefore <= 0.75f) {
                    stageBefore = 2;
                } else {
                    stageBefore = 3;
                }
                posAnimationProgress += dt / (rotationBack ? 1000.0f : 2000.0f);
                if (posAnimationProgress > 1.0f) {
                    posAnimationProgress = 1.0f;
                }
                progress = interpolator.getInterpolation(posAnimationProgress);
                if (stageBefore == 0 && progress > 0.25f ||
                        stageBefore == 1 && progress > 0.5f ||
                        stageBefore == 2 && progress > 0.75f) {
                    if (rotationBack) {
                        phase++;
                        if (phase > 7) {
                            phase = 0;
                        }
                    } else {
                        phase--;
                        if (phase < 0) {
                            phase = 7;
                        }
                    }
                }
                if (progress <= 0.25f) {
                    progress /= 0.25f;
                } else if (progress <= 0.5f) {
                    progress = (progress - 0.25f) / 0.25f;
                } else if (progress <= 0.75f) {
                    progress = (progress - 0.5f) / 0.25f;
                } else {
                    progress = (progress - 0.75f) / 0.25f;
                }
                if (rotationBack) {
                    float prevProgress = progress;
                    progress = 1.0f - progress;
                    if (posAnimationProgress >= 1.0f) {
                        phase++;
                        if (phase > 7) {
                            phase = 0;
                        }
                        progress = 1.0f;
                    }
                }
            } else {
                posAnimationProgress += dt / (fastAnimation ? 300.0f : 500.0f);
                if (posAnimationProgress > 1.0f) {
                    posAnimationProgress = 1.0f;
                }
                progress = interpolator.getInterpolation(posAnimationProgress);
                if (rotationBack) {
                    progress = 1.0f - progress;
                    if (posAnimationProgress >= 1.0f) {
                        phase++;
                        if (phase > 7) {
                            phase = 0;
                        }
                        progress = 1.0f;
                    }
                }
            }

            if (postInvalidateParent || rotatingPreview) {
                Utilities.generateGradient(currentBitmap, true, phase, progress, currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
                invalidateLegacy = true;
            } else {
                if (useLegacyBitmap && intensity < 0) {

                } else {
                    if (progress != 1f) {
                        float part = 1f / ANIMATION_CACHE_BITMAPS_COUNT;
                        int i = (int) (progress / part);
                        if (i == 0) {
                            gradientCanvas.drawBitmap(gradientFromBitmap, 0, 0, null);
                        } else {
                            gradientCanvas.drawBitmap(gradientToBitmap[i - 1], 0, 0, null);
                        }
                        float alpha = (progress - i * part) / part;
                        paint3.setAlpha((int) (255 * alpha));
                        gradientCanvas.drawBitmap(gradientToBitmap[i], 0, 0, paint3);
                    } else {
                        gradientCanvas.drawBitmap(gradientToBitmap[ANIMATION_CACHE_BITMAPS_COUNT - 1], 0, 0, paint3);
                    }
                }
            }
            invalidateParent();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        paint.setAlpha(alpha);
        paint2.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
