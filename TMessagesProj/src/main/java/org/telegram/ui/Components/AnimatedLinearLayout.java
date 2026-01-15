package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.lambda.Destroyable;

public class AnimatedLinearLayout extends LinearLayout {
    private final HashMap<View, Holder> viewHolders = new HashMap<>();
    private final ArrayList<Holder> visibleHolders = new ArrayList<>();
    private final ListAnimator.Callback callback = animator -> {
        checkViewsVisibility();
        onItemsChanged();
    };

    private final ListAnimator<Holder> listAnimator = new ListAnimator<>(
        callback, CubicBezierInterpolator.EASE_OUT_QUINT, 480L);

    public AnimatedLinearLayout(Context context) {
        super(context);
    }

    public boolean isViewVisible(View child) {
        final Holder holder = viewHolders.get(child);
        return holder != null && holder.isVisible;
    }

    public void setDebugName(View child, String tag) {
        final Holder holder = viewHolders.get(child);
        if (holder != null) {
            holder.tag = tag;
        }
    }

    public void setViewVisible(View child, boolean visible) {
        setViewVisible(child, visible, true);
    }

    public void setViewVisible(View child, boolean visible, boolean animated) {
        if (child == null) {
            return;
        }

        final Holder holder = viewHolders.get(child);
        if (holder != null && holder.isVisible != visible) {
            holder.isVisible = visible;
            if (visible) {
                holder.view.setVisibility(VISIBLE);
            }
            if (!visible && !holder.hasInAnimator){
                holder.view.setVisibility(GONE);
            }
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculateTotalSizesAfterMeasure();
    }

    protected final void calculateTotalSizesAfterMeasure() {
        totalHeight = 0;
        totalWidth = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            final Holder holder = viewHolders.get(view);

            if (view.getVisibility() == VISIBLE && holder != null && holder.isVisible) {
                totalWidth += view.getMeasuredWidth();
                totalHeight += view.getMeasuredHeight();
            }
        }
    }

    private int totalWidth;
    private int totalHeight;

    public int getSumWidthOfAllVisibleChild() {
        return totalWidth;
    }

    public int getSumHeightOfAllVisibleChild() {
        return totalHeight;
    }

    public float getAnimatedHeightWithPadding(float padding) {
        return getMetadata().getTotalHeight() + (padding) * getMetadata().getTotalVisibility();
    }

    public float getAnimatedHeightWithPadding() {
        return getAnimatedHeightWithPadding(getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        Log.i("LIST_DEBUG", "start list: ");

        visibleHolders.clear();
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            final Holder holder = viewHolders.get(view);

            if (view.getVisibility() == VISIBLE && holder != null && holder.isVisible) {
                visibleHolders.add(holder);
                holder.hasInAnimator = true;

                Log.i("LIST_DEBUG", "show item: " + holder.tag + " " + a);
            }
        }

        listAnimator.reset(visibleHolders, true);
        checkViewsVisibility();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        child.setVisibility(GONE);
        viewHolders.put(child, new Holder(child));
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        viewHolders.remove(child);
    }

    private Runnable onAnimatedHeightChanged;

    public void setOnAnimatedHeightChangedListener(Runnable onAnimatedHeightChanged) {
        this.onAnimatedHeightChanged = onAnimatedHeightChanged;
    }

    private float lastAnimatedHeight;

    private void checkViewsVisibility() {
        for (ListAnimator.Entry<Holder> entry : listAnimator) {
            final View view = entry.item.view;
            final RectF pos = entry.getRectF();
            if (getOrientation() == VERTICAL) {
                view.setTranslationY((getPaddingTop() + pos.top) - view.getTop());
            } else {
                view.setTranslationX((getPaddingLeft() + pos.left) - view.getLeft());
            }

            final float factor = entry.getVisibility();
            setChildVisibilityFactor(view, factor);
        }

        final float animatedHeight = getMetadata().getTotalHeight();
        if (lastAnimatedHeight != animatedHeight) {
            lastAnimatedHeight = animatedHeight;
            if (onAnimatedHeightChanged != null) {
                onAnimatedHeightChanged.run();
            }
        }
    }

    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.95f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    public ListAnimator.Metadata getMetadata() {
        return listAnimator.getMetadata();
    }

    protected void onItemsChanged() {

    }

    protected int getEntriesCount() {
        return listAnimator.size();
    }

    protected ListAnimator.Entry<?> getEntry(int index) {
        return listAnimator.getEntry(index);
    }

    private static class Holder implements ListAnimator.Measurable, Destroyable {
        private final View view;
        private boolean isVisible;
        private boolean hasInAnimator;
        private String tag;

        public Holder(@NonNull View view) {
            this.view = view;
        }

        @Override
        public void performDestroy() {
            view.setVisibility(View.GONE);
            hasInAnimator = false;
        }

        @Override
        public int hashCode() {
            return view.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof Holder) {
                return view.equals(((Holder) obj).view);
            }
            return false;
        }

        @Override
        public int getWidth() {
            return view.getMeasuredWidth();
        }

        @Override
        public int getHeight() {
            return view.getMeasuredHeight();
        }
    }
}
