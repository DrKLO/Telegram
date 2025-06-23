package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;

public class SpanningLinearLayoutManager extends LinearLayoutManager {

    private int minimumItemWidth = 0;
    private int minimumItemHeight = 0;

    private int maximumItemWidth = 0;
    private int maximumItemHeight = 0;

    private int currentMeasuredWidth = 0;
    private int currentMeasuredHeight = 0;

    private int prevItemWidth = 0;
    private int prevItemHeight = 0;

    private int itemHeight = 0;

    private int itemPadding = 0;
    private int lastAddedCount = 0;

    private RecyclerView recyclerView;

    private Runnable enshureOffscreenAnimated = () -> {
        if (lastAddedCount > 0 && recyclerView != null) {
            View v = getChildAt(0);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(v);
            if (holder != null && !holder.shouldIgnore()) {
                try {
                    ((SpanningItemAnimator) recyclerView.getItemAnimator()).animateAdd(holder);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public SpanningLinearLayoutManager(Context context, RecyclerView recyclerView) {
        super(context);
        this.recyclerView = recyclerView;
    }

    public SpanningLinearLayoutManager(Context context, int orientation, boolean reverseLayout, RecyclerView recyclerView) {
        super(context, orientation, reverseLayout);
        this.recyclerView = recyclerView;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return spanLayoutSize(super.generateDefaultLayoutParams());
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return spanLayoutSize(super.generateLayoutParams(c, attrs));
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return spanLayoutSize(super.generateLayoutParams(lp));
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        if (lp == null || lp.width <= 0 || lp.height <= 0) {
            return false;
        } else {
            if (sizeChanged) {
                if (getOrientation() == HORIZONTAL) {
                    lp.width = getCalculatedWidth();
                    lp.height = itemHeight;
                } else {
                    lp.height = getCalculatedHeight();
                }
            }
            return true;
        }
    }

    private int lastCalculatedSizeWidth;
    private int lastCalculatedSizeHeight;
    private boolean sizeChanged = false;

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0 && (lastCalculatedSizeWidth != w || lastCalculatedSizeHeight != h)) {
            calculateSize();
            lastCalculatedSizeWidth = w;
            lastCalculatedSizeHeight = h;
            sizeChanged = true;
        }
        super.onLayoutChildren(recycler, state);
        sizeChanged = false;
    }

    private void calculateSize() {
        if (getOrientation() == HORIZONTAL) {
            int newCurrentMeasuredWidth = (int) Math.round(getHorizontalSpace() / (double) getItemCount()) - itemPadding * 2;
            if (newCurrentMeasuredWidth != currentMeasuredWidth) {
                prevItemWidth = currentMeasuredWidth > 0 ? getCalculatedWidth() : 0;
                currentMeasuredWidth = newCurrentMeasuredWidth;
            }
        } else if (getOrientation() == VERTICAL) {
            int newCurrentMeasuredHeight = (int) Math.round(getVerticalSpace() /  (double) getItemCount()) - itemPadding * 2;
            if (newCurrentMeasuredHeight != currentMeasuredHeight) {
                prevItemHeight = currentMeasuredHeight > 0 ? getCalculatedHeight() : 0;
                currentMeasuredHeight = newCurrentMeasuredHeight;
            }
        }
    }

    public int getPrevItemWidth() {
        return prevItemWidth;
    }

    public int getPrevItemHeight() {
        return prevItemHeight;
    }

    private RecyclerView.LayoutParams spanLayoutSize(RecyclerView.LayoutParams layoutParams) {
        if (getOrientation() == HORIZONTAL) {
            layoutParams.width = this.getPrevItemWidth();
            if (layoutParams.width == 0) {
                layoutParams.width = this.getCalculatedWidth();
            }

            if (itemHeight > 0) {
                layoutParams.height = itemHeight;
            }

        } else if (getOrientation() == VERTICAL) {
            layoutParams.height = this.getPrevItemHeight();
            if (layoutParams.height == 0) {
                layoutParams.height = this.getCalculatedHeight();
            }
        }

        return layoutParams;
    }

    public int getCalculatedWidth() {
        int calculatedWidth = Math.max(minimumItemWidth, currentMeasuredWidth);
        if (maximumItemWidth > 0) {
            return Math.min(maximumItemWidth, calculatedWidth);
        }
        return calculatedWidth;
    }

    public int getCalculatedHeight() {
        int calculatedHeight = Math.max(minimumItemHeight, currentMeasuredHeight);
        if (maximumItemHeight > 0) {
            return Math.min(maximumItemHeight, calculatedHeight);
        }
        return calculatedHeight;
    }

    @Override
    public boolean canScrollVertically() {
        return currentMeasuredHeight != minimumItemHeight;
    }
    @Override
    public boolean canScrollHorizontally() {
        return currentMeasuredWidth != minimumItemWidth;
    }

    private int getHorizontalSpace() {
        return getWidth() - getPaddingRight() - getPaddingLeft();
    }

    private int getVerticalSpace() {
        return getHeight() - getPaddingBottom() - getPaddingTop();
    }

    public void setMinimumItemWidth(int minimumWidth) {
        this.minimumItemWidth = minimumWidth;
    }

    public int getMinimumItemWidth() {
        return minimumItemWidth;
    }

    public void setMinimumItemHeight(int minimumHeight) {
        this.minimumItemHeight = minimumHeight;
    }

    public int getMinimumItemHeight() {
        return minimumItemHeight;
    }

    public void setMaximumItemWidth(int maximumItemWidth) {
        this.maximumItemWidth = maximumItemWidth;
    }

    public int getMaximumItemWidth() {
        return maximumItemWidth;
    }

    public void setMaximumItemHeight(int maximumItemHeight) {
        this.maximumItemHeight = maximumItemHeight;
    }

    public int getMaximumItemHeight() {
        return maximumItemHeight;
    }


    public void setItemHeight(int value) {
        itemHeight = value;
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public void setItemPadding(int value) {
        itemPadding = value;
    }

    public int getItemPadding() {
        return itemPadding;
    }

    @Override
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        lastAddedCount = itemCount;
        calculateSize();
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        calculateSize();
    }

    private class SizeAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        View view;
        ViewGroup.LayoutParams layoutParams;

        boolean isWidthAnimate;
        int sizeFrom;
        int sizeTo;
        int diff;
        float translateMultiplier;

        public SizeAnimatorUpdateListener(View view, float translateMultiplier, int pos) {
            this.view = view;
            layoutParams = view.getLayoutParams();
            isWidthAnimate = getOrientation() == HORIZONTAL;
            sizeFrom = isWidthAnimate ? layoutParams.width : layoutParams.height;
            sizeTo = isWidthAnimate ? getCalculatedWidth() : getCalculatedHeight();
            diff = sizeTo - sizeFrom;
            this.translateMultiplier = translateMultiplier;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (diff != 0) {
                float v = (float) animation.getAnimatedValue();
                int result = (int) ((diff * v) + sizeFrom);
                if (this.isWidthAnimate) {
                    layoutParams.width = result;
                } else {
                    layoutParams.height = result;
                }
                if (translateMultiplier > 0) {
                    float r = (diff * translateMultiplier) * (1f - v);
                    if (isWidthAnimate) {
                        view.setTranslationX(r);
                    } else {
                        view.setTranslationY(r);
                    }
                }
                view.requestLayout();
            }
        }
    }

    public class SpanningItemAnimator extends DefaultItemAnimator {

        public float translateMultiplier = 0f;

        public SpanningItemAnimator() {
            super();
        }

        @Override
        protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
            return 0;
        }

        @Override
        protected long getMoveAnimationDelay() {
            return 0;
        }

        @Override
        public long getMoveDuration() {
            return 220;
        }

        @Override
        public long getRemoveDuration() {
            return 220;
        }

        @Override
        public long getAddDuration() {
            return 220;
        }

        @Override
        public long getChangeDuration() {
            return 220;
        }

        @Override
        public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder) {
            return true;
        }

        @Override
        public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
            ValueAnimator animator = new ValueAnimator();
            animator.setFloatValues(0f, 1f);

            View view = newHolder.itemView;
            view.setTranslationX(0);
            animator.setDuration(getChangeDuration());
            animator.addUpdateListener(new SizeAnimatorUpdateListener(view, 0f, newHolder.getLayoutPosition()));
            animator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                view.setTranslationX((fromX - toX) * (1f - v));
            });

            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    dispatchChangeStarting(newHolder, false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setTranslationX(0);
                    animator.removeAllListeners();
                    dispatchChangeFinished(newHolder, false);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });

            animator.setTarget(newHolder.itemView);
            animator.start();
            return false;
        }

        @Override
        public boolean animateAdd(RecyclerView.ViewHolder holder) {
            final View view = holder.itemView;
            final ViewPropertyAnimator animation = view.animate();
            mAddAnimations.add(holder);

            view.setScaleX(0);
            view.setScaleY(0);

            lastAddedCount--;
            AndroidUtilities.cancelRunOnUIThread(enshureOffscreenAnimated);
            AndroidUtilities.runOnUIThread(enshureOffscreenAnimated, getAddDuration() - 47);

            view.requestLayout();

            animation.alpha(1).scaleX(1).scaleY(1).setDuration(getAddDuration())
                    .setUpdateListener(new SizeAnimatorUpdateListener(view, translateMultiplier, holder.getLayoutPosition()))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            dispatchAddStarting(holder);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            view.setAlpha(1);
                            view.setScaleX(1);
                            view.setScaleY(1);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            view.setTranslationX(0);
                            animation.setListener(null);
                            dispatchAddFinished(holder);
                        }
                    }).start();

            return false;
        }

        @Override
        public boolean animateRemove(RecyclerView.ViewHolder holder, ItemHolderInfo info) {
            ValueAnimator animator = new ValueAnimator();

            View view = holder.itemView;

            animator.setFloatValues(0f, 1f);

            animator.setDuration(getChangeDuration());
            animator.addUpdateListener(new SizeAnimatorUpdateListener(view, 0, holder.getLayoutPosition()));
            animator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();

                view.setAlpha(1f - v);
                view.setScaleX(1f - v);
                view.setScaleY(1f - v);
            });

            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    dispatchRemoveStarting(holder);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    animator.removeAllListeners();

                    view.setAlpha(1);
                    view.setScaleX(1);
                    view.setScaleY(1);
                    view.setTranslationX(0);
                    view.setTranslationY(0);

                    dispatchRemoveFinished(holder);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });

            animator.setTarget(holder.itemView);
            animator.start();
            return false;
        }
    }
}
