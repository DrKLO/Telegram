package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;
import android.widget.ImageView;

/** Old-theme screenshot shown during the day/night switch, cut by a circle whose edge is a soft radial gradient. */
public class ThemeRevealView extends ImageView {
    /** part of the radius the edge fades over */
    public static final float SOFT_EDGE = 0.4f;
    private static final float GRADIENT_RADIUS = 100f;

    public static final Property<ThemeRevealView, Float> RADIUS = new Property<ThemeRevealView, Float>(Float.class, "radius") {
        @Override
        public Float get(ThemeRevealView view) {
            return view.radius;
        }

        @Override
        public void set(ThemeRevealView view, Float value) {
            view.radius = value;
            view.invalidate();
        }
    };

    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix maskMatrix = new Matrix();
    private RadialGradient mask;
    private boolean hole;
    private float cx, cy, radius;

    // the switch icon is not in the screenshot: its current animation frame is drawn here in the old color, so it is
    // masked together with the old theme (the live icon below shows the new color)
    private ImageView icon;
    private ColorFilter iconOldFilter;
    private final int[] iconLocation = new int[2], location = new int[2];

    public ThemeRevealView(Context context) {
        super(context);
    }

    /** @param hole true: erase the screenshot inside the circle; false: keep it only inside the circle */
    public void setMask(float cx, float cy, boolean hole) {
        this.cx = cx;
        this.cy = cy;
        this.hole = hole;
        if (mask == null) {
            mask = new RadialGradient(0, 0, GRADIENT_RADIUS, new int[] { 0xFF000000, 0xFF000000, 0x00000000 }, new float[] { 0f, 1f - SOFT_EDGE, 1f }, Shader.TileMode.CLAMP);
            maskPaint.setShader(mask);
        }
        maskPaint.setXfermode(new PorterDuffXfermode(hole ? PorterDuff.Mode.DST_OUT : PorterDuff.Mode.DST_IN));
    }

    public void setIcon(ImageView icon, ColorFilter oldFilter) {
        this.icon = icon;
        this.iconOldFilter = oldFilter;
    }

    public void setRadius(float radius) {
        this.radius = radius;
        invalidate();
    }

    /** a radius that covers the whole {@code w}x{@code h} area from ({@code cx}, {@code cy}) with the soft edge outside of it */
    public static float coverRadius(float cx, float cy, float w, float h) {
        final float dx = Math.max(cx, w - cx), dy = Math.max(cy, h - cy);
        return (float) Math.sqrt(dx * dx + dy * dy) / (1f - SOFT_EDGE);
    }

    public void clearMask() {
        mask = null;
        maskPaint.setShader(null);
        icon = null;
        iconOldFilter = null;
    }

    private void drawIcon(Canvas canvas) {
        final Drawable drawable = icon != null ? icon.getDrawable() : null;
        if (drawable == null || !icon.isAttachedToWindow()) {
            return;
        }
        icon.getLocationInWindow(iconLocation);
        getLocationInWindow(location);
        final ColorFilter filter = drawable.getColorFilter();
        canvas.save();
        canvas.translate(iconLocation[0] - location[0] + icon.getPaddingLeft(), iconLocation[1] - location[1] + icon.getPaddingTop());
        canvas.concat(icon.getImageMatrix());
        drawable.setColorFilter(iconOldFilter);
        drawable.draw(canvas);
        drawable.setColorFilter(filter);
        canvas.restore();
        if (drawable instanceof Animatable && ((Animatable) drawable).isRunning()) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mask == null) {
            super.onDraw(canvas);
            return;
        }
        if (radius < 1f && !hole) {
            return;
        }
        final int count = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.onDraw(canvas);
        drawIcon(canvas);
        if (radius >= 1f) {
            maskMatrix.setScale(radius / GRADIENT_RADIUS, radius / GRADIENT_RADIUS);
            maskMatrix.postTranslate(cx, cy);
            mask.setLocalMatrix(maskMatrix);
            canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        }
        canvas.restoreToCount(count);
    }

    /**
     * Paints the icon's drawable area out of a reveal screenshot: each pixel row of it is filled with the row's background just
     * left of the icon (the switch icons lie on plain backgrounds: list rows, action bars).
     * @param snapshotOf the view the bitmap is a snapshot of, or null for a copy of the whole window
     */
    public static void eraseFromSnapshot(Bitmap bitmap, View snapshotOf, ImageView icon) {
        final Drawable drawable = icon.getDrawable();
        if (drawable == null || !icon.isAttachedToWindow() || !bitmap.isMutable()) {
            return;
        }
        final int[] location = new int[2];
        final RectF rect = new RectF(drawable.getBounds());
        icon.getImageMatrix().mapRect(rect);
        icon.getLocationInWindow(location);
        rect.offset(location[0] + icon.getPaddingLeft(), location[1] + icon.getPaddingTop());
        if (snapshotOf != null) {
            snapshotOf.getLocationInWindow(location);
            rect.offset(-location[0], -location[1]);
        }
        final int left = Math.max(1, (int) Math.floor(rect.left) - 1), right = Math.min(bitmap.getWidth(), (int) Math.ceil(rect.right) + 1);
        final int top = Math.max(0, (int) Math.floor(rect.top) - 1), bottom = Math.min(bitmap.getHeight(), (int) Math.ceil(rect.bottom) + 1);
        if (left >= right || top >= bottom) {
            return;
        }
        final int[] row = new int[right - left];
        for (int y = top; y < bottom; ++y) {
            java.util.Arrays.fill(row, bitmap.getPixel(left - 1, y));
            bitmap.setPixels(row, 0, row.length, left, y, row.length, 1);
        }
    }
}
