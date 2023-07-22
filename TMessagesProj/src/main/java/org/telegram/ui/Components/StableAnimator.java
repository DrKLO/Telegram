package org.telegram.ui.Components;

import android.animation.TimeAnimator;

import org.telegram.messenger.AndroidUtilities;

/**
 * Good for animations with a jank at the beginning.
 */
public class StableAnimator extends TimeAnimator {
    private int times = 0;
    private int totalTimes = 0;
    private AnimatorUpdateListener updateListener;
    private Object animatedValue;
    private float[] floatValues;

    public static StableAnimator ofFloat(float... values) {
        StableAnimator anim = new StableAnimator();
        anim.setFloatValues(values);
        return anim;
    }

    @Override
    public void setFloatValues(float[] floatValues) {
        super.setFloatValues(floatValues);
        this.floatValues = floatValues;
    }

    @Override
    public void addUpdateListener(AnimatorUpdateListener listener) {
        updateListener = listener;
    }

    @Override
    public Object getAnimatedValue() {
        return animatedValue;
    }

    @Override
    public void end() {
        updateListener = null;
        super.end();
    }

    @Override
    public void start() {
        setTimeListener((animation, totalTime, deltaTime) -> {
            if (times > 0 && totalTimes > 0) {
                times--;
                if (updateListener != null) {
                    if (floatValues != null && floatValues.length == 2) {
                        float percent = (float) times / (float) totalTimes;
                        float fraction = getInterpolator().getInterpolation(1f - percent);
                        animatedValue = floatValues[0] + ((floatValues[1] - floatValues[0]) * fraction);
                        updateListener.onAnimationUpdate(this);
                    } else {
                        end();
                    }
                }
            } else {
                end();
            }
        });
        this.times = (int) (getDuration() / AndroidUtilities.screenRefreshTime);
        this.totalTimes = this.times;
        super.start();
    }
}
