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
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.lang.ref.WeakReference;

public class MotionBackgroundDrawable extends Drawable {

    private int[] colors = new int[]{
            0xff426D57,
            0xffF7E48B,
            0xff87A284,
            0xffFDF6CA
    };

    private long lastUpdateTime;
    private WeakReference<View> parentView;

    private CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0.33, 0.0, 0.0, 1.0);

    private int translationY;

    private boolean isPreview;

    private float posAnimationProgress = 1.0f;
    private int phase;

    private RectF rect = new RectF();
    private Bitmap currentBitmap;
    private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Paint paint2 = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int intensity = 100;

    private Bitmap patternBitmap;
    private BitmapShader bitmapShader;
    private BitmapShader gradientShader;
    private Matrix matrix;

    private Canvas legacyCanvas;
    private Bitmap legacyBitmap;

    private boolean rotatingPreview;

    private android.graphics.Rect patternBounds = new android.graphics.Rect();

    private int roundRadius;

    public MotionBackgroundDrawable() {
        super();
        currentBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
        Utilities.generateGradient(currentBitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
        if (Build.VERSION.SDK_INT >= 29) {
            paint2.setBlendMode(BlendMode.SOFT_LIGHT);
        }
    }

    public MotionBackgroundDrawable(int c1, int c2, int c3, int c4, boolean preview) {
        super();
        colors[0] = c1;
        colors[1] = c2;
        colors[2] = c3;
        colors[3] = c4;
        isPreview = preview;
        if (Build.VERSION.SDK_INT >= 29) {
            paint2.setBlendMode(BlendMode.SOFT_LIGHT);
        }
        currentBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
        Utilities.generateGradient(currentBitmap, true, phase, interpolator.getInterpolation(posAnimationProgress), currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
    }

    public void setRoundRadius(int rad) {
        roundRadius = rad;
        matrix = new Matrix();
        bitmapShader = new BitmapShader(currentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(bitmapShader);
        invalidateParent();
    }

    public Bitmap getBitmap() {
        return currentBitmap;
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
            return Build.VERSION.SDK_INT < 29 ? 0x7fffffff : 0xffffffff;
        } else {
            if (Build.VERSION.SDK_INT < 29) {
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

    public void rotatePreview() {
        if (posAnimationProgress < 1.0f) {
            return;
        }
        rotatingPreview = true;
        posAnimationProgress = 0.0f;
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
        if (posAnimationProgress < 1.0f) {
            return;
        }
        rotatingPreview = false;
        posAnimationProgress = 0.0f;
        phase--;
        if (phase < 0) {
            phase = 7;
        }
        invalidateParent();
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
        if (parentView != null && parentView.get() != null) {
            parentView.get().invalidate();
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

    public void setPatternBitmap(int intensity, Bitmap bitmap) {
        this.intensity = intensity;
        patternBitmap = bitmap;
        if (Build.VERSION.SDK_INT >= 29) {
            if (intensity >= 0) {
                paint2.setBlendMode(BlendMode.SOFT_LIGHT);
            } else {
                paint2.setBlendMode(null);
            }
        }
        if (intensity < 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                bitmapShader = new BitmapShader(currentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                gradientShader = new BitmapShader(patternBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint2.setShader(new ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN));
                matrix = new Matrix();
            } else {
                paint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            }
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        patternBounds.set(left, top, right, bottom);
        if (Build.VERSION.SDK_INT < 28 && intensity < 0) {
            int w = right - left;
            int h = bottom - top;
            if (legacyBitmap == null || legacyBitmap.getWidth() != w || legacyBitmap.getHeight() != h) {
                if (legacyBitmap != null) {
                    legacyBitmap.recycle();
                }
                legacyBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                legacyCanvas = new Canvas(legacyBitmap);
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
        if (patternBitmap != null && intensity < 0) {
            canvas.drawColor(0xff000000);
            if (legacyBitmap != null) {
                rect.set(0, 0, legacyBitmap.getWidth(), legacyBitmap.getHeight());
                legacyCanvas.drawBitmap(currentBitmap, null, rect, paint);

                bitmapWidth = patternBitmap.getWidth();
                bitmapHeight = patternBitmap.getHeight();
                maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
                width = bitmapWidth * maxScale;
                height = bitmapHeight * maxScale;
                x = (w - width) / 2;
                y = (h - height) / 2;
                rect.set(x, y, x + width, y + height);
                legacyCanvas.drawBitmap(patternBitmap, null, rect, paint2);

                rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                canvas.drawBitmap(legacyBitmap, null, rect, paint);
            } else {
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

                rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                canvas.drawRoundRect(rect, roundRadius, roundRadius, paint2);
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
                canvas.drawBitmap(patternBitmap, null, rect, paint2);
            }
        }
        canvas.restore();

        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        if (dt > 20) {
            dt = 17;
        }
        lastUpdateTime = newTime;

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
                posAnimationProgress += dt / 2000.0f;
                if (posAnimationProgress > 1.0f) {
                    posAnimationProgress = 1.0f;
                }
                progress = interpolator.getInterpolation(posAnimationProgress);
                if (stageBefore == 0 && progress > 0.25f ||
                        stageBefore == 1 && progress > 0.5f ||
                        stageBefore == 2 && progress > 0.75f) {
                    phase--;
                    if (phase < 0) {
                        phase = 7;
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
            } else {
                posAnimationProgress += dt / 500.0f;
                if (posAnimationProgress > 1.0f) {
                    posAnimationProgress = 1.0f;
                }
                progress = interpolator.getInterpolation(posAnimationProgress);
            }
            Utilities.generateGradient(currentBitmap, true, phase, progress, currentBitmap.getWidth(), currentBitmap.getHeight(), currentBitmap.getRowBytes(), colors);
            invalidateParent();
        }
    }

    @Override
    public void setAlpha(int alpha) {
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
