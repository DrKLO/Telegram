package org.telegram.ui.Components;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class ButtonBounce {

    private View view;
    private final float durationPressMultiplier;
    private final float durationReleaseMultiplier;
    private final float overshoot;
    private long releaseDelay = 0;

    public ButtonBounce(View viewToInvalidate) {
        this(viewToInvalidate, 1f, 5.0f);
    }

    public ButtonBounce(View viewToInvalidate, float durationMultiplier, float overshoot) {
        view = viewToInvalidate;
        this.durationPressMultiplier = this.durationReleaseMultiplier = durationMultiplier;
        this.overshoot = overshoot;
    }

    public View getView() {
        return view;
    }

    public ButtonBounce(View viewToInvalidate, float durationPressMultiplier, float durationReleaseMultiplier, float overshoot) {
        view = viewToInvalidate;
        this.durationPressMultiplier = durationPressMultiplier;
        this.durationReleaseMultiplier = durationReleaseMultiplier;
        this.overshoot = overshoot;
    }

    private Runnable additionalInvalidate;
    public void setAdditionalInvalidate(Runnable additionalInvalidate) {
        this.additionalInvalidate = additionalInvalidate;
    }

    public ButtonBounce setReleaseDelay(long releaseDelay) {
        this.releaseDelay = releaseDelay;
        return this;
    }

    public void setView(View view) {
        this.view = view;
    }

    private ValueAnimator animator;
    private boolean isPressed;
    private float pressedT;

    public void setPressed(boolean pressed) {
        if (isPressed != pressed) {
            isPressed = pressed;
            ValueAnimator pastAnimator = animator;
            animator = null;
            if (pastAnimator != null) {
                pastAnimator.cancel();
            }
            animator = ValueAnimator.ofFloat(pressedT, pressed ? 1 : 0);
            animator.addUpdateListener(anm -> {
                pressedT = (float) anm.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == animator) {
                        animator = null;
                        pressedT = pressed ? 1 : 0;
                        invalidate();
                    }
                }
            });
            if (isPressed) {
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.setDuration((long) (60 * durationPressMultiplier));
                animator.setStartDelay(0);
            } else {
                animator.setInterpolator(new OvershootInterpolator(overshoot));
                animator.setDuration((long) (350 * durationReleaseMultiplier));
                animator.setStartDelay(releaseDelay);
            }
            animator.start();
        }
    }

    public float isPressedProgress() {
        return pressedT;
    }

    public float getScale(float diff) {
        return (1f - diff) + diff * (1f - pressedT);
    }

    public boolean isPressed() {
        return isPressed;
    }

    public void invalidate() {
        if (view != null) {
            view.invalidate();
        }
        if (additionalInvalidate != null) {
            additionalInvalidate.run();
        }
    }

}