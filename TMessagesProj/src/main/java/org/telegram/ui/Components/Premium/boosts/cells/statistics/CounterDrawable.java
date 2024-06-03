package org.telegram.ui.Components.Premium.boosts.cells.statistics;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class CounterDrawable extends Drawable {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRoundRect = new RectF();
    private final Drawable icon;
    private float textWith;
    private String text;

    public CounterDrawable(Context context) {
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(12));
        bgPaint.setColor(0xFF967bff);
        icon = ContextCompat.getDrawable(context, R.drawable.mini_boost_badge);
    }

    public void setText(String text) {
        this.text = text;
        textWith = textPaint.measureText(text);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        bgRoundRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        canvas.drawRoundRect(bgRoundRect, dp(12), dp(12), bgPaint);
        icon.setBounds(bounds.left + dp(2), bounds.top + dp(1), bounds.left + dp(2) + icon.getIntrinsicWidth(), getBounds().top + dp(1) + icon.getIntrinsicHeight());
        icon.draw(canvas);
        if (text != null) {
            canvas.drawText(text, dp(16.5f) + bounds.left, bounds.top + dp(13f), textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (dp(23) + textWith);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(18);
    }
}