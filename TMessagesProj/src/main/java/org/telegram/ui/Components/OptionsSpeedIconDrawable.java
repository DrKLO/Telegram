package org.telegram.ui.Components;


import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class OptionsSpeedIconDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Drawable.Callback callback = new Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            OptionsSpeedIconDrawable.this.invalidateSelf();
        }
        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            OptionsSpeedIconDrawable.this.scheduleSelf(what, when);
        }
        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            OptionsSpeedIconDrawable.this.unscheduleSelf(what);
        }
    };

    private AnimatedTextView.AnimatedTextDrawable textDrawable;
    private boolean textDrawableVisible;
    private AnimatedFloat textDrawableAlpha = new AnimatedFloat(this::invalidateSelf, 250, CubicBezierInterpolator.EASE_OUT_QUINT);

    public OptionsSpeedIconDrawable() {
        paint.setColor(Color.WHITE);
    }

    public void setSpeed(Float speed, boolean animated) {
        if (speed == null && textDrawable == null) {
            return;
        }
        if (textDrawable == null) {
            textDrawable = new AnimatedTextView.AnimatedTextDrawable();
            textDrawable.setCallback(callback);
            textDrawable.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
            textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);
            textDrawable.setTypeface(AndroidUtilities.bold());
            textDrawable.setTextSize(dp(7));
            textDrawable.setTextColor(0xFFFFFFFF);
            textDrawable.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
            textDrawable.getPaint().setStrokeWidth(dpf2(.1f));
            textDrawable.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        if (speed == null) {
            textDrawable.cancelAnimation();
            textDrawable.setText("", animated);
            textDrawableVisible = false;
        } else {
            String string = SpeedIconDrawable.formatNumber(speed);
            if (string.length() <= 1) {
                string += "X";
            }
            if (!TextUtils.equals(string, textDrawable.getText())) {
                textDrawable.cancelAnimation();
                textDrawable.setText(string, animated);
                textDrawableVisible = !TextUtils.isEmpty(string);
            }
        }
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds() == null) {
            return;
        }

        int cx = getBounds().centerX(), cy = getBounds().centerY();

        canvas.drawCircle(cx, cy - dpf2(6), dpf2(2), paint);
        canvas.drawCircle(cx, cy, dpf2(2), paint);
        canvas.drawCircle(cx, cy + dpf2(6), dpf2(2), paint);

        if (textDrawable != null) {
            canvas.save();

            int tcx = cx - dp(11.6f), tcy = cy + dp(4);

            float alpha = textDrawableAlpha.set(textDrawableVisible ? 1 : 0);
            int wasAlpha = paint.getAlpha();
            if (alpha < 1) {
                paint.setAlpha((int) (0xFF * alpha));
            }

            AndroidUtilities.rectTmp.set(
                tcx - dpf2(1.5f) - textDrawable.getCurrentWidth() / 2f,
                tcy - dpf2(4),
                tcx + dpf2(1.5f) + textDrawable.getCurrentWidth() / 2f,
                tcy + dpf2(5)
            );
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(2), dpf2(2), paint);

            canvas.save();
            textDrawable.setBounds(tcx, tcy, tcx, tcy);
            textDrawable.draw(canvas);
            canvas.restore();

            paint.setAlpha(wasAlpha);

            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(45);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(45);
    }
}
