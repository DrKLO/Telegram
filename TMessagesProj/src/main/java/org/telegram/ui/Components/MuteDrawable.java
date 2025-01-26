package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;

public class MuteDrawable extends Drawable {

    private Drawable baseDrawable;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MuteDrawable(Context context) {
        baseDrawable = context.getResources().getDrawable(R.drawable.filled_sound_on).mutate();

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpf2(1.566f));
        strokePaint.setColor(0xFFFFFFFF);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        clipPaint.setStyle(Paint.Style.STROKE);
        clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clipPaint.setStrokeWidth(dpf2(4.5f));
        clipPaint.setColor(0xFFFF0000);
        clipPaint.setStrokeCap(Paint.Cap.ROUND);
        clipPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private final AnimatedFloat animatedMuted = new AnimatedFloat(this::invalidateSelf, 0, 200, CubicBezierInterpolator.EASE_OUT);
    private boolean muted;

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);

        baseDrawable.setBounds(bounds);
        baseDrawable.draw(canvas);

        final float muted = animatedMuted.set(this.muted);
        if (muted > 0) {
            final float p = dpf2(.783f);
            float ax = bounds.centerX() - dp(9) + p, ay = bounds.centerY() - dp(9) + p;
            float bx = bounds.centerX() + dp(9) - p, by = bounds.centerY() + dp(9) - p;
            if (this.muted) {
                ax = lerp(bx, ax, muted);
                ay = lerp(by, ay, muted);
            } else {
                bx = lerp(ax, bx, muted);
                by = lerp(ay, by, muted);
            }
            canvas.drawLine(ax, ay, bx, by, clipPaint);
            strokePaint.setAlpha((int) (0xFF * Math.min(1, 10 * muted)));
            canvas.drawLine(ax, ay, bx, by, strokePaint);
        }

        canvas.restore();
    }

    public void setMuted(boolean muted, boolean animated) {
        this.muted = muted;
        if (!animated) {
            animatedMuted.set(muted, true);
        }
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        baseDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24);
    }
}
