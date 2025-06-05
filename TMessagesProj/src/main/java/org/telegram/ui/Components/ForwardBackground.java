package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.StaticLayout;
import android.view.View;
import android.graphics.Rect;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class ForwardBackground {

    private final View view;
    public final Path path = new Path();
    public final Rect bounds = new Rect();
    public final ButtonBounce bounce;
    private final RectF r = new RectF();

    public ForwardBackground(View view) {
        this.view = view;
        bounce = new ButtonBounce(view, .8f, 1.4f);
    }

    private int rippleDrawableColor;
    private Drawable rippleDrawable;

    public void set(StaticLayout[] layout, boolean topLeftRad) {
        final int h = dp(4) + (int) Theme.chat_forwardNamePaint.getTextSize() * 2;

        float pinnedR = Math.max(0, Math.min(6, SharedConfig.bubbleRadius) - 1);
        float R = Math.min(9, SharedConfig.bubbleRadius);
        float joinR = Math.min(3, SharedConfig.bubbleRadius);

        float pad = 4 + R / 9f * 2.66f;
        float l = -dp(pad);
        float t = -dp(3);
        float b = h + dp(5);

        float w1 = layout[0].getLineWidth(0) + dp(pad);
        float w2 = layout[1].getLineWidth(0) + dp(pad);

        float D;

        path.rewind();

        D = 2 * dp(topLeftRad ? pinnedR : SharedConfig.bubbleRadius / 2f);
        r.set(l, t, l + D, t + D);
        path.arcTo(r, 180, 90);

        float w = w1;
        if (Math.abs(w1 - w2) < dp(joinR + R)) {
            w = Math.max(w1, w2);
        }

        if (Math.abs(w1 - w2) > dp(joinR + R)) {
            float d = 2 * dp(joinR);
            float hm;
            if (w1 < w2) {
                hm = t + 0.45f * (b - t);

                D = 2 * dp(R);
                r.set(w - D, t, w, t + D);
                path.arcTo(r, 270, 90);

                r.set(w1, hm - d, w1 + d, hm);
                path.arcTo(r, 180, -90);

                r.set(w2 - (b - hm), hm, w2, b);
                path.arcTo(r, 270, 90);

                r.set(w2 - (b - hm), hm, w2, b);
                path.arcTo(r, 0, 90);
            } else {
                hm = t + 0.55f * (b - t);
                r.set(w - (hm - t), t, w, hm);
                path.arcTo(r, 270, 90);

                D = 2 * dp(R);
                r.set(w1 - (hm - t), t, w1, hm);
                path.arcTo(r, 0, 90);

                r.set(w2, hm, w2 + d, hm + d);
                path.arcTo(r, 270, -90);

                r.set(w2 - D, b - D, w2, b);
                path.arcTo(r, 0, 90);
            }
        } else {
            D = 2 * dp(R);
            r.set(w - D, t, w, t + D);
            path.arcTo(r, 270, 90);

            r.set(w - D, b - D, w, b);
            path.arcTo(r, 0, 90);
        }

        r.set(l, b - D, l + D, b);
        path.arcTo(r, 90, 90);

        path.close();

        bounds.set((int) l, (int) t, (int) Math.max(w1, w2), (int) b);
    }

    public void setColor(int color) {
        if (rippleDrawableColor != color) {
            if (rippleDrawable == null) {
                rippleDrawable = Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_ALL);
            } else {
                Theme.setSelectorDrawableColor(rippleDrawable, color, true);
            }
            rippleDrawable.setCallback(view);
            rippleDrawableColor = color;
        }
    }

    public void setPressed(boolean pressed) {
        setPressed(pressed, bounds.centerX(), bounds.centerY());
    }

    public void setPressed(boolean pressed, float x, float y) {
        bounce.setPressed(pressed);
        if (pressed) {
            if (rippleDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rippleDrawable.setHotspot(x, y);
            }
        }
        if (rippleDrawable != null) {
            rippleDrawable.setState(pressed ? new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed} : new int[]{});
        }
        view.invalidate();
    }

    private LoadingDrawable loadingDrawable;

    public void draw(Canvas canvas, boolean loading) {
        canvas.save();
        canvas.clipPath(path);
        if (rippleDrawable != null) {
            rippleDrawable.setBounds(bounds);
            rippleDrawable.draw(canvas);
        }

        if (loading) {
            if (loadingDrawable == null) {
                loadingDrawable = new LoadingDrawable();
                loadingDrawable.setAppearByGradient(true);
            } else if (loadingDrawable.isDisappeared() || loadingDrawable.isDisappearing()) {
                loadingDrawable.reset();
                loadingDrawable.resetDisappear();
            }
        } else if (loadingDrawable != null && !loadingDrawable.isDisappearing() && !loadingDrawable.isDisappeared()) {
            loadingDrawable.disappear();
        }

        canvas.restore();

        if (loadingDrawable != null && !loadingDrawable.isDisappeared()) {
            loadingDrawable.usePath(path);
            loadingDrawable.setColors(
                Theme.multAlpha(rippleDrawableColor, .7f),
                Theme.multAlpha(rippleDrawableColor, 1.3f),
                Theme.multAlpha(rippleDrawableColor, 1.5f),
                Theme.multAlpha(rippleDrawableColor, 2f)
            );
            loadingDrawable.setBounds(bounds);
            canvas.save();
            loadingDrawable.draw(canvas);
            canvas.restore();
            view.invalidate();
        }

    }

}
