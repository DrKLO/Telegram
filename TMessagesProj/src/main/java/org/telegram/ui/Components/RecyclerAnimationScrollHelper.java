package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;

public class RecyclerAnimationScrollHelper {

    public final static int SCROLL_DIRECTION_UNSET = -1;
    public final static int SCROLL_DIRECTION_DOWN = 0;
    public final static int SCROLL_DIRECTION_UP = 1;

    private RecyclerListView recyclerView;
    private LinearLayoutManager layoutManager;

    private int scrollDirection;
    ValueAnimator animator;

    ScrollListener scrollListener;

    AnimationCallback animationCallback;

    public RecyclerAnimationScrollHelper(RecyclerListView recyclerView, LinearLayoutManager layoutManager) {
        this.recyclerView = recyclerView;
        this.layoutManager = layoutManager;
    }

    public void scrollToPosition(int position, int offset) {
        scrollToPosition(position, offset, layoutManager.getReverseLayout(), false);
    }

    public void scrollToPosition(int position, int offset, boolean bottom) {
        scrollToPosition(position, offset, bottom, false);
    }

    public void scrollToPosition(int position, int offset, final boolean bottom, boolean smooth) {
        if (recyclerView.animationRunning) return;
        if (!smooth || scrollDirection == SCROLL_DIRECTION_UNSET) {
            layoutManager.scrollToPositionWithOffset(position, offset, bottom);
            return;
        }

        int n = recyclerView.getChildCount();
        if (n == 0) {
            layoutManager.scrollToPositionWithOffset(position, offset, bottom);
            return;
        }

        boolean scrollDown = scrollDirection == SCROLL_DIRECTION_DOWN;

        recyclerView.setScrollEnabled(false);
        int h = 0;
        int t = 0;
        final ArrayList<View> oldViews = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            View child = recyclerView.getChildAt(0);
            oldViews.add(child);

            int bot = child.getBottom();
            int top = child.getTop();
            if (bot > h) h = bot;
            if (top < t) t = top;

            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).setAnimationRunning(true);
            }
            recyclerView.removeView(child);
        }

        final int finalHeight = scrollDown ? h : recyclerView.getHeight() - t;

        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        AnimatableAdapter animatableAdapter = null;
        if (adapter instanceof AnimatableAdapter) {
            animatableAdapter = (AnimatableAdapter) adapter;
        }

        layoutManager.scrollToPositionWithOffset(position, offset, bottom);
        if (adapter != null) adapter.notifyDataSetChanged();
        AnimatableAdapter finalAnimatableAdapter = animatableAdapter;

        recyclerView.stopScroll();
        recyclerView.setVerticalScrollBarEnabled(false);
        if (animationCallback != null) animationCallback.onStartAnimation();

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                final ArrayList<View> incomingViews = new ArrayList<>();

                recyclerView.stopScroll();
                int n = recyclerView.getChildCount();
                int top = 0;
                int bottom = 0;
                for (int i = 0; i < n; i++) {
                    View child = recyclerView.getChildAt(i);
                    incomingViews.add(child);
                    if (child.getTop() < top)
                        top = child.getTop();
                    if (child.getBottom() > bottom)
                        bottom = child.getBottom();

                    if (child instanceof ChatMessageCell) {
                        ((ChatMessageCell) child).setAnimationRunning(true);
                    }
                }

                for (View view : oldViews) {
                    recyclerView.addView(view);
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setAnimationRunning(true);
                    }
                }

                recyclerView.animationRunning = true;
                if (finalAnimatableAdapter != null) finalAnimatableAdapter.onAnimationStart();

                final int scrollLength = finalHeight + (scrollDown ? -top : bottom - recyclerView.getHeight());

                if (animator != null) {
                    animator.removeAllListeners();
                    animator.cancel();
                }
                animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(animation -> {
                    float value = ((float) animation.getAnimatedValue());
                    for (View view : oldViews) {
                        if (scrollDown) {
                            view.setTranslationY(-scrollLength * value);
                        } else {
                            view.setTranslationY(scrollLength * value);
                        }
                    }

                    for (View view : incomingViews) {
                        if (scrollDown) {
                            view.setTranslationY((scrollLength) * (1f - value));
                        } else {
                            view.setTranslationY(-(scrollLength) * (1f - value));
                        }
                    }
                    if (scrollListener != null) scrollListener.onScroll();
                    recyclerView.invalidate();
                });

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        recyclerView.animationRunning = false;

                        for (View view : oldViews) {
                            recyclerView.removeView(view);
                            if (view instanceof ChatMessageCell) {
                                ((ChatMessageCell) view).setAnimationRunning(false);
                            }
                            view.setTranslationY(0);
                        }


                        recyclerView.setVerticalScrollBarEnabled(true);

                        int n = recyclerView.getChildCount();
                        for (int i = 0; i < n; i++) {
                            View child = recyclerView.getChildAt(i);
                            if (child instanceof ChatMessageCell) {
                                ((ChatMessageCell) child).setAnimationRunning(false);
                            }
                            child.setTranslationY(0);
                        }
                        if (animationCallback != null) {
                            animationCallback.onEndAnimation();
                        }
                        if (finalAnimatableAdapter != null) {
                            finalAnimatableAdapter.onAnimationEnd();
                        }

                        animator = null;
                    }
                });

                recyclerView.removeOnLayoutChangeListener(this);

                long duration = ((scrollLength / recyclerView.getMeasuredHeight()) + 1) * 200;

                duration = Math.min(duration, 1300);

                animator.setDuration(duration);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.start();
            }
        });
    }

    public void cancel() {
        if (animator != null) animator.cancel();
        clear();
    }

    private void clear() {
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.animationRunning = false;
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof AnimatableAdapter)
            ((AnimatableAdapter) adapter).onAnimationEnd();
        animator = null;

        int n = recyclerView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = recyclerView.getChildAt(i);
            child.setTranslationY(0f);
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).setAnimationRunning(false);
            }
        }
    }

    public void setScrollDirection(int scrollDirection) {
        this.scrollDirection = scrollDirection;
    }

    public void setScrollListener(ScrollListener listener) {
        scrollListener = listener;
    }

    public void setAnimationCallback(AnimationCallback animationCallback) {
        this.animationCallback = animationCallback;
    }

    public int getScrollDirection() {
        return scrollDirection;
    }

    public interface ScrollListener {
        void onScroll();
    }

    public static class AnimationCallback {
        public void onStartAnimation() {
        }

        public void onEndAnimation() {
        }
    }

    public static abstract class AnimatableAdapter extends RecyclerListView.SelectionAdapter {

        public boolean animationRunning;
        private boolean shouldNotifyDataSetChanged;
        private ArrayList<Integer> rangeInserted = new ArrayList<>();
        private ArrayList<Integer> rangeRemoved = new ArrayList<>();

        @Override
        public void notifyDataSetChanged() {
            if (!animationRunning) {
                super.notifyDataSetChanged();
            } else {
                shouldNotifyDataSetChanged = false;
            }
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            if (!animationRunning) {
                super.notifyItemRangeInserted(positionStart, itemCount);
            } else {
                rangeInserted.add(positionStart);
                rangeInserted.add(itemCount);
            }
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            if (!animationRunning) {
                super.notifyItemRangeRemoved(positionStart, itemCount);
            } else {
                rangeRemoved.add(positionStart);
                rangeRemoved.add(itemCount);
            }
        }

        public void onAnimationStart() {
            animationRunning = true;
            shouldNotifyDataSetChanged = false;
            rangeInserted.clear();
            rangeRemoved.clear();
        }

        public void onAnimationEnd() {
            animationRunning = false;
            if (shouldNotifyDataSetChanged) {
                notifyDataSetChanged();
            } else {
                if (!rangeInserted.isEmpty()) {
                    for (int i = 0; i < rangeInserted.size(); i += 2) {
                        notifyItemRangeInserted(rangeInserted.get(i), rangeInserted.get(i + 1));
                    }
                }

                if (!rangeRemoved.isEmpty()) {
                    for (int i = 0; i < rangeRemoved.size(); i += 2) {
                        notifyItemRangeRemoved(rangeRemoved.get(i), rangeRemoved.get(i + 1));
                    }
                }
            }
        }
    }
}
