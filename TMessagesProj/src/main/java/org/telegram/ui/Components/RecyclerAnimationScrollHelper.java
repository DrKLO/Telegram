package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.SparseArray;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;
import java.util.HashMap;

public class RecyclerAnimationScrollHelper {

    public final static int SCROLL_DIRECTION_UNSET = -1;
    public final static int SCROLL_DIRECTION_DOWN = 0;
    public final static int SCROLL_DIRECTION_UP = 1;

    private RecyclerListView recyclerView;
    private LinearLayoutManager layoutManager;

    private int scrollDirection;
    private ValueAnimator animator;

    private ScrollListener scrollListener;

    private AnimationCallback animationCallback;

    public SparseArray<View> positionToOldView = new SparseArray<>();
    private HashMap<Long, View> oldStableIds = new HashMap<>();
    public boolean forceUseStableId;
    public boolean isDialogs;

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
        scrollToPosition(position, offset, bottom, smooth, false);
    }

    public void scrollToPosition(int position, int offset, final boolean bottom, boolean smooth, boolean waitForAnimations) {
        if (recyclerView.fastScrollAnimationRunning) {
            return;
        }
        if (recyclerView.getItemAnimator() != null) {
            if (waitForAnimations) {
                if (recyclerView.getItemAnimator().isRunning(() -> scrollToPosition(position, offset, bottom, smooth, false))) {
                    return;
                }
            } else if (recyclerView.getItemAnimator().isRunning()) {
                return;
            }
        }
        if (!smooth || scrollDirection == SCROLL_DIRECTION_UNSET) {
            layoutManager.scrollToPositionWithOffset(position, offset, bottom);
            return;
        }

        int n = recyclerView.getChildCount();
        if (n == 0 || !MessagesController.getGlobalMainSettings().getBoolean("view_animations", true)) {
            layoutManager.scrollToPositionWithOffset(position, offset, bottom);
            return;
        }

        boolean scrollDown = scrollDirection == SCROLL_DIRECTION_DOWN;

        recyclerView.setScrollEnabled(false);

        final ArrayList<View> oldViews = new ArrayList<>();
        positionToOldView.clear();

        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        oldStableIds.clear();

        for (int i = 0; i < n; i++) {
            View child = recyclerView.getChildAt(i);
            oldViews.add(child);
            int childPosition = layoutManager.getPosition(child);
            positionToOldView.put(childPosition, child);
            if (adapter != null && (adapter.hasStableIds() || forceUseStableId)) {
                long itemId;
                if (forceUseStableId) {
                    int adapterPosition = ((RecyclerView.LayoutParams) child.getLayoutParams()).mViewHolder.getAdapterPosition();
                    if (adapterPosition < 0) {
                        continue;
                    }
                    itemId = adapter.getItemId(adapterPosition);
                } else {
                    itemId = ((RecyclerView.LayoutParams) child.getLayoutParams()).mViewHolder.getItemId();
                }

                oldStableIds.put(itemId, child);
            }
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).setAnimationRunning(true, true);
            }
        }

        recyclerView.prepareForFastScroll();

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

        recyclerView.fastScrollAnimationRunning = true;
        if (finalAnimatableAdapter != null) finalAnimatableAdapter.onAnimationStart();

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                recyclerView.removeOnLayoutChangeListener(this);
                final ArrayList<View> incomingViews = new ArrayList<>();

                recyclerView.stopScroll();
                int n = recyclerView.getChildCount();
                int top = 0;
                int bottom = 0;
                int scrollDiff = 0;
                boolean hasSameViews = false;
                for (int i = 0; i < n; i++) {
                    View child = recyclerView.getChildAt(i);
                    incomingViews.add(child);
                    if (child.getTop() < top)
                        top = child.getTop();
                    if (child.getBottom() > bottom)
                        bottom = child.getBottom();

                    if (child instanceof ChatMessageCell) {
                        ((ChatMessageCell) child).setAnimationRunning(true, false);
                    }

                    if (adapter != null && (adapter.hasStableIds() || forceUseStableId)) {
                        long stableId = adapter.getItemId(recyclerView.getChildAdapterPosition(child));
                        if (oldStableIds.containsKey(stableId)) {
                            View view = oldStableIds.get(stableId);
                            if (view != null) {
                                hasSameViews = true;
                                if (view instanceof ChatMessageCell) {
                                    ((ChatMessageCell) view).setAnimationRunning(false, false);
                                }
                                oldViews.remove(view);
                                if (animationCallback != null) {
                                    animationCallback.recycleView(view);
                                }
                                int dif = child.getTop() - view.getTop();
                                if (dif != 0) {
                                    scrollDiff = dif;
                                }
                            }
                        }
                    }
                }

                oldStableIds.clear();

                int oldH = 0;
                int oldT = Integer.MAX_VALUE;

                for (View view : oldViews) {
                    int bot = view.getBottom();
                    int topl = view.getTop();
                    if (bot > oldH) oldH = bot;
                    if (topl < oldT) oldT = topl;

                    if (view.getParent() == null) {
                        recyclerView.addView(view);
                        layoutManager.ignoreView(view);
                        if (animationCallback != null) {
                            animationCallback.ignoreView(view, true);
                        }
                    }
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).setAnimationRunning(true, true);
                    }
                }

                if (oldT == Integer.MAX_VALUE) {
                    oldT = 0;
                }

                if (animationCallback != null) {
                    animationCallback.onPreAnimation();
                }

                final int scrollLength ;
                if (oldViews.isEmpty()) {
                    scrollLength = Math.abs(scrollDiff);
                } else {
                    int finalHeight = scrollDown ? oldH : recyclerView.getHeight() - oldT;
                    scrollLength = finalHeight + (scrollDown ? -top : bottom - recyclerView.getHeight());
                }

                if (animator != null) {
                    animator.removeAllListeners();
                    animator.cancel();
                }
                animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(animation -> {
                    float value = ((float) animation.getAnimatedValue());
                    int size = oldViews.size();
                    for (int i = 0; i < size; i++) {
                        View view = oldViews.get(i);
                        float viewTop = view.getY();
                        float viewBottom = view.getY() + view.getMeasuredHeight();
                        if (viewBottom < 0 || viewTop > recyclerView.getMeasuredHeight()) {
                            continue;
                        }
                        if (scrollDown) {
                            view.setTranslationY(-scrollLength * value);
                        } else {
                            view.setTranslationY(scrollLength * value);
                        }
                    }

                    size = incomingViews.size();
                    for (int i = 0; i < size; i++) {
                        View view = incomingViews.get(i);
                        if (scrollDown) {
                            view.setTranslationY((scrollLength) * (1f - value));
                        } else {
                            view.setTranslationY(-(scrollLength) * (1f - value));
                        }
                    }
                    recyclerView.invalidate();
                    if (scrollListener != null) scrollListener.onScroll();
                });

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator == null) {
                            return;
                        }
                        recyclerView.fastScrollAnimationRunning = false;

                        for (View view : oldViews) {
                            if (view instanceof ChatMessageCell) {
                                ((ChatMessageCell) view).setAnimationRunning(false, true);
                            }
                            view.setTranslationY(0);
                            layoutManager.stopIgnoringView(view);
                            recyclerView.removeView(view);
                            if (animationCallback != null) {
                                animationCallback.ignoreView(view, false);
                                animationCallback.recycleView(view);
                            }
                        }

                        recyclerView.setScrollEnabled(true);
                        recyclerView.setVerticalScrollBarEnabled(true);

                        if (BuildVars.DEBUG_PRIVATE_VERSION) {
                            if (recyclerView.mChildHelper.getChildCount() != recyclerView.getChildCount()) {
                                throw new RuntimeException("views count in child helper must be quals views count in recycler view");
                            }

                            if (recyclerView.mChildHelper.getHiddenChildCount() != 0) {
                                throw new RuntimeException("hidden child count must be 0");
                            }
                        }

                        int n = recyclerView.getChildCount();
                        for (int i = 0; i < n; i++) {
                            View child = recyclerView.getChildAt(i);
                            if (child instanceof ChatMessageCell) {
                                ((ChatMessageCell) child).setAnimationRunning(false, false);
                            }
                            child.setTranslationY(0);
                        }

                        for (View v : incomingViews) {
                            if (v instanceof ChatMessageCell) {
                                ((ChatMessageCell) v).setAnimationRunning(false, false);
                            }
                            v.setTranslationY(0);
                        }

                        if (finalAnimatableAdapter != null) {
                            finalAnimatableAdapter.onAnimationEnd();
                        }

                        if (animationCallback != null) {
                            animationCallback.onEndAnimation();
                        }

                        positionToOldView.clear();

                        animator = null;
                    }
                });


                long duration;
                if (isDialogs) {
                    if (hasSameViews) {
                        duration = 150;
                        animator.setDuration(duration);
                        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    } else {
                        duration = (long) (((scrollLength / (float) recyclerView.getMeasuredHeight()) + 1f) * 200L);
                        if (duration < 300) {
                            duration = 300;
                        }
                        duration = Math.min(duration, 1300);
                        animator.setDuration(duration);
                        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    }
                } else {
                    if (hasSameViews) {
                        duration = 600;
                    } else {
                        duration = (long) (((scrollLength / (float) recyclerView.getMeasuredHeight()) + 1f) * 200L);
                        if (duration < 300) {
                            duration = 300;
                        }
                        duration = Math.min(duration, 1300);
                    }

                    animator.setDuration(duration);
                    animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                }
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
        recyclerView.fastScrollAnimationRunning = false;
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof AnimatableAdapter)
            ((AnimatableAdapter) adapter).onAnimationEnd();
        animator = null;

        int n = recyclerView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = recyclerView.getChildAt(i);
            child.setTranslationY(0f);
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).setAnimationRunning(false, false);
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

        public void recycleView(View view) {

        }

        public void onPreAnimation() {

        }

        public void ignoreView(View view, boolean ignore) {

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
                shouldNotifyDataSetChanged = true;
            }
        }

        @Override
        public void notifyItemInserted(int position) {
            if (!animationRunning) {
                super.notifyItemInserted(position);
            } else {
                rangeInserted.add(position);
                rangeInserted.add(1);
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
        public void notifyItemRemoved(int position) {
            if (!animationRunning) {
                super.notifyItemRemoved(position);
            } else {
                rangeRemoved.add(position);
                rangeRemoved.add(1);
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

        @Override
        public void notifyItemChanged(int position) {
            if (!animationRunning) {
                super.notifyItemChanged(position);
            }
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            if (!animationRunning) {
                super.notifyItemRangeChanged(positionStart, itemCount);
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
            if (shouldNotifyDataSetChanged || !rangeInserted.isEmpty() || !rangeRemoved.isEmpty()) {
                notifyDataSetChanged();
            }
        }
    }
}
