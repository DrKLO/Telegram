package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

public class HideViewAfterAnimation extends AnimatorListenerAdapter {

    private final View view;
    public HideViewAfterAnimation(View view) {
        this.view = view;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
        view.setVisibility(View.GONE);
    }

}
