package org.telegram.ui.Components;


import android.animation.TimeInterpolator;
import android.os.SystemClock;
import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

public class AnimatedFloat {

    private View parent;
    private float value;
    private float targetValue;
    private boolean firstSet;

    private long transitionDelay = 0;
    private long transitionDuration = 200;
    private TimeInterpolator transitionInterpolator = CubicBezierInterpolator.DEFAULT;
    private boolean transition;
    private long transitionStart;
    private float startValue;

    public AnimatedFloat() {
        this.parent = null;
        this.firstSet = true;
    }

    public AnimatedFloat(long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = null;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedFloat(long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = null;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedFloat(View parentToInvalidate) {
        this.parent = parentToInvalidate;
        this.firstSet = true;
    }

    public AnimatedFloat(View parentToInvalidate, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedFloat(float initialValue, View parentToInvalidate) {
        this.parent = parentToInvalidate;
        this.value = targetValue = initialValue;
        this.firstSet = false;
    }

    public AnimatedFloat(float initialValue, View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.value = targetValue = initialValue;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = false;
    }

    public float get() {
        return value;
    }

    public float set(float mustBe) {
        return this.set(mustBe, false);
    }

    public float set(float mustBe, boolean force) {
        final long now = SystemClock.elapsedRealtime();
        if (force || firstSet) {
            value = targetValue = mustBe;
            transition = false;
            firstSet = false;
        } else if (Math.abs(targetValue - mustBe) > 0.0001f) {
            transition = true;
            targetValue = mustBe;
            startValue = value;
            transitionStart = now;
        }
        if (transition) {
            final float t = MathUtils.clamp((now - transitionStart - transitionDelay) / (float) transitionDuration, 0, 1);
            if (now - transitionStart >= transitionDelay) {
                value = AndroidUtilities.lerp(startValue, targetValue, transitionInterpolator.getInterpolation(t));
            }
            if (t >= 1f) {
                transition = false;
            } else if (parent != null) {
                parent.invalidate();
            }
        }
        return value;
    }

    public void setParent(View parent) {
        this.parent = parent;
    }
}