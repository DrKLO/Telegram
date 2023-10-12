package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.os.Build;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class ScaleStateListAnimator {

    public static void apply(View view) {
        apply(view, .1f, 1.5f);
    }

    public static void apply(View view, float scale, float tension) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        AnimatorSet pressedAnimator = new AnimatorSet();
        pressedAnimator.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f - scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f - scale)
        );
        pressedAnimator.setDuration(80);

        AnimatorSet defaultAnimator = new AnimatorSet();
        defaultAnimator.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
        );
        defaultAnimator.setInterpolator(new OvershootInterpolator(tension));
        defaultAnimator.setDuration(350);

        StateListAnimator scaleStateListAnimator = new StateListAnimator();

        scaleStateListAnimator.addState(new int[]{android.R.attr.state_pressed}, pressedAnimator);
        scaleStateListAnimator.addState(new int[0], defaultAnimator);

        view.setStateListAnimator(scaleStateListAnimator);
    }
}
