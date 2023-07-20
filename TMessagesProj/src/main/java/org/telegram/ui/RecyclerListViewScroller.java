package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

public class RecyclerListViewScroller {

    ValueAnimator valueAnimator;
    final RecyclerListView recyclerListView;

    int lastScrolled;

    public RecyclerListViewScroller(RecyclerListView recyclerListView) {
        this.recyclerListView = recyclerListView;
    }

    public void smoothScrollBy(int dy) {
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.cancel();
        }
        lastScrolled = 0;
        valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            int currentScroll = (int) (dy * (float) animation.getAnimatedValue());
            recyclerListView.scrollBy(0, currentScroll - lastScrolled);
            lastScrolled = currentScroll;
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                recyclerListView.scrollBy(0, dy - lastScrolled);
                valueAnimator = null;
            }
        });
        valueAnimator.setDuration(200);
        valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        valueAnimator.start();
    }

    public void cancel() {
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }

    public boolean isRunning() {
        return valueAnimator != null;
    }
}
