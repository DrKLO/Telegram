package org.telegram.ui.Components;

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
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.wallpaper.WallpaperGiftPatternPosition;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.blur3.utils.BitmapChangeTracker;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Random;

public class MotionBackgroundDrawable extends Drawable {

    private final static int ANIMATION_CACHE_BITMAPS_COUNT = 3;

    private static final boolean useLegacyBitmap = Build.VERSION.SDK_INT < 28;
    private static final boolean useSoftLight = Build.VERSION.SDK_INT >= 29;

    private final int[] colors = new int[]{
            0xff426D57,
            0xffF7E48B,
            0xff87A284,
            0xffFDF6CA
    };

    private long lastUpdateTime;
    private WeakReference<View> parentView;

    private boolean ignoreInterpolator;
    private final CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0.33, 0.0, 0.0, 1.0);

    private int translationY;

    public boolean isPreview;

    public float posAnimationProgress = 1.0f;
    private int phase;

    private final RectF rect = new RectF();
    private Bitmap currentBitmap;
    private Bitmap gradientFromBitmap;
    private final Bitmap[] gradientToBitmap = new Bitmap[ANIMATION_CACHE_BITMAPS_COUNT];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint paint2 = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint paint3 = new Paint();

    private int intensity = 100;
    private Canvas gradientCanvas;
    private Canvas gradientFromCanvas;

    private boolean postInvalidateParent;

    private Bitmap patternBitmap;
    private BitmapShader bitmapShader;
    private BitmapShader gradientShader;

    private Bitmap patternGiftBitmap;
    private ImageReceiver giftImageReceiver;
    private boolean disableGradientShaderScaling;
    private Matrix matrix;

    private boolean fastAnimation;

    private GradientDrawable gradientDrawable = new GradientDrawable();
    private GenericProvider<MotionBackgroundDrawable, Float> animationProgressProvider;

    private boolean rotationBack;

    private boolean rotatingPreview;

    private final Runnable updateAnimationRunnable = this::updateAnimation;

    private ColorFilter patternColorFilter;
    private int roundRadius;
    private float patternAlpha = 1f;
    private float backgroundAlpha = 1f;
    private int alpha = 255;

    private float indeterminateSpeedScale = 1f;
    private boolean isIndeterminateAnimation;
    private int bitmapWidth = 60;
    private int bitmapHeight = 80;

    private MotionBackgroundPaint motionBackgroundPaint;

    public MotionBackgroundDrawable() {
        super();
        init();
    }

    public MotionBackgroundDrawable(int c1, int c2, int c3, int c4, boolean preview) {
        this(c1, c2, c3, c4, 0, preview);
    }

    public MotionBackgroundDrawable(int c1, int c2, int c3, int c4, int rotation, boolean preview) {
        this(c1, c2, c3, c4, rotation, preview, false);
    }

    public MotionBackgroundDrawable(int c1, int c2, int c3, int c4, int rotation, boolean preview, boolean square) {
        super();
        if (square) {
            bitmapWidth = 80;
            bitmapHeight = 80;
        }
        isPreview = preview;
        setColors(c1, c2, c3, c4, rotation, false);
        init();
    }

    private void init() {
        currentBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        currentBitmap.setHasAlpha(false);
        for (int i = 0; i < ANIMATION_CACHE_BITMAPS_COUNT; i++) {
            gradientToBitmap[i] = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            gradientToBitmap[i].setHasAlpha(false);
        }
        gradientCanvas = new Canvas(currentBitmap);

        gradientFromBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        gradientFromBitmap.setHasAlpha(false);
        gradientFromCanvas = new Canvas(gradientFromBitmap);

        Utilities.generateGradient(currentBitmap, phase, interpolator.getInterpolation(posAnimationProgress), colors);
        if (useSoftLight) {
            paint2.setBlendMode(BlendMode.SOFT_LIGHT);
        }
    }

    public void setFastRenderAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && motionBackgroundPaint == null && !SharedConfig.fastWallpaperDisabled) {
            motionBackgroundPaint = new MotionBackgroundPaint();
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

    public Bitmap getPatternBitmap() {
        return patternBitmap;
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
        Utilities.generateGradient(currentBitmap, phase, interpolator.getInterpolation(posAnimationProgress), colors);
    }

    public float getPosAnimationProgress() {
        return posAnimationProgress;
    }

    public void setPosAnimationProgress(float posAnimationProgress) {
        this.posAnimationProgress = posAnimationProgress;
        updateAnimation();
    }

    public void switchToNextPosition() {
        switchToNextPosition(false);
    }

    public void switchToNextPosition(boolean fast) {
        if (posAnimationProgress < 1.0f || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            invalidateParent();
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

    public void generateNextGradient() {
        for (int i = -1; i < ANIMATION_CACHE_BITMAPS_COUNT; i++) {
            float p = (i + 1) / (float) ANIMATION_CACHE_BITMAPS_COUNT;
            Utilities.generateGradient(i < 0 ? gradientFromBitmap : gradientToBitmap[i], phase, p, colors);
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
        Utilities.generateGradient(gradientFromBitmap, phase, 0, colors);
        generateNextGradient();
    }

    public int[] getColors() {
        return colors;
    }

    public void setParentView(View view) {
        parentView = new WeakReference<>(view);
        if (giftImageReceiver != null) {
            giftImageReceiver.setParentView(view);
        }
    }

    public void setColors(int c1, int c2, int c3, int c4) {
        setColors(c1, c2, c3, c4, 0, true);
    }

    public void setColors(int c1, int c2, int c3, int c4, Bitmap bitmap) {
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        Utilities.generateGradient(bitmap, phase, interpolator.getInterpolation(posAnimationProgress), colors);
    }

    public void setColors(int c1, int c2, int c3, int c4, int rotation, boolean invalidate) {
        if (isPreview && c3 == 0 && c4 == 0) {
            gradientDrawable = new GradientDrawable(BackgroundGradientDrawable.getGradientOrientation(rotation), new int[]{c1, c2});
        } else {
            gradientDrawable = null;
        }
        if (colors[0] == c1 && colors[1] == c2 && colors[2] == c3 && colors[3] == c4) {
            return;
        }
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        if (currentBitmap != null) {
            Utilities.generateGradient(currentBitmap, phase, interpolator.getInterpolation(posAnimationProgress), colors);
            if (invalidate) {
                invalidateParent();
            }
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
        setPatternBitmap(intensity, patternBitmap, true);
    }

    public void setPatternBitmap(int intensity, Bitmap bitmap) {
        setPatternBitmap(intensity, bitmap, true);
    }


    private List<WallpaperGiftPatternPosition> giftPatternPositions;
    private int giftPosition = -1;
    public void setPatternGiftPositions(List<WallpaperGiftPatternPosition> giftPositions) {
        giftPatternPositions = giftPositions;
    }

    public void setGiftPatternRandomSeed(long seed) {
        if (giftPatternPositions != null) {
            giftPosition = new Random(seed).nextInt(giftPatternPositions.size());
        }
    }

    public void setGiftPatternBitmap(Bitmap bitmap) {
        patternGiftBitmap = bitmap;
        invalidateParent();
    }

    public void setGiftDrawable(TLRPC.Document document) {
        if (giftImageReceiver == null) {
            giftImageReceiver = new ImageReceiver();
            giftImageReceiver.setAlpha(0.5f);
            if (parentView != null) {
                giftImageReceiver.setParentView(parentView.get());
            }
            if (isAttached) {
                giftImageReceiver.onAttachedToWindow();
            }
        }

        giftImageReceiver.setImage(ImageLocation.getForDocument(document), "80_80", null, null, null, 0);
        giftImageReceiver.setAutoRepeatCount(1);
        giftImageReceiver.setAutoRepeat(1);
    }

    public boolean isAttached;

    public void onAttachedToWindow() {
        isAttached = true;
        if (giftImageReceiver != null) {
            giftImageReceiver.onAttachedToWindow();
        }
    }

    public void onDetachedFromWindow() {
        isAttached = false;
        if (giftImageReceiver != null) {
            giftImageReceiver.onDetachedFromWindow();
        }
    }

    public void setPatternBitmap(int intensity, Bitmap bitmap, boolean doNotScale) {
        this.intensity = intensity;
        patternBitmap = bitmap;
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
                gradientShader = new BitmapShader(patternBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                disableGradientShaderScaling = doNotScale;
                paint2.setShader(new ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN));
                paint2.setFilterBitmap(true);
                matrix = new Matrix();
            } else {
                paint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            }
        } else {
            if (useLegacyBitmap) {
                paint2.setXfermode(null);
            }
        }
    }

    private int patternColor = Color.BLACK;
    public void setPatternColorFilter(int color) {
        patternColor = color;
        patternColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        invalidateParent();
    }

    public void setPatternAlpha(float alpha) {
        this.patternAlpha = alpha;
        invalidateParent();
    }

    public void setBackgroundAlpha(float alpha) {
        this.backgroundAlpha = alpha;
        invalidateParent();
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        canvas.save();

        final Bitmap patternBitmap = getOrBuildPatternWithGiftBitmap();
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

        final boolean isUseFastRender = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && motionBackgroundPaint != null
            && currentBitmap != null
            && patternBitmap != null;

        if (intensity < 0) {
            if (!isUseFastRender && (!useLegacyBitmap || patternBitmap == null)) {
                canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (alpha * backgroundAlpha)));
            }
            if (patternBitmap != null) {
                if (useLegacyBitmap) {
                    checkLegacyForNegativeIntensity((int) (alpha * patternAlpha) * -intensity / 100);

                    bitmapWidth = patternBitmap.getWidth();
                    bitmapHeight = patternBitmap.getHeight();
                    maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                    width = bitmapWidth * maxScale;
                    height = bitmapHeight * maxScale;
                    x = (w - width) / 2;
                    y = (h - height) / 2;
                    rect.set(x, y, x + width, y + height);

                    if (patternAlphaInverted != null) {
                        canvas.drawBitmap(currentBitmap, null, rect, paint);
                        canvas.drawBitmap(patternAlphaInverted, null, rect, paint);
                    } else {
                        canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (alpha * backgroundAlpha)));
                    }
                    drawGiftImageForLegacyNegativeIntensity(canvas, rect, giftPosition);
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
                    matrix.setTranslate((int) x, (int) (y + tr));
                    if (!disableGradientShaderScaling || maxScale > 1.4f || maxScale < 0.8f) {
                        matrix.preScale(maxScale, maxScale);
                    } else {
                        maxScale = 1;
                    }
                    gradientShader.setLocalMatrix(matrix);
                    paint2.setColorFilter(null);
                    paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * alpha * patternAlpha));
                    rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);

                    if (isUseFastRender) {
                        final Paint paint = motionBackgroundPaint.getPaint(currentBitmap, patternBitmap,
                            patternColor, (int) (alpha * patternAlpha), intensity, canvas.isHardwareAccelerated());
                        motionBackgroundPaint.applyPatternMatrix(matrix);
                        motionBackgroundPaint.applyGradientMatrix(rect);

                        canvas.drawRoundRect(rect, roundRadius, roundRadius, paint);
                    } else {
                        canvas.drawRoundRect(rect, roundRadius, roundRadius, paint2);
                    }
                    drawGiftImageForNegativeIntensity(canvas, x, y + tr, maxScale);
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
                if (!isUseFastRender) {
                    canvas.drawRoundRect(rect, roundRadius, roundRadius, paint);
                }
            } else {
                canvas.translate(0, tr);
                if (gradientDrawable != null) {
                    gradientDrawable.setBounds((int) x, (int) y, (int) (x + width), (int) (y + height));
                    gradientDrawable.setAlpha((int) (255 * backgroundAlpha));
                    gradientDrawable.draw(canvas);
                } else {
                    rect.set(x, y, x + width, y + height);
                    Paint bitmapPaint = paint;
                    int wasAlpha = bitmapPaint.getAlpha();
                    bitmapPaint.setAlpha((int) (wasAlpha * backgroundAlpha));
                    if (!isUseFastRender) {
                        canvas.drawBitmap(currentBitmap, null, rect, bitmapPaint);
                    }
                    bitmapPaint.setAlpha(wasAlpha);
                }
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
                if (isUseFastRender) {
                    final Paint paint = motionBackgroundPaint.getPaint(currentBitmap, patternBitmap,
                        patternColor, (int) (alpha * patternAlpha), intensity, canvas.isHardwareAccelerated());

                    motionBackgroundPaint.applyPatternMatrix(rect);
                    motionBackgroundPaint.applyGradientMatrix(rect);

                    canvas.drawRect(rect, paint);
                } else {
                    canvas.drawBitmap(patternBitmap, null, rect, paint2);
                }
                paint2.setAlpha((int) ((Math.abs(intensity) / 100f) * alpha * patternAlpha * 0.8f));
                drawGiftImageForPositiveIntensity(canvas, rect, giftPosition);
            }
        }
        canvas.restore();

        updateAnimation();
    }

    public void setAnimationProgressProvider(GenericProvider<MotionBackgroundDrawable, Float> animationProgressProvider) {
        this.animationProgressProvider = animationProgressProvider;
        updateAnimation();
    }

    public void updateAnimation() {
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        if (dt > 20) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        if (dt <= 1) {
            return;
        }

        if (isIndeterminateAnimation && posAnimationProgress == 1.0f) {
            posAnimationProgress = 0f;
        }
        if (posAnimationProgress < 1.0f) {
            float progress;
            boolean isNeedGenerateGradient = postInvalidateParent || rotatingPreview;
            if (isIndeterminateAnimation) {
                posAnimationProgress += (dt / 12000f) * indeterminateSpeedScale;
                if (posAnimationProgress >= 1.0f) {
                    posAnimationProgress = 0.0f;
                }
                float progressPerPhase = 1f / 8f;
                phase = (int) (posAnimationProgress / progressPerPhase);
                progress = 1f - (posAnimationProgress - phase * progressPerPhase) / progressPerPhase;
                isNeedGenerateGradient = true;
            } else {
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
                    if (animationProgressProvider != null) {
                        posAnimationProgress = animationProgressProvider.provide(this);
                    } else {
                        posAnimationProgress += dt / (rotationBack ? 1000.0f : 2000.0f);
                    }
                    if (posAnimationProgress > 1.0f) {
                        posAnimationProgress = 1.0f;
                    }
                    if (animationProgressProvider == null && !ignoreInterpolator) {
                        progress = interpolator.getInterpolation(posAnimationProgress);
                    } else {
                        progress = posAnimationProgress;
                    }
                    if (ignoreInterpolator && (progress == 0 || progress == 1)) {
                        ignoreInterpolator = false;
                    }
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
                    if (animationProgressProvider != null) {
                        posAnimationProgress = animationProgressProvider.provide(this);
                    } else {
                        posAnimationProgress += dt / (fastAnimation ? 300.0f : 500.0f);
                    }
                    if (posAnimationProgress > 1.0f) {
                        posAnimationProgress = 1.0f;
                    }
                    if (animationProgressProvider == null && !ignoreInterpolator) {
                        progress = interpolator.getInterpolation(posAnimationProgress);
                    } else {
                        progress = posAnimationProgress;
                    }
                    if (ignoreInterpolator && (progress == 0 || progress == 1)) {
                        ignoreInterpolator = false;
                    }
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
            }

            if (isNeedGenerateGradient) {
                Utilities.generateGradient(currentBitmap, phase, progress, colors);
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

    public boolean isOneColor() {
        return colors[0] == colors[1] && colors[0] == colors[2] && colors[0] == colors[3];
    }

    public float getIndeterminateSpeedScale() {
        return indeterminateSpeedScale;
    }

    public void setIndeterminateSpeedScale(float indeterminateSpeedScale) {
        this.indeterminateSpeedScale = indeterminateSpeedScale;
    }

    public boolean isIndeterminateAnimation() {
        return isIndeterminateAnimation;
    }

    public void setIndeterminateAnimation(boolean isIndeterminateAnimation) {
        if (!isIndeterminateAnimation && this.isIndeterminateAnimation) {
            float progressPerPhase = 1f / 8f;
            int phase = (int) (posAnimationProgress / progressPerPhase);
            posAnimationProgress = 1f - (posAnimationProgress - phase * progressPerPhase) / progressPerPhase;
            ignoreInterpolator = true;
        }
        this.isIndeterminateAnimation = isIndeterminateAnimation;
    }

    private void drawGiftImageForNegativeIntensity(Canvas canvas, float tx, float ty, float scale) {
        drawGiftImage(canvas, giftPosition, tx, ty, scale, scale);
    }

    private void drawGiftImageForLegacyNegativeIntensity(Canvas canvas, RectF rect, int giftIndex) {
        drawGiftImageForPositiveIntensity(canvas, rect, giftIndex);
    }

    private void drawGiftImageForPositiveIntensity(Canvas canvas, RectF rect, int giftIndex) {
        if (giftPatternPositions != null && patternBitmap != null) {
            float sx = rect.width()  / (float) patternBitmap.getWidth();
            float sy = rect.height() / (float) patternBitmap.getHeight();
            drawGiftImage(canvas, giftIndex, rect.left, rect.top, sx, sy);
        }
    }

    private void drawGiftImage(Canvas canvas, int giftIndex, float tx, float ty, float sx, float sy) {
        if (giftPatternPositions != null && giftImageReceiver != null && giftIndex >= 0 && giftIndex < giftPatternPositions.size()) {
            final WallpaperGiftPatternPosition r = giftPatternPositions.get(giftIndex);
            canvas.save();
            canvas.translate(tx, ty);
            canvas.scale(sx, sy);
            canvas.concat(r.matrix);
            giftImageReceiver.setImageCoords(r.rect);
            giftImageReceiver.draw(canvas);
            canvas.restore();
        }
    }



    /* Pattern And Gift Merge */

    private final BitmapChangeTracker patternChangeTracker = new BitmapChangeTracker();
    private final BitmapChangeTracker giftChangeTracker = new BitmapChangeTracker();
    private Bitmap patternWithGiftBitmap;
    private Canvas patternWithGiftCanvas;
    private Paint patternWithGiftPaint;
    private int patternInvertedLastPosition;

    private Bitmap getOrBuildPatternWithGiftBitmap() {
        if (patternBitmap == null) {
            return null;
        }

        if (patternGiftBitmap == null) {
            return patternBitmap;
        }

        final boolean isPatternInvalidated = patternChangeTracker.isInvalidated(patternBitmap);
        final boolean isGiftInvalidated = giftChangeTracker.isInvalidated(patternGiftBitmap);
        final boolean isGiftPositionInvalidated = patternInvertedLastPosition != giftPosition;
        final boolean isPatternOrGiftInvalidated = isPatternInvalidated || isGiftInvalidated || isGiftPositionInvalidated;

        if (patternWithGiftBitmap != null && !isPatternOrGiftInvalidated) {
            return patternWithGiftBitmap;
        }

        final int W = patternBitmap.getWidth();
        final int H = patternBitmap.getHeight();
        final boolean recreateBitmap = patternWithGiftBitmap == null
                || patternWithGiftBitmap.getWidth() != W
                || patternWithGiftBitmap.getHeight() != H;

        if (recreateBitmap) {
            patternWithGiftBitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            patternWithGiftCanvas = new Canvas(patternWithGiftBitmap);
        }

        final Bitmap.Config patternConfig = patternBitmap.getConfig();
        if (patternConfig == Bitmap.Config.ARGB_8888) {
            Utilities.copyBitmaps(patternBitmap, patternWithGiftBitmap);
        } else if (patternConfig == Bitmap.Config.ALPHA_8) {
            Utilities.expandAlphaToBlack(patternBitmap, patternWithGiftBitmap);
        }

        if (patternWithGiftPaint == null) {
            patternWithGiftPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            patternWithGiftPaint.setAlpha(204); // 80%
        }

        drawGiftPatterns(patternWithGiftCanvas, patternWithGiftPaint, giftPosition);

        patternInvertedLastPosition = giftPosition;
        patternChangeTracker.set(patternBitmap);
        giftChangeTracker.set(patternGiftBitmap);

        return patternWithGiftBitmap;
    }

    private void drawGiftPatterns(Canvas canvas, Paint paint, int giftIndex) {
        if (patternGiftBitmap != null && giftPatternPositions != null) {
            for (int a = 0; a < giftPatternPositions.size(); a++) {
                if (a == giftIndex) {
                    continue;
                }

                final WallpaperGiftPatternPosition r = giftPatternPositions.get(a);
                canvas.save();
                canvas.concat(r.matrix);
                canvas.drawBitmap(patternGiftBitmap, null, r.rect, paint);
                canvas.restore();
            }
        }
    }



    /* Legacy Utils */

    private final BitmapChangeTracker patternWithGiftChangeTracker = new BitmapChangeTracker();
    private Bitmap patternAlphaInverted;
    private int patternInvertedLastAlpha;

    private void checkLegacyForNegativeIntensity(int alpha) {
        if (patternBitmap == null) {
            return;
        }

        final Bitmap patternToInvert = getOrBuildPatternWithGiftBitmap();
        final boolean patternToInvertInvalidated = patternWithGiftChangeTracker.isInvalidated(patternToInvert);
        if (patternToInvertInvalidated || patternAlphaInverted == null || patternInvertedLastAlpha != alpha) {
            final int W = patternBitmap.getWidth();
            final int H = patternBitmap.getHeight();
            patternInvertedLastAlpha = alpha;

            final boolean recreateBitmap = patternAlphaInverted == null
                || patternAlphaInverted.getWidth() != W
                || patternAlphaInverted.getHeight() != H;
            if (recreateBitmap) {
                patternAlphaInverted = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            }

            Utilities.applyAlphaInvert(patternToInvert, patternAlphaInverted, alpha);
        }

        patternWithGiftChangeTracker.set(patternToInvert);
    }
}
