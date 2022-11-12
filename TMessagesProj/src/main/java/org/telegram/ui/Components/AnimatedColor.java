package org.telegram.ui.Components;


import android.animation.TimeInterpolator;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

public class AnimatedColor {

    private View parent;
    private Runnable invalidate;
    private int value;
    private int targetValue;
    private boolean firstSet;

    private long transitionDelay = 0;
    private long transitionDuration = 200;
    private TimeInterpolator transitionInterpolator = CubicBezierInterpolator.DEFAULT;
    private boolean transition;
    private long transitionStart;
    private int startValue;

    public AnimatedColor() {
        this.parent = null;
        this.firstSet = true;
    }

    public AnimatedColor(long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = null;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedColor(long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = null;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedColor(View parentToInvalidate) {
        this.parent = parentToInvalidate;
        this.firstSet = true;
    }

    public AnimatedColor(View parentToInvalidate, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedColor(View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }


    public AnimatedColor(Runnable invalidate) {
        this.invalidate = invalidate;
        this.firstSet = true;
    }

    public AnimatedColor(Runnable invalidate, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.invalidate = invalidate;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedColor(int initialValue, View parentToInvalidate) {
        this.parent = parentToInvalidate;
        this.value = targetValue = initialValue;
        this.firstSet = false;
    }

    public AnimatedColor(int initialValue, Runnable invalidate) {
        this.invalidate = invalidate;
        this.value = targetValue = initialValue;
        this.firstSet = false;
    }

    public AnimatedColor(int initialValue, View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.value = targetValue = initialValue;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = false;
    }

    public AnimatedColor(int initialValue, Runnable invalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.invalidate = invalidate;
        this.value = targetValue = initialValue;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = false;
    }

    public int get() {
        return value;
    }

    // set() must be called inside onDraw/dispatchDraw
    // the main purpose of AnimatedColor is to interpolate between abrupt changing states

    public int set(int mustBeColor) {
        return this.set(mustBeColor, false);
    }

    public int set(int mustBeColor, boolean force) {
        final long now = SystemClock.elapsedRealtime();
        if (force || transitionDuration <= 0 || firstSet) {
            value = targetValue = mustBeColor;
            transition = false;
            firstSet = false;
        } else if (targetValue != mustBeColor) {
            transition = true;
            targetValue = mustBeColor;
            startValue = value;
            transitionStart = now;
        }
        if (transition) {
            final float t = MathUtils.clamp((now - transitionStart - transitionDelay) / (float) transitionDuration, 0, 1);
            if (now - transitionStart >= transitionDelay) {
                if (transitionInterpolator == null) {
                    value = ColorUtils.blendARGB(startValue, targetValue, t);
                } else {
                    value = ColorUtils.blendARGB(startValue, targetValue, transitionInterpolator.getInterpolation(t));
                }
            }
            if (t >= 1f) {
                transition = false;
            } else {
                if (parent != null) {
                    parent.invalidate();
                }
                if (invalidate != null) {
                    invalidate.run();
                }
            }
        }
        return value;
    }

    public float getTransitionProgress() {
        if (!transition) {
            return 0;
        }
        final long now = SystemClock.elapsedRealtime();
        return MathUtils.clamp((now - transitionStart - transitionDelay) / (float) transitionDuration, 0, 1);
    }

    public float getTransitionProgressInterpolated() {
        if (transitionInterpolator != null) {
            return transitionInterpolator.getInterpolation(getTransitionProgress());
        } else {
            return getTransitionProgress();
        }
    }

    public void setParent(View parent) {
        this.parent = parent;
    }
}