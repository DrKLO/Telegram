package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

public class SideMenultItemAnimator extends SimpleItemAnimator {

    private static TimeInterpolator sDefaultInterpolator;

    private ArrayList<RecyclerView.ViewHolder> mPendingRemovals = new ArrayList<>();
    private ArrayList<RecyclerView.ViewHolder> mPendingAdditions = new ArrayList<>();
    private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

    ArrayList<ArrayList<RecyclerView.ViewHolder>> mAdditionsList = new ArrayList<>();
    ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
    ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();

    ArrayList<RecyclerView.ViewHolder> mAddAnimations = new ArrayList<>();
    ArrayList<RecyclerView.ViewHolder> mMoveAnimations = new ArrayList<>();
    ArrayList<RecyclerView.ViewHolder> mRemoveAnimations = new ArrayList<>();
    ArrayList<RecyclerView.ViewHolder> mChangeAnimations = new ArrayList<>();

    private RecyclerListView parentRecyclerView;
    private boolean shouldClipChildren;

    private static class MoveInfo {
        public RecyclerView.ViewHolder holder;
        public int fromX, fromY, toX, toY;

        MoveInfo(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    private static class ChangeInfo {
        public RecyclerView.ViewHolder oldHolder, newHolder;
        public int fromX, fromY, toX, toY;

        private ChangeInfo(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder) {
            this.oldHolder = oldHolder;
            this.newHolder = newHolder;
        }

        ChangeInfo(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                   int fromX, int fromY, int toX, int toY) {
            this(oldHolder, newHolder);
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }

        @Override
        public String toString() {
            return "ChangeInfo{"
                    + "oldHolder=" + oldHolder
                    + ", newHolder=" + newHolder
                    + ", fromX=" + fromX
                    + ", fromY=" + fromY
                    + ", toX=" + toX
                    + ", toY=" + toY
                    + '}';
        }
    }

    public SideMenultItemAnimator(RecyclerListView view) {
        parentRecyclerView = view;
        view.setChildDrawingOrderCallback((childCount, i) -> {
            int childPos = i;
            if (i == childCount - 1) {
                childPos = 0;
            } else if (i >= 0) {
                childPos = i + 1;
            }
            return childPos;
        });
    }

    @Override
    public void runPendingAnimations() {
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            return;
        }
        int animatingHeight = 0;
        for (int a = 0, N = mPendingRemovals.size(); a < N; a++) {
            RecyclerView.ViewHolder holder = mPendingRemovals.get(a);
            animatingHeight += holder.itemView.getMeasuredHeight();
        }
        for (int a = 0, N = mPendingRemovals.size(); a < N; a++) {
            RecyclerView.ViewHolder holder = mPendingRemovals.get(a);
            animateRemoveImpl(holder, animatingHeight);
        }
        mPendingRemovals.clear();

        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();

            for (MoveInfo moveInfo : moves) {
                animateMoveImpl(moveInfo.holder, moveInfo.fromX, moveInfo.fromY, moveInfo.toX, moveInfo.toY);
            }
            moves.clear();
            mMovesList.remove(moves);
        }
        if (changesPending) {
            final ArrayList<ChangeInfo> changes = new ArrayList<>(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();

            for (ChangeInfo change : changes) {
                animateChangeImpl(change);
            }
            changes.clear();
            mChangesList.remove(changes);
        }
        if (additionsPending) {
            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();

            animatingHeight = 0;
            for (int a = 0, N = additions.size(); a < N; a++) {
                RecyclerView.ViewHolder holder = additions.get(a);
                animatingHeight += holder.itemView.getMeasuredHeight();
            }

            for (int a = 0, N = additions.size(); a < N; a++) {
                RecyclerView.ViewHolder holder = additions.get(a);
                animateAddImpl(holder, a, N, animatingHeight);
            }
            additions.clear();
            mAdditionsList.remove(additions);
        }
        parentRecyclerView.invalidateViews();
        parentRecyclerView.invalidate();
    }

    @Override
    public boolean animateRemove(final RecyclerView.ViewHolder holder, ItemHolderInfo info) {
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        return true;
    }

    private void animateRemoveImpl(final RecyclerView.ViewHolder holder, int totalHeight) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mRemoveAnimations.add(holder);
        animation.setDuration(220).translationY(-totalHeight).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                .setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                }).start();
    }

    @Override
    public boolean animateAdd(final RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        mPendingAdditions.add(holder);
        holder.itemView.setAlpha(0.0f);
        return true;
    }

    void animateAddImpl(final RecyclerView.ViewHolder holder, int num, int addCount, int totalHeight) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);
        view.setAlpha(1.0f);
        view.setTranslationY(-totalHeight);
        animation.translationY(0.0f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchAddStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setTranslationY(0.0f);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        dispatchAddFinished(holder);
                        mAddAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                }).start();
    }

    @Override
    public boolean animateMove(final RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        fromX += (int) holder.itemView.getTranslationX();
        fromY += (int) holder.itemView.getTranslationY();
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        if (deltaX != 0) {
            view.setTranslationX(-deltaX);
        }
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    void animateMoveImpl(final RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            view.animate().translationX(0);
        }
        if (deltaY != 0) {
            view.animate().translationY(0);
        }
        final ViewPropertyAnimator animation = view.animate();
        mMoveAnimations.add(holder);
        animation.setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                .setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchMoveStarting(holder);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                if (deltaX != 0) {
                    view.setTranslationX(0);
                }
                if (deltaY != 0) {
                    view.setTranslationY(0);
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animation.setListener(null);
                dispatchMoveFinished(holder);
                mMoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
        if (oldHolder == newHolder) {
            return animateMove(oldHolder, null, fromX, fromY, toX, toY);
        }
        final float prevTranslationX = oldHolder.itemView.getTranslationX();
        final float prevTranslationY = oldHolder.itemView.getTranslationY();
        final float prevAlpha = oldHolder.itemView.getAlpha();
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        oldHolder.itemView.setTranslationX(prevTranslationX);
        oldHolder.itemView.setTranslationY(prevTranslationY);
        oldHolder.itemView.setAlpha(prevAlpha);
        if (newHolder != null) {
            resetAnimation(newHolder);
            newHolder.itemView.setTranslationX(-deltaX);
            newHolder.itemView.setTranslationY(-deltaY);
            newHolder.itemView.setAlpha(0);
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        return true;
    }

    void animateChangeImpl(final ChangeInfo changeInfo) {
        final RecyclerView.ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final RecyclerView.ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        if (view != null) {
            final ViewPropertyAnimator oldViewAnim = view.animate().setDuration(getChangeDuration());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
            oldViewAnim.alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.oldHolder, true);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    oldViewAnim.setListener(null);
                    view.setAlpha(1);
                    view.setTranslationX(0);
                    view.setTranslationY(0);
                    dispatchChangeFinished(changeInfo.oldHolder, true);
                    mChangeAnimations.remove(changeInfo.oldHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
        if (newView != null) {
            final ViewPropertyAnimator newViewAnimation = newView.animate();
            mChangeAnimations.add(changeInfo.newHolder);
            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration()).alpha(1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.newHolder, false);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    newViewAnimation.setListener(null);
                    newView.setAlpha(1);
                    newView.setTranslationX(0);
                    newView.setTranslationY(0);
                    dispatchChangeFinished(changeInfo.newHolder, false);
                    mChangeAnimations.remove(changeInfo.newHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
    }

    private void endChangeAnimation(List<ChangeInfo> infoList, RecyclerView.ViewHolder item) {
        for (int i = infoList.size() - 1; i >= 0; i--) {
            ChangeInfo changeInfo = infoList.get(i);
            if (endChangeAnimationIfNecessary(changeInfo, item)) {
                if (changeInfo.oldHolder == null && changeInfo.newHolder == null) {
                    infoList.remove(changeInfo);
                }
            }
        }
    }

    private void endChangeAnimationIfNecessary(ChangeInfo changeInfo) {
        if (changeInfo.oldHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder);
        }
        if (changeInfo.newHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder);
        }
    }

    private boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, RecyclerView.ViewHolder item) {
        boolean oldItem = false;
        if (changeInfo.newHolder == item) {
            changeInfo.newHolder = null;
        } else if (changeInfo.oldHolder == item) {
            changeInfo.oldHolder = null;
            oldItem = true;
        } else {
            return false;
        }
        item.itemView.setAlpha(1);
        item.itemView.setTranslationX(0);
        item.itemView.setTranslationY(0);
        dispatchChangeFinished(item, oldItem);
        return true;
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
        final View view = item.itemView;
        view.animate().cancel();
        for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = mPendingMoves.get(i);
            if (moveInfo.holder == item) {
                view.setTranslationY(0);
                view.setTranslationX(0);
                dispatchMoveFinished(item);
                mPendingMoves.remove(i);
            }
        }
        endChangeAnimation(mPendingChanges, item);
        if (mPendingRemovals.remove(item)) {
            view.setTranslationY(0.0f);
            dispatchRemoveFinished(item);
        }
        if (mPendingAdditions.remove(item)) {
            view.setTranslationY(0.0f);
            dispatchAddFinished(item);
        }

        for (int i = mChangesList.size() - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            endChangeAnimation(changes, item);
            if (changes.isEmpty()) {
                mChangesList.remove(i);
            }
        }
        for (int i = mMovesList.size() - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            for (int j = moves.size() - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                if (moveInfo.holder == item) {
                    view.setTranslationY(0);
                    view.setTranslationX(0);
                    dispatchMoveFinished(item);
                    moves.remove(j);
                    if (moves.isEmpty()) {
                        mMovesList.remove(i);
                    }
                    break;
                }
            }
        }
        for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
            if (additions.remove(item)) {
                view.setTranslationY(0.0f);
                dispatchAddFinished(item);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(i);
                }
            }
        }
        dispatchFinishedWhenDone();
    }

    private void resetAnimation(RecyclerView.ViewHolder holder) {
        if (sDefaultInterpolator == null) {
            sDefaultInterpolator = new ValueAnimator().getInterpolator();
        }
        holder.itemView.animate().setInterpolator(sDefaultInterpolator);
        endAnimation(holder);
    }

    @Override
    public boolean isRunning() {
        return (!mPendingAdditions.isEmpty()
                || !mPendingChanges.isEmpty()
                || !mPendingMoves.isEmpty()
                || !mPendingRemovals.isEmpty()
                || !mMoveAnimations.isEmpty()
                || !mRemoveAnimations.isEmpty()
                || !mAddAnimations.isEmpty()
                || !mChangeAnimations.isEmpty()
                || !mMovesList.isEmpty()
                || !mAdditionsList.isEmpty()
                || !mChangesList.isEmpty());
    }

    public boolean isAnimatingChild(View child) {
        if (!shouldClipChildren) {
            return false;
        }
        for (int a = 0, N = mRemoveAnimations.size(); a < N; a++) {
            if (mRemoveAnimations.get(a).itemView == child) {
                return true;
            }
        }
        for (int a = 0, N = mAddAnimations.size(); a < N; a++) {
            if (mAddAnimations.get(a).itemView == child) {
                return true;
            }
        }
        return false;
    }

    public void setShouldClipChildren(boolean value) {
        shouldClipChildren = value;
    }

    public int getAnimationClipTop() {
        if (!shouldClipChildren) {
            return 0;
        }
        if (!mRemoveAnimations.isEmpty()) {
            int top = Integer.MAX_VALUE;
            for (int a = 0, N = mRemoveAnimations.size(); a < N; a++) {
                top = Math.min(top, mRemoveAnimations.get(a).itemView.getTop());
            }
            return top;
        }
        if (!mAddAnimations.isEmpty()) {
            int top = Integer.MAX_VALUE;
            for (int a = 0, N = mAddAnimations.size(); a < N; a++) {
                top = Math.min(top, mAddAnimations.get(a).itemView.getTop());
            }
            return top;
        }
        return 0;
    }

    void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
            onAllAnimationsDone();
        }
    }

    protected void onAllAnimationsDone() {

    }

    @Override
    public void endAnimations() {
        int count = mPendingMoves.size();
        for (int i = count - 1; i >= 0; i--) {
            MoveInfo item = mPendingMoves.get(i);
            View view = item.holder.itemView;
            view.setTranslationY(0);
            view.setTranslationX(0);
            dispatchMoveFinished(item.holder);
            mPendingMoves.remove(i);
        }
        count = mPendingRemovals.size();
        for (int i = count - 1; i >= 0; i--) {
            RecyclerView.ViewHolder item = mPendingRemovals.get(i);
            dispatchRemoveFinished(item);
            mPendingRemovals.remove(i);
        }
        count = mPendingAdditions.size();
        for (int i = count - 1; i >= 0; i--) {
            RecyclerView.ViewHolder item = mPendingAdditions.get(i);
            item.itemView.setTranslationY(0.0f);
            dispatchAddFinished(item);
            mPendingAdditions.remove(i);
        }
        count = mPendingChanges.size();
        for (int i = count - 1; i >= 0; i--) {
            endChangeAnimationIfNecessary(mPendingChanges.get(i));
        }
        mPendingChanges.clear();
        if (!isRunning()) {
            return;
        }

        int listCount = mMovesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            count = moves.size();
            for (int j = count - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                RecyclerView.ViewHolder item = moveInfo.holder;
                View view = item.itemView;
                view.setTranslationY(0);
                view.setTranslationX(0);
                dispatchMoveFinished(moveInfo.holder);
                moves.remove(j);
                if (moves.isEmpty()) {
                    mMovesList.remove(moves);
                }
            }
        }
        listCount = mAdditionsList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<RecyclerView.ViewHolder> additions = mAdditionsList.get(i);
            count = additions.size();
            for (int j = count - 1; j >= 0; j--) {
                RecyclerView.ViewHolder item = additions.get(j);
                View view = item.itemView;
                view.setTranslationY(0.0f);
                dispatchAddFinished(item);
                additions.remove(j);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(additions);
                }
            }
        }
        listCount = mChangesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            count = changes.size();
            for (int j = count - 1; j >= 0; j--) {
                endChangeAnimationIfNecessary(changes.get(j));
                if (changes.isEmpty()) {
                    mChangesList.remove(changes);
                }
            }
        }

        cancelAll(mRemoveAnimations);
        cancelAll(mMoveAnimations);
        cancelAll(mAddAnimations);
        cancelAll(mChangeAnimations);

        dispatchAnimationsFinished();
    }

    void cancelAll(List<RecyclerView.ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            viewHolders.get(i).itemView.animate().cancel();
        }
    }

    @Override
    public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder, List<Object> payloads) {
        return !payloads.isEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads);
    }
}
