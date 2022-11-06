package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ClipRoundedDrawable extends Drawable {

    private Callback callback = new Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable drawable) {
            ClipRoundedDrawable.this.invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable what, long when) {
            ClipRoundedDrawable.this.scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable what) {
            ClipRoundedDrawable.this.unscheduleSelf(what);
        }
    };
    private Drawable drawable;

    public ClipRoundedDrawable() {}

    public ClipRoundedDrawable(Drawable drawable) {
        setDrawable(drawable);
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public void setDrawable(Drawable newDrawable) {
        if (drawable != null) {
            drawable.setCallback(null);
        }
        drawable = newDrawable;
        if (drawable != null) {
            drawable.setBounds(getBounds());
            drawable.setCallback(callback);
        }
    }

    private Path path;
    private RectF tempBounds = new RectF();
    private boolean hasRadius = false;
    private float[] radii = new float[8];

    public void setRadius(float r) {
        radii[0] = radii[1] = radii[2] = radii[3] =
        radii[4] = radii[5] = radii[6] = radii[7] = Math.max(0, r);
        hasRadius = r > 0;
        updatePath();
    }

    public void setRadii(float rx, float ry) {
        radii[0] = radii[2] = radii[4] = radii[6] = Math.max(0, rx);
        radii[1] = radii[3] = radii[5] = radii[7] = Math.max(0, ry);
        hasRadius = rx > 0 || ry > 0;
        updatePath();
    }

    public void setRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        radii[0] = radii[1] = Math.max(0, topLeft);
        radii[2] = radii[3] = Math.max(0, topRight);
        radii[4] = radii[5] = Math.max(0, bottomRight);
        radii[6] = radii[7] = Math.max(0, bottomLeft);
        hasRadius = topLeft > 0 || topRight > 0 || bottomRight > 0 || bottomLeft > 0;
        updatePath();
    }

    private int R = (int) Math.round(Math.random() * 9999999);

    public void setRadii(float[] newRadii) {
        if (newRadii == null) {
            setRadius(0);
            return;
        }
        if (newRadii.length == 8) {
            for (int i = 0; i < 8; ++i)
                radii[i] = newRadii[i];
        } else if (newRadii.length == 4) {
            setRadii(newRadii[0], newRadii[1], newRadii[2], newRadii[3]);
            return;
        }
        hasRadius = false;
        for (int i = 0; i < 8; ++i) {
            if (radii[i] > 0) {
                hasRadius = true;
                break;
            }
        }
        updatePath();
    }

    private void updatePath() {
        if (!hasRadius) {
            return;
        }
        if (path == null) {
            path = new Path();
        } else {
            path.rewind();
        }
        tempBounds.set(getBounds());
        path.addRoundRect(tempBounds, radii, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (drawable != null) {
            drawable.setBounds(getBounds());
            if (!hasRadius) {
                canvas.save();
                canvas.clipRect(getBounds());
                drawable.draw(canvas);
                canvas.restore();
            } else {
                canvas.save();
                updatePath();
                canvas.clipPath(path);
                drawable.draw(canvas);
                canvas.restore();
            }
        }
    }

    @Override
    public void setAlpha(int i) {
        if (drawable != null) {
            drawable.setAlpha(i);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (drawable != null) {
            drawable.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        if (drawable != null) {
            return drawable.getIntrinsicWidth();
        }
        return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (drawable != null) {
            return drawable.getIntrinsicHeight();
        }
        return super.getIntrinsicHeight();
    }
}
