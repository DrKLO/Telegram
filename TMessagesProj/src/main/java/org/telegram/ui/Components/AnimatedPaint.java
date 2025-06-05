package org.telegram.ui.Components;

import android.animation.TimeInterpolator;
import android.graphics.Interpolator;
import android.graphics.Paint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

public class AnimatedPaint extends Paint {

    private final Theme.ResourcesProvider resourcesProvider;
    private final AnimatedColor color;

    public AnimatedPaint(View view) {
        this(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG, null);
    }

    public AnimatedPaint(View view, Theme.ResourcesProvider resourcesProvider) {
        this(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG, resourcesProvider);
    }

    public AnimatedPaint(View view, int paintFlags) {
        this(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT, paintFlags, null);
    }

    public AnimatedPaint(View view, long duration, TimeInterpolator interpolator, int paintFlags) {
        this(view, duration, interpolator, paintFlags, null);
    }

    public AnimatedPaint(View view, long duration, TimeInterpolator interpolator, int paintFlags, Theme.ResourcesProvider resourcesProvider) {
        super(paintFlags);
        this.resourcesProvider = resourcesProvider;
        color = new AnimatedColor(view, duration, interpolator);
    }

    public AnimatedPaint force(int color) {
        setColor(this.color.force(color));
        return this;
    }

    public AnimatedPaint set(int color) {
        setColor(this.color.set(color));
        return this;
    }

    public AnimatedPaint set(int color, float alpha) {
        setColor(Theme.multAlpha(this.color.set(color), alpha));
        return this;
    }

    public AnimatedPaint setByKey(int colorKey) {
        setColor(this.color.set(Theme.getColor(colorKey, resourcesProvider)));
        return this;
    }

    public AnimatedPaint setByKey(int colorKey, float alpha) {
        setColor(Theme.multAlpha(this.color.set(Theme.getColor(colorKey, resourcesProvider)), alpha));
        return this;
    }

    public AnimatedPaint multAlpha(float alpha) {
        setAlpha((int) (getAlpha() * alpha));
        return this;
    }

    public AnimatedPaint blendTo(int color, float t) {
        setColor(ColorUtils.blendARGB(getColor(), color, t));
        return this;
    }

}
