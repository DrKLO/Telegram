package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class SpeedIconDrawable extends Drawable {

    private final AnimatedTextView.AnimatedTextDrawable textDrawable;
    private final Drawable.Callback callback = new Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            SpeedIconDrawable.this.invalidateSelf();
        }
        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            SpeedIconDrawable.this.scheduleSelf(what, when);
        }
        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            SpeedIconDrawable.this.unscheduleSelf(what);
        }
    };
    private final Paint outlinePaint;

    public SpeedIconDrawable() {
        this(true);
    }

    public SpeedIconDrawable(boolean outline) {
        textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        textDrawable.setCallback(callback);
        textDrawable.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);
        textDrawable.setTypeface(AndroidUtilities.bold());
        textDrawable.setTextSize(AndroidUtilities.dp(10));
        textDrawable.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
        textDrawable.getPaint().setStrokeWidth(AndroidUtilities.dpf2(.6f));

        if (outline) {
            outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outlinePaint.setStyle(Paint.Style.STROKE);
        } else {
            outlinePaint = null;
        }
    }

//    private static Locale decimalFormatLocale;
//    private static DecimalFormat decimalFormat;
    public static String formatNumber(float value) {
        final float precision = Math.abs(value - .25f) < 0.001f && false ? 100F : 10F;
        float roundedValue = Math.round(value * precision) / precision;
        if (roundedValue == (long) roundedValue) {
            return "" + (long) roundedValue;
        } else {
            return "" + roundedValue;
        }
//        if (decimalFormat == null || decimalFormatLocale != Locale.getDefault()) {
//            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(decimalFormatLocale = Locale.getDefault());
//            symbols.setDecimalSeparator('.');
//            decimalFormat = new DecimalFormat("###,##0.0", symbols);
//        }
//        return decimalFormat.format(value);
    }

    public void setValue(float value, boolean animated) {
        String text = formatNumber(value) + "X";
        if (!animated || !TextUtils.equals(textDrawable.getText(), text)) {
            textDrawable.cancelAnimation();
            textDrawable.setText(text, animated);
            invalidateSelf();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (outlinePaint != null) {
            outlinePaint.setStrokeWidth(AndroidUtilities.dpf2(1.6f));
            AndroidUtilities.rectTmp.set(
                (getIntrinsicWidth() - textDrawable.getCurrentWidth()) / 2f - AndroidUtilities.dpf2(3f),
                (getIntrinsicHeight() - textDrawable.getHeight()) / 2f + AndroidUtilities.dpf2(0.2f),
                (getIntrinsicWidth() + textDrawable.getCurrentWidth()) / 2f + AndroidUtilities.dpf2(3f),
                (getIntrinsicHeight() + textDrawable.getHeight()) / 2f
            );
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dpf2(3f), AndroidUtilities.dpf2(3f), outlinePaint);
        }

        textDrawable.getPaint().setStrokeWidth(AndroidUtilities.dpf2(.3f));
        textDrawable.setBounds(0, (int) ((getIntrinsicHeight() - textDrawable.getHeight()) / 2F), getIntrinsicWidth(), (int) ((getIntrinsicHeight() + textDrawable.getHeight()) / 2F));
        textDrawable.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public void setAlpha(int alpha) {
        textDrawable.setAlpha(alpha);
        if (outlinePaint != null) {
            outlinePaint.setAlpha(alpha);
        }
    }

    public void setColor(int color) {
        textDrawable.setTextColor(color);
        if (outlinePaint != null) {
            outlinePaint.setColor(color);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
//        textDrawable.setColorFilter(colorFilter);
//        if (outlinePaint != null) {
//            outlinePaint.setColorFilter(colorFilter);
//        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
