package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;

public class SummaryIcon extends Drawable {

    private boolean on;
    private final AnimatedFloat progress;
    private final Drawable arrow, stars;
    private int alpha = 0xFF;

    public SummaryIcon(View view) {
        progress = new AnimatedFloat(view, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        arrow = view.getContext().getResources().getDrawable(R.drawable.summary_arrow);
        stars = view.getContext().getResources().getDrawable(R.drawable.summary_stars);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        stars.setBounds(getBounds());
        stars.setAlpha(alpha);
        stars.draw(canvas);

        final float t = progress.set(on);
        final float cx = getBounds().centerX();
        final float cy = getBounds().centerY();
        final float sz = getBounds().width();

        canvas.save();
        if (t < 0.5f) {
            final float s = Math.abs(t - 0.5f) + 0.5f;
            canvas.scale(s, s, cx, cy);
        }

        canvas.save();
        if (t > 0.5f) {
            final float s = (Math.abs(t - 0.5f) + 0.5f);
            canvas.scale(-s, -s, getBounds().left + sz * 0.32f, getBounds().bottom - sz * 0.32f);
            canvas.translate(-sz * (1.0f - s) * 0.4f, sz * (1.0f - s) * 0.4f);
        }
        arrow.setBounds(getBounds());
        arrow.setAlpha(alpha);
        arrow.draw(canvas);
        canvas.restore();

        canvas.save();
        if (t > 0.5f) {
            final float s = (Math.abs(t - 0.5f) + 0.5f);
            canvas.scale(-s, -s, getBounds().right - sz * 0.32f, getBounds().top + sz * 0.32f);
        }
        canvas.rotate(180f, cx, cy);
        if (t > 0.5f) {
            final float s = (Math.abs(t - 0.5f) + 0.5f);
            canvas.translate(-sz * (1.0f - s) * 0.4f, sz * (1.0f - s) * 0.4f);
        }
        arrow.setBounds(getBounds());
        arrow.setAlpha(alpha);
        arrow.draw(canvas);
        canvas.restore();

        canvas.restore();
    }

    public void set(boolean on) {
        set(on, true);
    }
    public void set(boolean on, boolean animated) {
        this.on = on;
        if (!animated) {
            progress.set(on);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return arrow.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return arrow.getIntrinsicHeight();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        arrow.setColorFilter(colorFilter);
        stars.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
