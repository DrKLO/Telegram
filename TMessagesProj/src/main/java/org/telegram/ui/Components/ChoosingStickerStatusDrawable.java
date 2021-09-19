package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ChoosingStickerStatusDrawable extends StatusDrawable {

    Paint strokePaint;
    Paint fillPaint;

    private boolean isChat = false;
    private long lastUpdateTime = 0;
    private boolean started = false;
    float progress;
    boolean increment = true;
    int color;

    public ChoosingStickerStatusDrawable(boolean createPaint) {
        if (createPaint) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.2f));
        }
    }

    @Override
    public void start() {
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        invalidateSelf();
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public void setIsChat(boolean value) {
        this.isChat = value;
    }

    @Override
    public void setColor(int color) {
        if (this.color != color) {
            fillPaint.setColor(color);
            strokePaint.setColor(color);
        }
        this.color = color;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float animationProgress = Math.min(progress, 1f);
        float k = 0.3f;
        float p = CubicBezierInterpolator.EASE_IN.getInterpolation(animationProgress < k ? animationProgress / k : 1f);
        float p2 = CubicBezierInterpolator.EASE_OUT.getInterpolation(animationProgress < k ? 0 : (animationProgress - k) / (1f - k));
        float cx, xOffset;
        if (increment) {
            cx = AndroidUtilities.dp(2.1f) * p + (AndroidUtilities.dp(7) - AndroidUtilities.dp(2.1f)) * (1f - p);
            xOffset = AndroidUtilities.dpf2(1.5f) * (1f - CubicBezierInterpolator.EASE_OUT.getInterpolation(progress / 2));
        } else {
            cx = AndroidUtilities.dp(2.1f) * (1f - p) + (AndroidUtilities.dp(7) - AndroidUtilities.dp(2.1f)) * p;
            xOffset = AndroidUtilities.dpf2(1.5f) * CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(progress / 2);
        }
        float cy = AndroidUtilities.dp(11) / 2f;
        float r = AndroidUtilities.dpf2(2f);

        float scaleOffset = AndroidUtilities.dpf2(0.5f) * p - AndroidUtilities.dpf2(0.5f) * p2;

        Paint strokePaint = this.strokePaint != null ? this.strokePaint : Theme.chat_statusRecordPaint;
        Paint paint = this.fillPaint != null ? this.fillPaint : Theme.chat_statusPaint;
        if (strokePaint.getStrokeWidth() != AndroidUtilities.dp(0.8f)) {
            strokePaint.setStrokeWidth(AndroidUtilities.dp(0.8f));
        }
        for (int i = 0; i < 2; i++) {
            canvas.save();
            canvas.translate(strokePaint.getStrokeWidth() / 2f + xOffset + AndroidUtilities.dp(9) * i + getBounds().left + AndroidUtilities.dpf2(0.2f), strokePaint.getStrokeWidth() / 2f + AndroidUtilities.dpf2(2f) + getBounds().top);

            AndroidUtilities.rectTmp.set(0, scaleOffset, AndroidUtilities.dp(7), AndroidUtilities.dp(11) - scaleOffset);
            canvas.drawOval(AndroidUtilities.rectTmp, strokePaint);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.restore();
        }

        if (started) {
            update();
        }
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 16) {
            dt = 16;
        }
        progress += dt / 500f;
        if (progress >= 2f) {
            progress = 0;
            increment = !increment;
        }
        invalidateSelf();
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(20);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(18);
    }
}
