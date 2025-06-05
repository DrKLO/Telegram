package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

public class RecyclerItemsEnterAnimator {

    private final RecyclerListView listView;
    private final SparseArray<Float> listAlphaItems = new SparseArray<>();
    HashSet<View> ignoreView = new HashSet<>();
    boolean invalidateAlpha;
    boolean alwaysCheckItemsAlpha;
    public boolean animateAlphaProgressView = true;

    ArrayList<AnimatorSet> currentAnimations = new ArrayList<>();
    ArrayList<ViewTreeObserver.OnPreDrawListener> preDrawListeners = new ArrayList<>();

    public RecyclerItemsEnterAnimator(RecyclerListView listView, boolean alwaysCheckItemsAlpha) {
        this.listView = listView;
        this.alwaysCheckItemsAlpha = alwaysCheckItemsAlpha;
        listView.setItemsEnterAnimator(this);
    }

    public void dispatchDraw() {
        if (invalidateAlpha || alwaysCheckItemsAlpha) {
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
        final View finalProgressView = getProgressView();
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (finalProgressView != null && layoutManager != null) {
            listView.removeView(finalProgressView);
            ignoreView.add(finalProgressView);
            listView.addView(finalProgressView);
            layoutManager.ignoreView(finalProgressView);
            Animator animator;
            if (animateAlphaProgressView) {
                animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0f);
            } else {
                animator = ValueAnimator.ofFloat(0f, 1f);
            }
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
        ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                preDrawListeners.remove(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (child != finalProgressView && position >= finalFrom - 1 && listAlphaItems.get(position, null) == null) {
                        listAlphaItems.put(position, 0f);
                        invalidateAlpha = true;
                        listView.invalidate();
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
                currentAnimations.add(animatorSet);
                animatorSet.start();
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        currentAnimations.remove(animatorSet);
                        if (currentAnimations.isEmpty()) {
                            listAlphaItems.clear();
                            invalidateAlpha = true;
                            listView.invalidate();
                        }
                    }
                });
                return false;
            }
        };
        preDrawListeners.add(preDrawListener);
        listView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    public View getProgressView() {
        View progressView = null;
        int n = listView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) >= 0 && child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        return progressView;
    }

    public void onDetached() {
        cancel();
    }

    public void cancel() {
        if (!currentAnimations.isEmpty()) {
            ArrayList<AnimatorSet> animations = new ArrayList<>(currentAnimations);
            for (int i = 0; i < animations.size(); i++) {
                animations.get(i).end();
                animations.get(i).cancel();
            }
        }
        currentAnimations.clear();
        for (int i = 0; i < preDrawListeners.size(); i++) {
            listView.getViewTreeObserver().removeOnPreDrawListener(preDrawListeners.get(i));
        }
        preDrawListeners.clear();
        listAlphaItems.clear();
        listView.invalidate();
        invalidateAlpha = true;
    }
}
