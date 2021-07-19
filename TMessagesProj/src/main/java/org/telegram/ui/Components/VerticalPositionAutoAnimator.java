package org.telegram.ui.Components;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;

public final class VerticalPositionAutoAnimator {

    public static VerticalPositionAutoAnimator attach(View floatingButtonView) {
        return attach(floatingButtonView, 350);
    }

    public static VerticalPositionAutoAnimator attach(View floatingButtonView, float springStiffness) {
        final AnimatorLayoutChangeListener listener = new AnimatorLayoutChangeListener(floatingButtonView, springStiffness);
        floatingButtonView.addOnLayoutChangeListener(listener);
        return new VerticalPositionAutoAnimator(listener);
    }

    private final AnimatorLayoutChangeListener animatorLayoutChangeListener;

    private VerticalPositionAutoAnimator(AnimatorLayoutChangeListener animatorLayoutChangeListener) {
        this.animatorLayoutChangeListener = animatorLayoutChangeListener;
    }

    public void ignoreNextLayout() {
        animatorLayoutChangeListener.ignoreNextLayout = true;
    }

    private static class AnimatorLayoutChangeListener implements View.OnLayoutChangeListener {

        private final SpringAnimation floatingButtonAnimator;

        private Boolean orientation;
        private boolean ignoreNextLayout;

        public AnimatorLayoutChangeListener(View view, float springStiffness) {
            floatingButtonAnimator = new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0);
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
            v.setTranslationY(oldTop - top);
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
