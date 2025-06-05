package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.ui.Components.CubicBezierInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VoIPEllipsizeSpan extends ReplacementSpan {

    private final CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00);

    private final View[] parents;

    public VoIPEllipsizeSpan(View... parents) {
        this.parents = parents;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return dp(20);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.save();
        canvas.translate(x + dp(4), y / 2f);
        long time = SystemClock.uptimeMillis() % 250 + 500;
        for (int i = 0; i < 3; i++) {
            long pointTime = (time + i * 250L) % 750;
            float moveFraction = Math.min(1, pointTime / 667f);
            float scale;
            if (moveFraction <= 0.425f) {
                scale = interpolator.getInterpolation(moveFraction / 0.425f);
            } else {
                scale = 1f - interpolator.getInterpolation((moveFraction - 0.425f) / 0.575f);
            }
            moveFraction = interpolator.getInterpolation(moveFraction);
            canvas.drawCircle(dpf2(1.667f + moveFraction * 16f), dp(3), dpf2(2 * scale), paint);
        }
        canvas.restore();
        for (View parent : parents) {
            parent.invalidate();
        }
    }
}