package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

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
        smoothScrollBy(dy, 200, CubicBezierInterpolator.DEFAULT);
    }

    public void smoothScrollBy(int dy, long duration, Interpolator interpolator) {
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.cancel();
        }

        final int[] total = new int[1];

        lastScrolled = 0;
        valueAnimator = ValueAnimator.ofInt(0, dy);
        valueAnimator.addUpdateListener(animation -> {
            int currentScroll = (int) animation.getAnimatedValue();
            int sY = currentScroll - lastScrolled;

            recyclerListView.scrollBy(0, sY);
            total[0] += sY;

            lastScrolled = currentScroll;
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {


                recyclerListView.scrollBy(0, dy - total[0]);
                valueAnimator = null;
            }
        });
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(interpolator);
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
