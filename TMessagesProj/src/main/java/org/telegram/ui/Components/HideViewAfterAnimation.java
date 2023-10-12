package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

public class HideViewAfterAnimation extends AnimatorListenerAdapter {

    private final View view;
    private final boolean goneOnHide;

    public HideViewAfterAnimation(View view) {
        this.view = view;
        this.goneOnHide = true;
    }

    public HideViewAfterAnimation(View view, boolean goneOnHide) {
        this.view = view;
        this.goneOnHide = goneOnHide;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
        view.setVisibility(goneOnHide ? View.GONE : View.INVISIBLE);
    }

}
