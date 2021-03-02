package org.webrtc;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.HashSet;

public class RecyclerItemsEnterAnimator {

    private final RecyclerListView listView;
    private final SparseArray<Float> listAlphaItems = new SparseArray<>();
    HashSet<View> ignoreView = new HashSet<>();
    boolean invalidateAlpha;

    public RecyclerItemsEnterAnimator(RecyclerListView listView) {
        this.listView = listView;
    }

    public void dispatchDraw() {
        if (invalidateAlpha || listAlphaItems.size() > 0) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                int position = listView.getChildAdapterPosition(child);
                if (position >= 0 && !ignoreView.contains(child)) {
                    Float alpha = listAlphaItems.get(position, null);
                    if (alpha == null) {
                        child.setAlpha(1f);
                    } else {
                        child.setAlpha(alpha);
                    }
                }
            }
            invalidateAlpha = false;
        }
    }

    public void showItemsAnimated(int from) {
        int n = listView.getChildCount();
        View progressView = null;
        for (int i = 0; i < n; i++) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) >= 0 && child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        final View finalProgressView = progressView;
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (progressView != null && layoutManager != null) {
            listView.removeView(progressView);
            ignoreView.add(finalProgressView);
            listView.addView(finalProgressView);
            layoutManager.ignoreView(finalProgressView);
            Animator animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finalProgressView.setAlpha(1f);
                    layoutManager.stopIgnoringView(finalProgressView);
                    ignoreView.remove(finalProgressView);
                    listView.removeView(finalProgressView);
                }
            });
            animator.start();
            from--;
        }
        int finalFrom = from;
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (child != finalProgressView && position >= finalFrom - 1 && listAlphaItems.get(position, null) == null) {
                        listAlphaItems.put(position, 0f);
                        child.setAlpha(0);
                        int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                        int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                        ValueAnimator a = ValueAnimator.ofFloat(0, 1f);
                        a.addUpdateListener(valueAnimator -> {
                            Float alpha = (Float) valueAnimator.getAnimatedValue();
                            listAlphaItems.put(position, alpha);
                            invalidateAlpha = true;
                            listView.invalidate();
                        });
                        a.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                listAlphaItems.remove(position);
                                invalidateAlpha = true;
                                listView.invalidate();
                            }
                        });
                        a.setStartDelay(delay);
                        a.setDuration(200);
                        animatorSet.playTogether(a);
                    }
                }
                animatorSet.start();
                return false;
            }
        });
    }
}
