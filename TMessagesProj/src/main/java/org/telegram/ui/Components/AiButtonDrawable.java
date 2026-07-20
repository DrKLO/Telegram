package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class AiButtonDrawable extends Drawable {

    private final Drawable base;
    private final Drawable star;

    private final AnimatedFloat animation = new AnimatedFloat(this::invalidateSelf, 0, 1200L, CubicBezierInterpolator.EASE_OUT_QUINT);

    public AiButtonDrawable(Context context) {
        base = context.getResources().getDrawable(R.drawable.input_ai).mutate();
        star = context.getResources().getDrawable(R.drawable.input_ai_star).mutate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();

        base.setBounds(bounds);
        base.draw(canvas);

        final float t = animation.set(1.0f);

        final float cx1 = bounds.left + bounds.width() * 0.352f;
        final float cy1 = bounds.top + bounds.height() * 0.248f;
        final float r1 = bounds.width() * 0.105f * (float) (1.0f - Math.sin(Math.PI * AndroidUtilities.cascade(t, 0, 2, 1.5f)));

        final float cx2 = bounds.left + bounds.width() * 0.215f;
        final float cy2 = bounds.top + bounds.height() * 0.43f;
        final float r2 = bounds.width() * 0.09f * (float) (1.0f - Math.sin(Math.PI * AndroidUtilities.cascade(t, 1, 2, 1.5f)));

        star.setBounds((int) (cx1 - r1), (int) (cy1 - r1), (int) (cx1 + r1), (int) (cy1 + r1));
        star.draw(canvas);

        star.setBounds((int) (cx2 - r2), (int) (cy2 - r2), (int) (cx2 + r2), (int) (cy2 + r2));
        star.draw(canvas);
    }

    public void animate() {
        animation.force(0.0f);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        base.setAlpha(alpha);
        star.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        base.setColorFilter(colorFilter);
        star.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicWidth() {
        return base.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return base.getIntrinsicHeight();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
