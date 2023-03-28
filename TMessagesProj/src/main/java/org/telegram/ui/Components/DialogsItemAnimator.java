package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.os.Build;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.telegram.ui.Adapters.DialogsAdapter;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogsEmptyCell;

import java.util.ArrayList;
import java.util.List;

public class DialogsItemAnimator extends SimpleItemAnimator {
    private static final boolean DEBUG = false;

    private static TimeInterpolator sDefaultInterpolator = new DecelerateInterpolator();

    private ArrayList<ViewHolder> mPendingRemovals = new ArrayList<>();
    private ArrayList<ViewHolder> mPendingAdditions = new ArrayList<>();
    private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

    ArrayList<ArrayList<ViewHolder>> mAdditionsList = new ArrayList<>();
    ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
    ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();

    ArrayList<ViewHolder> mAddAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mMoveAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mRemoveAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mChangeAnimations = new ArrayList<>();

    private DialogCell removingDialog;
    private int topClip;
    private int bottomClip;

    private final static int deleteDuration = 180;
    private final static int changeDuration = 180;

    private static class MoveInfo {
        public ViewHolder holder;
        public int fromX, fromY, toX, toY;

        MoveInfo(ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    private final RecyclerListView listView;

    public DialogsItemAnimator(RecyclerListView listView) {
        setSupportsChangeAnimations(false);
        this.listView = listView;
    }

    private static class ChangeInfo {
        public ViewHolder oldHolder, newHolder;
        public int fromX, fromY, toX, toY;

        private ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder) {
            this.oldHolder = oldHolder;
            this.newHolder = newHolder;
        }

        ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder,
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

    @Override
    public void runPendingAnimations() {
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            // nothing to animate
            return;
        }
        // First, remove stuff
        for (ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();
        // Next, move stuff
        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();
            Runnable mover = () -> {
                for (MoveInfo moveInfo : moves) {
                    animateMoveImpl(moveInfo.holder, null, moveInfo.fromX, moveInfo.fromY, moveInfo.toX, moveInfo.toY);
                }
                moves.clear();
                mMovesList.remove(moves);
            };
            mover.run();
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {
            final ArrayList<ChangeInfo> changes = new ArrayList<>(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();
            Runnable changer = () -> {
                for (ChangeInfo change : changes) {
                    animateChangeImpl(change);
                }
                changes.clear();
                mChangesList.remove(changes);
            };
            changer.run();
        }
        // Next, add stuff
        if (additionsPending) {
            final ArrayList<ViewHolder> additions = new ArrayList<>(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();
            Runnable adder = () -> {
                for (ViewHolder holder : additions) {
                    animateAddImpl(holder);
                }
                additions.clear();
                mAdditionsList.remove(additions);
            };
            adder.run();
        }
    }

    @Override
    public boolean animateRemove(final ViewHolder holder, ItemHolderInfo info) {
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        int top = Integer.MIN_VALUE;
        DialogCell bottomView = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view.getTop() > top && view instanceof DialogCell) {
                bottomView = (DialogCell) view;
            }
        }
        if (holder.itemView == bottomView) {
            removingDialog = bottomView;
        }
        return true;
    }

    public void prepareForRemove() {
        topClip = Integer.MAX_VALUE;
        bottomClip = Integer.MAX_VALUE;
        removingDialog = null;
    }

    private void animateRemoveImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        mRemoveAnimations.add(holder);
        if (view instanceof DialogCell) {
            DialogCell dialogCell = (DialogCell) view;
            if (view == removingDialog) {
                if (topClip != Integer.MAX_VALUE) {
                    bottomClip = removingDialog.getMeasuredHeight() - topClip;
                    removingDialog.setTopClip(topClip);
                    removingDialog.setBottomClip(bottomClip);
                } else if (bottomClip != Integer.MAX_VALUE) {
                    topClip = removingDialog.getMeasuredHeight() - bottomClip;
                    removingDialog.setTopClip(topClip);
                    removingDialog.setBottomClip(bottomClip);
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    dialogCell.setElevation(-1);
                    dialogCell.setOutlineProvider(null);
                }
                final ObjectAnimator animator = ObjectAnimator.ofFloat(dialogCell, AnimationProperties.CLIP_DIALOG_CELL_PROGRESS, 1.0f)
                        .setDuration(deleteDuration);
                animator.setInterpolator(sDefaultInterpolator);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animator.removeAllListeners();
                        dialogCell.setClipProgress(0.0f);
                        if (Build.VERSION.SDK_INT >= 21) {
                            dialogCell.setElevation(0);
                        }
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                });
                animator.start();
            } else {
                final ObjectAnimator animator = ObjectAnimator.ofFloat(dialogCell, View.ALPHA, 1.0f).setDuration(deleteDuration);
                animator.setInterpolator(sDefaultInterpolator);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animator.removeAllListeners();
                        dialogCell.setClipProgress(0.0f);
                        if (Build.VERSION.SDK_INT >= 21) {
                            dialogCell.setElevation(0);
                        }
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                });
                animator.start();
            }
        } else {
            final ViewPropertyAnimator animation = view.animate();
            animation.setDuration(deleteDuration).alpha(0).setListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            dispatchRemoveStarting(holder);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            animation.setListener(null);
                            view.setAlpha(1);
                            dispatchRemoveFinished(holder);
                            mRemoveAnimations.remove(holder);
                            dispatchFinishedWhenDone();
                        }
                    }).start();
        }
    }

    @Override
    public boolean animateAdd(final ViewHolder holder) {
        resetAnimation(holder);
        if (!(holder.itemView instanceof DialogCell)) {
            holder.itemView.setAlpha(0);
        }

        mPendingAdditions.add(holder);
        if (mPendingAdditions.size() > 2) {
            for (int i = 0; i < mPendingAdditions.size(); i++) {
                mPendingAdditions.get(i).itemView.setAlpha(0);
                if (mPendingAdditions.get(i).itemView instanceof DialogCell) {
                    ((DialogCell) mPendingAdditions.get(i).itemView).setMoving(true);
                }
            }
        }
        return true;
    }

    void animateAddImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        mAddAnimations.add(holder);

        final ViewPropertyAnimator animation = view.animate();
        animation.alpha(1).setDuration(deleteDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchAddStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setAlpha(1);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        dispatchAddFinished(holder);
                        mAddAnimations.remove(holder);
                        dispatchFinishedWhenDone();

                        if (holder.itemView instanceof DialogCell) {
                            ((DialogCell) holder.itemView).setMoving(false);
                        }
                    }
                }).start();
    }

    @Override
    public boolean animateMove(final ViewHolder holder, ItemHolderInfo info, int fromX, int fromY,
                               int toX, int toY) {
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
        if (holder.itemView instanceof DialogCell) {
            ((DialogCell) holder.itemView).setMoving(true);
        } else if (holder.itemView instanceof DialogsAdapter.LastEmptyView) {
            ((DialogsAdapter.LastEmptyView) holder.itemView).moving = true;
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    public void onListScroll(int dy) {
        if (!mPendingRemovals.isEmpty()) {
            for (int a = 0, N = mPendingRemovals.size(); a < N; a++) {
                ViewHolder holder = mPendingRemovals.get(a);
                holder.itemView.setTranslationY(holder.itemView.getTranslationY() + dy);
            }
        }
        if (!mRemoveAnimations.isEmpty()) {
            for (int a = 0, N = mRemoveAnimations.size(); a < N; a++) {
                ViewHolder holder = mRemoveAnimations.get(a);
                holder.itemView.setTranslationY(holder.itemView.getTranslationY() + dy);
            }
        }
    }

    void animateMoveImpl(final ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            view.animate().translationX(0);
        }
        if (deltaY != 0) {
            view.animate().translationY(0);
        }
        if (fromY > toY) {
            bottomClip = fromY - toY;
        } else {
            topClip = toY - fromY;
        }
        if (removingDialog != null) {
            if (topClip != Integer.MAX_VALUE) {
                bottomClip = removingDialog.getMeasuredHeight() - topClip;
                removingDialog.setTopClip(topClip);
                removingDialog.setBottomClip(bottomClip);
            } else if (bottomClip != Integer.MAX_VALUE) {
                topClip = removingDialog.getMeasuredHeight() - bottomClip;
                removingDialog.setTopClip(topClip);
                removingDialog.setBottomClip(bottomClip);
            }
        }
        // TODO: make EndActions end listeners instead, since end actions aren't called when
        // vpas are canceled (and can't end them. why?)
        // need listener functionality in VPACompat for this. Ick.
        final ViewPropertyAnimator animation = view.animate();
        mMoveAnimations.add(holder);
        animation.setDuration(deleteDuration).setListener(new AnimatorListenerAdapter() {
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

                if (holder.itemView instanceof DialogCell) {
                    ((DialogCell) holder.itemView).setMoving(false);
                } else if (holder.itemView instanceof DialogsAdapter.LastEmptyView) {
                    ((DialogsAdapter.LastEmptyView) holder.itemView).moving = false;
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animation.setListener(null);
                dispatchMoveFinished(holder);
                mMoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
                if (holder.itemView instanceof DialogCell) {
                    ((DialogCell) holder.itemView).setMoving(false);
                } else if (holder.itemView instanceof DialogsAdapter.LastEmptyView) {
                    ((DialogsAdapter.LastEmptyView) holder.itemView).moving = false;
                }

                view.setTranslationX(0);
                view.setTranslationY(0);
            }
        }).start();
    }

    @Override
    public boolean animateChange(ViewHolder oldHolder, ViewHolder newHolder,ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
        if (oldHolder.itemView instanceof DialogCell) {
            resetAnimation(oldHolder);
            resetAnimation(newHolder);
            oldHolder.itemView.setAlpha(1.0f);
            newHolder.itemView.setAlpha(0.0f);
            newHolder.itemView.setTranslationX(0.0f);
            mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
            return true;
        }
        return false;
    }

    void animateChangeImpl(final ChangeInfo changeInfo) {
        final ViewHolder holder = changeInfo.oldHolder;
        final ViewHolder newHolder = changeInfo.newHolder;
        if (holder == null || newHolder == null) {
            return;
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(changeDuration);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(newHolder.itemView, View.ALPHA, 1.0f));

        mChangeAnimations.add(changeInfo.oldHolder);
        mChangeAnimations.add(changeInfo.newHolder);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchChangeStarting(changeInfo.oldHolder, true);
                dispatchChangeStarting(changeInfo.newHolder, false);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                holder.itemView.setAlpha(1);

                animatorSet.removeAllListeners();

                dispatchChangeFinished(changeInfo.oldHolder, true);
                mChangeAnimations.remove(changeInfo.oldHolder);
                dispatchFinishedWhenDone();

                dispatchChangeFinished(changeInfo.newHolder, false);
                mChangeAnimations.remove(changeInfo.newHolder);
                dispatchFinishedWhenDone();
            }
        });
        animatorSet.start();
    }

    private void endChangeAnimation(List<ChangeInfo> infoList, ViewHolder item) {
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

    private boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, ViewHolder item) {
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
    public void endAnimation(ViewHolder item) {
        final View view = item.itemView;
        // this will trigger end callback which should set properties to their target values.
        view.animate().cancel();
        // TODO if some other animations are chained to end, how do we cancel them as well?
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
            if (view instanceof DialogCell) {
                ((DialogCell) view).setClipProgress(0.0f);
            } else {
                view.setAlpha(1);
            }
            dispatchRemoveFinished(item);
        }
        if (mPendingAdditions.remove(item)) {
            if (view instanceof DialogCell) {
                ((DialogCell) view).setClipProgress(0.0f);
            } else {
                view.setAlpha(1);
            }
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
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            if (additions.remove(item)) {
                if (view instanceof DialogCell) {
                    ((DialogCell) view).setClipProgress(1.0f);
                } else {
                    view.setAlpha(1);
                }
                dispatchAddFinished(item);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(i);
                }
            }
        }

        if (mRemoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mRemoveAnimations list");
        }

        if (mAddAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mAddAnimations list");
        }

        if (mChangeAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mChangeAnimations list");
        }

        if (mMoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mMoveAnimations list");
        }
        dispatchFinishedWhenDone();
    }

    private void resetAnimation(ViewHolder holder) {
        holder.itemView.animate().setInterpolator(sDefaultInterpolator);
        endAnimation(holder);
    }

    @Override
    public boolean isRunning() {
        return (!mPendingAdditions.isEmpty()
                || !mPendingChanges.isEmpty()
                || !mPendingMoves.isEmpty()
                || !mPendingChanges.isEmpty()
                || !mMoveAnimations.isEmpty()
                || !mRemoveAnimations.isEmpty()
                || !mAddAnimations.isEmpty()
                || !mChangeAnimations.isEmpty()
                || !mMovesList.isEmpty()
                || !mAdditionsList.isEmpty()
                || !mChangesList.isEmpty());
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
            ViewHolder item = mPendingRemovals.get(i);
            View view = item.itemView;
            view.setTranslationY(0);
            view.setTranslationX(0);
            dispatchRemoveFinished(item);
            mPendingRemovals.remove(i);
        }
        count = mPendingAdditions.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder item = mPendingAdditions.get(i);
            if (item.itemView instanceof DialogCell) {
                ((DialogCell) item.itemView).setClipProgress(0.0f);
            } else {
                item.itemView.setAlpha(1);
            }
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
                ViewHolder item = moveInfo.holder;
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
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            count = additions.size();
            for (int j = count - 1; j >= 0; j--) {
                ViewHolder item = additions.get(j);
                View view = item.itemView;
                if (view instanceof DialogCell) {
                    ((DialogCell) view).setClipProgress(0.0f);
                } else {
                    view.setAlpha(1);
                }
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

    void cancelAll(List<ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            viewHolders.get(i).itemView.animate().cancel();
        }
    }

    @Override
    public boolean canReuseUpdatedViewHolder(ViewHolder viewHolder, List<Object> payloads) {
        return viewHolder.itemView instanceof DialogsEmptyCell;
    }
}
