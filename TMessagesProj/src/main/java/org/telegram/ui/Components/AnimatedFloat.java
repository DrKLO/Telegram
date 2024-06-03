package org.telegram.ui.Components;


import android.animation.TimeInterpolator;
import android.os.SystemClock;
import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

public class AnimatedFloat {

    private View parent;
    private Runnable invalidate;
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

    public AnimatedFloat(View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.parent = parentToInvalidate;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }


    public AnimatedFloat(Runnable invalidate) {
        this.invalidate = invalidate;
        this.firstSet = true;
    }

    public AnimatedFloat(Runnable invalidate, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.invalidate = invalidate;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedFloat(Runnable invalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.invalidate = invalidate;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = true;
    }

    public AnimatedFloat(float initialValue, View parentToInvalidate) {
        this.parent = parentToInvalidate;
        this.value = targetValue = initialValue;
        this.firstSet = false;
    }

    public AnimatedFloat(float initialValue, Runnable invalidate) {
        this.invalidate = invalidate;
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

    public AnimatedFloat(float initialValue, Runnable invalidate, long transitionDelay, long transitionDuration, TimeInterpolator transitionInterpolator) {
        this.invalidate = invalidate;
        this.value = targetValue = initialValue;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.transitionInterpolator = transitionInterpolator;
        this.firstSet = false;
    }

    // get() is not recommended to use (unless to minimize System.currentTimeMillis() calls)
    @Deprecated
    public float get() {
        return value;
    }

    // set() must be called inside onDraw/dispatchDraw
    // the main purpose of AnimatedFloat is to interpolate between abrupt changing states


    public float set(float mustBe) {
        return this.set(mustBe, false);
    }

    public float set(boolean mustBe) {
        return this.set(mustBe ? 1 : 0, false);
    }

    // do set(value, true) when it's needed to skip animation

    public float set(boolean mustBe, boolean force) {
        return this.set(mustBe ? 1 : 0, force);
    }

    public float set(float mustBe, boolean force) {
        if (force || transitionDuration <= 0 || firstSet) {
            value = targetValue = mustBe;
            transition = false;
            firstSet = false;
        } else if (Math.abs(targetValue - mustBe) > 0.0001f) {
            transition = true;
            targetValue = mustBe;
            startValue = value;
            transitionStart = SystemClock.elapsedRealtime();
        }
        if (transition) {
            final long now = SystemClock.elapsedRealtime();
            final float t = MathUtils.clamp((now - transitionStart - transitionDelay) / (float) transitionDuration, 0, 1);
            if (now - transitionStart >= transitionDelay) {
                if (transitionInterpolator == null) {
                    value = AndroidUtilities.lerp(startValue, targetValue, t);
                } else {
                    value = AndroidUtilities.lerp(startValue, targetValue, transitionInterpolator.getInterpolation(t));
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

    public void setDuration(long duration) {
        transitionDuration = duration;
    }

    public void setDelay(long delay) {
        transitionDelay = delay;
    }

    public long getDuration() {
        return transitionDuration;
    }

    public boolean isInProgress() {
        return transition;
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

    public float getTargetValue() {
        return targetValue;
    }

    public void setParent(View parent) {
        this.parent = parent;
    }

    public void setInvalidate(Runnable invalidate) {
        this.invalidate = invalidate;
    }
}