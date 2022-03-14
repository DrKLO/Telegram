package org.telegram.ui.Components;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;

public final class VerticalPositionAutoAnimator {
    private final AnimatorLayoutChangeListener animatorLayoutChangeListener;
    private SpringAnimation floatingButtonAnimator;
    private float offsetY;
    private View floatingButtonView;

    public static VerticalPositionAutoAnimator attach(View floatingButtonView) {
        return attach(floatingButtonView, 350);
    }

    public static VerticalPositionAutoAnimator attach(View floatingButtonView, float springStiffness) {
        return new VerticalPositionAutoAnimator(floatingButtonView, springStiffness);
    }

    public void addUpdateListener(DynamicAnimation.OnAnimationUpdateListener onAnimationUpdateListener) {
        floatingButtonAnimator.addUpdateListener(onAnimationUpdateListener);
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
        if (floatingButtonAnimator.isRunning()) {
            floatingButtonAnimator.getSpring().setFinalPosition(offsetY);
        } else floatingButtonView.setTranslationY(offsetY);
    }

    public float getOffsetY() {
        return offsetY;
    }

    private VerticalPositionAutoAnimator(View floatingButtonView, float springStiffness) {
        this.floatingButtonView = floatingButtonView;
        animatorLayoutChangeListener = new AnimatorLayoutChangeListener(floatingButtonView, springStiffness);
        floatingButtonView.addOnLayoutChangeListener(animatorLayoutChangeListener);
    }

    public void ignoreNextLayout() {
        animatorLayoutChangeListener.ignoreNextLayout = true;
    }

    private class AnimatorLayoutChangeListener implements View.OnLayoutChangeListener {
        private Boolean orientation;
        private boolean ignoreNextLayout;

        public AnimatorLayoutChangeListener(View view, float springStiffness) {
            floatingButtonAnimator = new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, offsetY);
            floatingButtonAnimator.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
            floatingButtonAnimator.getSpring().setStiffness(springStiffness);
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            checkOrientation();
            if (oldTop == 0 || oldTop == top || ignoreNextLayout) {
                ignoreNextLayout = false;
                return;
            }
            floatingButtonAnimator.cancel();
            if (v.getVisibility() != View.VISIBLE) {
                v.setTranslationY(offsetY);
                return;
            }
            floatingButtonAnimator.getSpring().setFinalPosition(offsetY);
            v.setTranslationY(oldTop - top + offsetY);
            floatingButtonAnimator.start();
        }

        private void checkOrientation() {
            final boolean orientation = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            if (this.orientation == null || this.orientation != orientation) {
                this.orientation = orientation;
                ignoreNextLayout = true;
            }
        }
    }
}
