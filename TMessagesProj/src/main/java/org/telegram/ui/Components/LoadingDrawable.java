package org.telegram.ui.Components;


import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class LoadingDrawable extends Drawable {

    private static final float APPEAR_DURATION = 550;
    private static final float DISAPPEAR_DURATION = 320;

    public Theme.ResourcesProvider resourcesProvider;
    public LoadingDrawable(Theme.ResourcesProvider resourcesProvider) {
        this();
        this.resourcesProvider = resourcesProvider;
    }

    public LoadingDrawable(int colorKey1, int colorKey2, Theme.ResourcesProvider resourcesProvider) {
        this();
        this.colorKey1 = colorKey1;
        this.colorKey2 = colorKey2;
        this.resourcesProvider = resourcesProvider;
    }

    public LoadingDrawable(int color1, int color2) {
        this();
        this.color1 = color1;
        this.color2 = color2;
    }

    public LoadingDrawable() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.density > 2 ? 2 : 1);
    }

    public void setColors(int color1, int color2) {
        this.color1 = color1;
        this.color2 = color2;
        this.stroke = false;
    }

    public void setColors(int color1, int color2, int strokeColor1, int strokeColor2) {
        this.color1 = color1;
        this.color2 = color2;
        this.stroke = true;
        this.strokeColor1 = strokeColor1;
        this.strokeColor2 = strokeColor2;
    }

    public void setBackgroundColor(int backgroundColor) {
        if (this.backgroundPaint == null) {
            this.backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        this.backgroundPaint.setColor(this.backgroundColor = backgroundColor);
    }

    public boolean isDisappearing() {
        return disappearStart > 0 && (SystemClock.elapsedRealtime() - disappearStart) < DISAPPEAR_DURATION;
    }

    public boolean isDisappeared() {
        return disappearStart > 0 && (SystemClock.elapsedRealtime() - disappearStart) >= DISAPPEAR_DURATION;
    }

    public long timeToDisappear() {
        if (disappearStart > 0) {
            return (long) DISAPPEAR_DURATION - (SystemClock.elapsedRealtime() - disappearStart);
        }
        return 0;
    }

    private long start = -1, disappearStart = -1;
    private LinearGradient gradient, strokeGradient;
    private Matrix matrix = new Matrix(), strokeMatrix = new Matrix();
    private int gradientColor1, gradientColor2;
    private int gradientStrokeColor1, gradientStrokeColor2;
    public int colorKey1 = Theme.key_dialogBackground;
    public int colorKey2 = Theme.key_dialogBackgroundGray;
    public boolean stroke;
    public Integer backgroundColor, color1, color2, strokeColor1, strokeColor2;
    private int gradientWidth;
    private float gradientWidthScale = 1f;
    private float speed = 1f;

    public Paint backgroundPaint;
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path usePath;
    private Path path = new Path();

    private Rect lastBounds;
    private float[] radii = new float[8];

    private RectF rectF = new RectF();

    private boolean appearByGradient;
    private int appearGradientWidth;
    private Paint appearPaint;
    private LinearGradient appearGradient;
    private Matrix appearMatrix;

    private int disappearGradientWidth;
    private Paint disappearPaint;
    private LinearGradient disappearGradient;
    private Matrix disappearMatrix;

    public void usePath(Path path) {
        usePath = path;
    }

    public void setGradientScale(float scale) {
        gradientWidthScale = scale;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setAppearByGradient(boolean enabled) {
        appearByGradient = enabled;
    }

    public void setRadiiDp(float allDp) {
        if (usePath != null) {
            paint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(allDp)));
            strokePaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(allDp)));
        } else {
            setRadiiDp(allDp, allDp, allDp, allDp);
        }
    }

    public void setRadiiDp(float topLeftDp, float topRightDp, float bottomRightDp, float bottomLeftDp) {
        setRadii(AndroidUtilities.dp(topLeftDp), AndroidUtilities.dp(topRightDp), AndroidUtilities.dp(bottomRightDp), AndroidUtilities.dp(bottomLeftDp));
    }

    public void setRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        final boolean changed = (
            radii[0] != topLeft ||
            radii[2] != topRight ||
            radii[4] != bottomRight ||
            radii[6] != bottomLeft
        );
        radii[0] = radii[1] = topLeft;
        radii[2] = radii[3] = topRight;
        radii[4] = radii[5] = bottomRight;
        radii[6] = radii[7] = bottomLeft;

        if (lastBounds != null && changed) {
            path.rewind();
            rectF.set(lastBounds);
            path.addRoundRect(rectF, radii, Path.Direction.CW);
        }
    }

    public void setRadii(float[] radii) {
        if (radii == null || radii.length != 8) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < 8; ++i) {
            if (this.radii[i] != radii[i]) {
                this.radii[i] = radii[i];
                changed = true;
            }
        }
        if (lastBounds != null && changed) {
            path.rewind();
            rectF.set(lastBounds);
            path.addRoundRect(rectF, radii, Path.Direction.CW);
        }
    }

    public void setBounds(@NonNull RectF bounds) {
        super.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
        lastBounds = null;
    }

    public void reset() {
        start = -1;
    }

    public void disappear() {
        if (!isDisappeared() && !isDisappearing()) {
            disappearStart = SystemClock.elapsedRealtime();
        }
    }

    public void resetDisappear() {
        disappearStart = -1;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (isDisappeared()) {
            return;
        }

        Rect bounds = getBounds();
        if (getPaintAlpha() <= 0) {
            return;
        }

        int width = bounds.width();
        if (width <= 0) {
            width = AndroidUtilities.dp(200);
        }
        int gwidth = (int) (Math.min(AndroidUtilities.dp(400), width) * gradientWidthScale);
        int color1 = this.color1 != null ? this.color1 : Theme.getColor(colorKey1, resourcesProvider);
        int color2 = this.color2 != null ? this.color2 : Theme.getColor(colorKey2, resourcesProvider);
        int strokeColor1 = this.strokeColor1 != null ? this.strokeColor1 : Theme.getColor(colorKey1, resourcesProvider);
        int strokeColor2 = this.strokeColor2 != null ? this.strokeColor2 : Theme.getColor(colorKey2, resourcesProvider);
        if (gradient == null || gwidth != gradientWidth || color1 != gradientColor1 || color2 != gradientColor2 || strokeColor1 != gradientStrokeColor1 || strokeColor2 != gradientStrokeColor2) {
            gradientWidth = gwidth;

            gradientColor1 = color1;
            gradientColor2 = color2;
            gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[] { gradientColor1, gradientColor2, gradientColor1 }, new float[] { 0f, .67f, 1f }, Shader.TileMode.REPEAT);
            gradient.setLocalMatrix(matrix);
            paint.setShader(gradient);

            gradientStrokeColor1 = strokeColor1;
            gradientStrokeColor2 = strokeColor2;
            strokeGradient = new LinearGradient(0, 0, gradientWidth, 0, new int[] { gradientStrokeColor1, gradientStrokeColor1, gradientStrokeColor2, gradientStrokeColor1 }, new float[] { 0f, .4f, .67f, 1f }, Shader.TileMode.REPEAT);
            strokeGradient.setLocalMatrix(strokeMatrix);
            strokePaint.setShader(strokeGradient);
        }

        long now = SystemClock.elapsedRealtime();
        if (start < 0) {
            start = now;
        }
        float t = (now - start) / 2000f;
        t = (float) Math.pow(t * speed / 4, .85f) * 4f;
        float offset = ((t * AndroidUtilities.density * gradientWidth) % gradientWidth);
        float appearT = (now - start) / APPEAR_DURATION;
        float disappearT = disappearStart > 0 ? 1f - CubicBezierInterpolator.EASE_OUT.getInterpolation(Math.min(1, (now - disappearStart) / DISAPPEAR_DURATION)) : 0;

        boolean disappearRestore = false;
        if (isDisappearing()) {
            int disappearGradientWidthNow = Math.max(AndroidUtilities.dp(200), bounds.width() / 3);

            if (disappearT < 1) {
                if (disappearPaint == null) {
                    disappearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    disappearGradient = new LinearGradient(0, 0, disappearGradientWidth = disappearGradientWidthNow, 0, new int[]{0xffffffff, 0x00ffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
                    disappearMatrix = new Matrix();
                    disappearGradient.setLocalMatrix(disappearMatrix);
                    disappearPaint.setShader(disappearGradient);
                    disappearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                } else if (disappearGradientWidth != disappearGradientWidthNow) {
                    disappearGradient = new LinearGradient(0, 0, disappearGradientWidth = disappearGradientWidthNow, 0, new int[]{0xffffffff, 0x00ffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
                    disappearGradient.setLocalMatrix(disappearMatrix);
                    disappearPaint.setShader(disappearGradient);
                }

                rectF.set(bounds);
                rectF.inset(-strokePaint.getStrokeWidth(), -strokePaint.getStrokeWidth());
                canvas.saveLayerAlpha(rectF, 255, Canvas.ALL_SAVE_FLAG);
                disappearRestore = true;
            }
        }
        boolean appearRestore = false;
        if (appearByGradient) {
            int appearGradientWidthNow = Math.max(AndroidUtilities.dp(200), bounds.width() / 3);

            if (appearT < 1) {
                if (appearPaint == null) {
                    appearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    appearGradient = new LinearGradient(0, 0, appearGradientWidth = appearGradientWidthNow, 0, new int[]{0x00ffffff, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
                    appearMatrix = new Matrix();
                    appearGradient.setLocalMatrix(appearMatrix);
                    appearPaint.setShader(appearGradient);
                    appearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                } else if (appearGradientWidth != appearGradientWidthNow) {
                    appearGradient = new LinearGradient(0, 0, appearGradientWidth = appearGradientWidthNow, 0, new int[]{0x00ffffff, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
                    appearGradient.setLocalMatrix(appearMatrix);
                    appearPaint.setShader(appearGradient);
                }

                rectF.set(bounds);
                rectF.inset(-strokePaint.getStrokeWidth(), -strokePaint.getStrokeWidth());
                canvas.saveLayerAlpha(rectF, 255, Canvas.ALL_SAVE_FLAG);
                appearRestore = true;
            }
        }

        matrix.setTranslate(offset, 0);
        gradient.setLocalMatrix(matrix);

        strokeMatrix.setTranslate(offset, 0);
        strokeGradient.setLocalMatrix(strokeMatrix);

        Path drawPath;
        if (usePath != null) {
            drawPath = usePath;
        } else {
            if (lastBounds == null || !lastBounds.equals(bounds)) {
                path.rewind();
                rectF.set(lastBounds = bounds);
                path.addRoundRect(rectF, radii, Path.Direction.CW);
            }
            drawPath = path;
        }
        if (backgroundPaint != null) {
            canvas.drawPath(drawPath, backgroundPaint);
        }
        canvas.drawPath(drawPath, paint);
        if (stroke) {
            canvas.drawPath(drawPath, strokePaint);
        }

        if (appearRestore) {
            canvas.save();
            float appearOffset = appearT * (appearGradientWidth + bounds.width() + appearGradientWidth) - appearGradientWidth;
            appearMatrix.setTranslate(bounds.left + appearOffset, 0);
            appearGradient.setLocalMatrix(appearMatrix);
            int inset = (int) strokePaint.getStrokeWidth();
            canvas.drawRect(bounds.left - inset, bounds.top - inset, bounds.right + inset, bounds.bottom + inset, appearPaint);
            canvas.restore();
            canvas.restore();
        }
        if (disappearRestore) {
            canvas.save();
            float appearOffset = disappearT * (disappearGradientWidth + bounds.width() + disappearGradientWidth) - disappearGradientWidth;
            disappearMatrix.setTranslate(bounds.right - appearOffset, 0);
            disappearGradient.setLocalMatrix(disappearMatrix);
            int inset = (int) strokePaint.getStrokeWidth();
            canvas.drawRect(bounds.left - inset, bounds.top - inset, bounds.right + inset, bounds.bottom + inset, disappearPaint);
            canvas.restore();
            canvas.restore();
        }

        if (!isDisappeared()) {
            invalidateSelf();
        }
    }

    public void updateBounds() {
        if (usePath != null) {
            usePath.computeBounds(AndroidUtilities.rectTmp, false);
            setBounds(AndroidUtilities.rectTmp);
        }
    }

    public int getPaintAlpha() {
        return paint.getAlpha();
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
        strokePaint.setAlpha(i);
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
