package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

public class ComposeDrawable extends Drawable {

    private final Drawable background;
    private final Drawable icon;

    private final ArrayList<View> views = new ArrayList<>();

    private int tx, ty;
    private boolean iconVisible = false;
    private final AnimatedFloat animatedIconVisible = new AnimatedFloat(this::invalidate, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

    public ComposeDrawable(Drawable background, Drawable icon) {
        this.background = background;
        this.icon = icon;
    }

    public void addView(View view) {
        views.add(view);
    }

    public void setIconTranslate(int tx, int ty) {
        this.tx = tx;
        this.ty = ty;
    }

    public void setIconVisible(boolean visible) {
        setIconVisible(visible, true);
    }
    public void setIconVisible(boolean visible, boolean animated) {
        if (this.iconVisible == visible) return;
        this.iconVisible = visible;
        if (!animated) {
            animatedIconVisible.force(visible);
        }
        invalidate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float iconVisible = animatedIconVisible.set(this.iconVisible);

        background.setAlpha(alpha);
        background.setBounds(getBounds());
        background.draw(canvas);

        if (iconVisible > 0) {
            icon.setAlpha((int) (alpha * iconVisible));
            icon.setBounds(
                getBounds().left + tx,
                getBounds().top + ty,
                getBounds().left + tx + icon.getIntrinsicWidth(),
                getBounds().top + ty + icon.getIntrinsicHeight()
            );
            final float scale = lerp(0.5f, 1.0f, iconVisible);
            canvas.save();
            canvas.scale(scale, scale, icon.getBounds().centerX(), icon.getBounds().centerY());
            icon.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return background.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return background.getIntrinsicHeight();
    }

    private int alpha = 0xFF;
    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        background.setColorFilter(colorFilter);
        icon.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    private void invalidate() {
        for (View view : views)
            view.invalidate();
        invalidateSelf();
    }
}
