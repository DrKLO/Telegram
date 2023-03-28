/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.recyclerview.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.BuildVars;

import java.util.ArrayList;
import java.util.List;

/**
 * This implementation of {@link RecyclerView.ItemAnimator} provides basic
 * animations on remove, add, and move events that happen to the items in
 * a RecyclerView. RecyclerView uses a DefaultItemAnimator by default.
 *
 * @see RecyclerView#setItemAnimator(RecyclerView.ItemAnimator)
 */
public class DefaultItemAnimator extends SimpleItemAnimator {
    private static final boolean DEBUG = BuildVars.DEBUG_VERSION;

    private static TimeInterpolator sDefaultInterpolator;
    protected Interpolator translationInterpolator;

    protected ArrayList<RecyclerView.ViewHolder> mPendingRemovals = new ArrayList<>();
    protected ArrayList<RecyclerView.ViewHolder> mPendingAdditions = new ArrayList<>();
    protected ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    protected ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

    ArrayList<ArrayList<RecyclerView.ViewHolder>> mAdditionsList = new ArrayList<>();
    ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
    ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();
    ArrayList<MoveInfo> currentMoves = new ArrayList<>();
    ArrayList<ChangeInfo> currentChanges = new ArrayList<>();

    protected ArrayList<RecyclerView.ViewHolder> mAddAnimations = new ArrayList<>();
    protected ArrayList<RecyclerView.ViewHolder> mMoveAnimations = new ArrayList<>();
    protected ArrayList<RecyclerView.ViewHolder> mRemoveAnimations = new ArrayList<>();
    ArrayList<RecyclerView.ViewHolder> mChangeAnimations = new ArrayList<>();

    protected boolean delayAnimations = true;

    protected static class MoveInfo {
        public RecyclerView.ViewHolder holder;
        public int fromX, fromY, toX, toY;

        public MoveInfo(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    protected static class ChangeInfo {
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
        for (RecyclerView.ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();
        // Next, move stuff
        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>();
            moves.addAll(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();
            Runnable mover = new Runnable() {
                @Override
                public void run() {
                    for (MoveInfo moveInfo : moves) {
                        animateMoveImpl(moveInfo.holder, moveInfo);
                        currentMoves.add(moveInfo);
                    }
                    moves.clear();
                    mMovesList.remove(moves);
                }
            };
            if (delayAnimations && removalsPending) {
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, getMoveAnimationDelay());
            } else {
                mover.run();
            }
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {
            final ArrayList<ChangeInfo> changes = new ArrayList<>();
            changes.addAll(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();
            Runnable changer = new Runnable() {
                @Override
                public void run() {
                    for (ChangeInfo change : changes) {
                        animateChangeImpl(change);
                        currentChanges.add(change);
                    }
                    changes.clear();
                    mChangesList.remove(changes);
                }
            };
            if (delayAnimations && removalsPending) {
                RecyclerView.ViewHolder holder = changes.get(0).oldHolder;
                ViewCompat.postOnAnimationDelayed(holder.itemView, changer, getRemoveDuration());
            } else {
                changer.run();
            }
        }
        // Next, add stuff
        if (additionsPending) {
            final ArrayList<RecyclerView.ViewHolder> additions = new ArrayList<>();
            additions.addAll(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();
            Runnable adder = new Runnable() {
                @Override
                public void run() {
                    for (RecyclerView.ViewHolder holder : additions) {
                        animateAddImpl(holder);
                    }
                    additions.clear();
                    mAdditionsList.remove(additions);
                }
            };
            if (delayAnimations && (removalsPending || movesPending || changesPending)) {
                long removeDuration = removalsPending ? getRemoveDuration() : 0;
                long moveDuration = movesPending ? getMoveDuration() : 0;
                long changeDuration = changesPending ? getChangeDuration() : 0;
                long totalDelay = getAddAnimationDelay(removeDuration, moveDuration, changeDuration);
                View view = additions.get(0).itemView;
                ViewCompat.postOnAnimationDelayed(view, adder, totalDelay);
            } else {
                adder.run();
            }
        }
    }

    protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
        return removeDuration + Math.max(moveDuration, changeDuration);
    }

    protected long getMoveAnimationDelay() {
        return getRemoveDuration();
    }

    @Override
    public boolean animateRemove(final RecyclerView.ViewHolder holder, ItemHolderInfo info) {
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        checkIsRunning();
        return true;
    }

    protected float animateByScale(View view) {
        return 0; // animates from (1f - animateByScale()) to 1f
    }

    public void setDelayAnimations(boolean value) {
        delayAnimations = value;
    }

    protected void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mRemoveAnimations.add(holder);
        if (getRemoveDelay() > 0) {
            // wanted to achieve an effect of next items covering current
            view.bringToFront();
        }
        animation
            .setDuration(getRemoveDuration())
            .setStartDelay(getRemoveDelay())
            .setInterpolator(getRemoveInterpolator())
            .alpha(0)
            .scaleX(1f - animateByScale(view))
            .scaleY(1f - animateByScale(view))
            .setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        view.setAlpha(1);
                        if (animateByScale(view) > 0) {
                            view.setScaleX(1f);
                            view.setScaleY(1f);
                        }
                        view.setTranslationX(0);
                        view.setTranslationY(0);
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                }).start();
    }

    @Override
    public boolean animateAdd(final RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        holder.itemView.setAlpha(0);
        if (animateByScale(holder.itemView) > 0) {
            holder.itemView.setScaleX(1f - animateByScale(holder.itemView));
            holder.itemView.setScaleY(1f - animateByScale(holder.itemView));
        }
        mPendingAdditions.add(holder);
        checkIsRunning();
        return true;
    }

    public void animateAddImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);
        animation
            .alpha(1)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(getAddDuration())
            .setStartDelay(getAddDelay())
            .setInterpolator(getAddInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchAddStarting(holder);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                    view.setAlpha(1);
                    if (animateByScale(view) > 0) {
                        view.setScaleX(1f);
                        view.setScaleY(1f);
                    }
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
    public boolean animateMove(final RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY,
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
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        checkIsRunning();
        return true;
    }

    protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {

    }

    protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {

    }

    protected void beforeAnimateMoveImpl(final RecyclerView.ViewHolder holder) {

    }

    protected void afterAnimateMoveImpl(final RecyclerView.ViewHolder holder) {

    }

    protected void beforeAnimateChangeImpl(final RecyclerView.ViewHolder oldHolder, final RecyclerView.ViewHolder newHolder) {

    }

    protected void afterAnimateChangeImpl(final RecyclerView.ViewHolder oldHolder, final RecyclerView.ViewHolder newHolder) {

    }

    protected void animateMoveImpl(final RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
        int fromX = moveInfo.fromX;
        int fromY = moveInfo.fromY;
        int toX = moveInfo.toX;
        int toY = moveInfo.toY;
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            view.animate().translationX(0);
        }
        if (deltaY != 0) {
            view.animate().translationY(0);
        }
        // TODO: make EndActions end listeners instead, since end actions aren't called when
        // vpas are canceled (and can't end them. why?)
        // need listener functionality in VPACompat for this. Ick.
        final ViewPropertyAnimator animation = view.animate();
        mMoveAnimations.add(holder);
        if (Build.VERSION.SDK_INT >= 19) {
            animation.setUpdateListener(animation1 -> onMoveAnimationUpdate(holder));
        }
        if (translationInterpolator != null) {
            animation.setInterpolator(translationInterpolator);
        } else {
            animation.setInterpolator(getMoveInterpolator());
        }
        beforeAnimateMoveImpl(holder);
        animation
            .setDuration(getMoveDuration())
            .setStartDelay(getMoveDelay())
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

                    afterAnimateMoveImpl(holder);
                }
            })
            .start();
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, ItemHolderInfo info,
            int fromX, int fromY, int toX, int toY) {
        if (oldHolder == newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder,info, fromX, fromY, toX, toY);
        }
        final float prevTranslationX = oldHolder.itemView.getTranslationX();
        final float prevTranslationY = oldHolder.itemView.getTranslationY();
        final float prevAlpha = oldHolder.itemView.getAlpha();
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        // recover prev translation state after ending animation
        oldHolder.itemView.setTranslationX(prevTranslationX);
        oldHolder.itemView.setTranslationY(prevTranslationY);
        oldHolder.itemView.setAlpha(prevAlpha);
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder);
            newHolder.itemView.setTranslationX(-deltaX);
            newHolder.itemView.setTranslationY(-deltaY);
            newHolder.itemView.setAlpha(0);
            if (animateByScale(newHolder.itemView) > 0) {
                newHolder.itemView.setScaleX(1f - animateByScale(newHolder.itemView));
                newHolder.itemView.setScaleY(1f - animateByScale(newHolder.itemView));
            }
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        checkIsRunning();
        return true;
    }

    void animateChangeImpl(final ChangeInfo changeInfo) {
        final RecyclerView.ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final RecyclerView.ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        beforeAnimateChangeImpl(changeInfo.oldHolder, changeInfo.newHolder);
        if (view != null) {
            final ViewPropertyAnimator oldViewAnim = view.animate().setDuration(getChangeRemoveDuration()).setStartDelay(getChangeDelay());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
            oldViewAnim
                .alpha(0);
            if (animateByScale(view) > 0) {
                oldViewAnim
                    .scaleX(1f - animateByScale(view))
                    .scaleY(1f - animateByScale(view));
            }
            if (Build.VERSION.SDK_INT >= 19) {
                oldViewAnim.setUpdateListener(animation1 -> onChangeAnimationUpdate(changeInfo.oldHolder));
            }
            oldViewAnim
                .setInterpolator(getChangeInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchChangeStarting(changeInfo.oldHolder, true);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        oldViewAnim.setListener(null);
                        view.setAlpha(1);
                        if (animateByScale(view) > 0) {
                            view.setScaleX(1f);
                            view.setScaleY(1f);
                        }
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
            newViewAnimation
                .translationX(0).translationY(0)
                .setDuration(getChangeAddDuration())
                .setStartDelay(getChangeDelay() + (getChangeDuration() - getChangeAddDuration()))
                .setInterpolator(getChangeInterpolator())
                .alpha(1);
            if (animateByScale(newView) > 0) {
                newViewAnimation.scaleX(1f).scaleY(1f);
            }
            if (Build.VERSION.SDK_INT >= 19) {
                newViewAnimation.setUpdateListener(animation1 -> onChangeAnimationUpdate(changeInfo.newHolder));
            }
            newViewAnimation
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchChangeStarting(changeInfo.newHolder, false);
                    }
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        newViewAnimation.setListener(null);
                        newView.setAlpha(1);
                        if (animateByScale(newView) > 0) {
                            newView.setScaleX(1f);
                            newView.setScaleY(1f);
                        }
                        newView.setTranslationX(0);
                        newView.setTranslationY(0);
                        dispatchChangeFinished(changeInfo.newHolder, false);
                        mChangeAnimations.remove(changeInfo.newHolder);
                        dispatchFinishedWhenDone();

                        afterAnimateChangeImpl(changeInfo.oldHolder, changeInfo.newHolder);
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

    protected void endChangeAnimationIfNecessary(ChangeInfo changeInfo) {
        if (changeInfo.oldHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder);
        }
        if (changeInfo.newHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder);
        }
    }
    protected boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, RecyclerView.ViewHolder item) {
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
        if (animateByScale(item.itemView) > 0) {
            item.itemView.setScaleX(1f);
            item.itemView.setScaleY(1f);
        }
        item.itemView.setTranslationX(0);
        item.itemView.setTranslationY(0);
        dispatchChangeFinished(item, oldItem);
        return true;
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
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
            view.setAlpha(1);
            view.setScaleX(1f);
            view.setScaleY(1f);
            dispatchRemoveFinished(item);
        }
        if (mPendingAdditions.remove(item)) {
            view.setAlpha(1);
            view.setScaleX(1f);
            view.setScaleY(1f);
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
                view.setAlpha(1);
                if (animateByScale(view) > 0) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                }
                dispatchAddFinished(item);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(i);
                }
            }
        }

        // animations should be ended by the cancel above.
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mRemoveAnimations.remove(item) && BuildVars.DEBUG_VERSION) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mRemoveAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mAddAnimations.remove(item) && BuildVars.DEBUG_VERSION) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mAddAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mChangeAnimations.remove(item) && BuildVars.DEBUG_VERSION) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mChangeAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mMoveAnimations.remove(item) && BuildVars.DEBUG_VERSION) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mMoveAnimations list");
        }
        dispatchFinishedWhenDone();
    }

    public void resetAnimation(RecyclerView.ViewHolder holder) {
        if (sDefaultInterpolator == null) {
            sDefaultInterpolator = new ValueAnimator().getInterpolator();
        }
        holder.itemView.animate().setInterpolator(sDefaultInterpolator);
        endAnimation(holder);
    }

    public float getTargetY(View view) {
        for (int i = currentMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = currentMoves.get(i);
            if (moveInfo.holder.itemView == view) {
                return Math.min(Math.min(moveInfo.toY, moveInfo.fromY), view.getY());
            }
        }
        for (int i = currentChanges.size() - 1; i >= 0; i--) {
            ChangeInfo changeInfo = currentChanges.get(i);
            if (changeInfo.oldHolder.itemView == view || changeInfo.newHolder.itemView == view) {
                return Math.min(Math.min(changeInfo.toY, changeInfo.fromY), view.getY());
            }
        }
        return view.getY();
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

    /**
     * Check the state of currently pending and running animations. If there are none
     * pending/running, call {@link #dispatchAnimationsFinished()} to notify any
     * listeners.
     */
    protected void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
            onAllAnimationsDone();
            currentMoves.clear();
            currentChanges.clear();
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
            item.itemView.setAlpha(1);
            if (animateByScale(item.itemView) > 0) {
                item.itemView.setScaleX(1f);
                item.itemView.setScaleY(1f);
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
                view.setAlpha(1);
                if (animateByScale(view) > 0) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
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

    void cancelAll(List<RecyclerView.ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            viewHolders.get(i).itemView.animate().cancel();
        }
    }

    public boolean isHolderRemoving(RecyclerView.ViewHolder holder) {
        return mRemoveAnimations.contains(holder);
    }

    public boolean isHolderAdding(RecyclerView.ViewHolder holder) {
        return mAddAnimations.contains(holder);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the payload list is not empty, DefaultItemAnimator returns <code>true</code>.
     * When this is the case:
     * <ul>
     * <li>If you override {@link #animateChange(RecyclerView.ViewHolder, RecyclerView.ViewHolder, int, int, int, int)}, both
     * ViewHolder arguments will be the same instance.
     * </li>
     * <li>
     * If you are not overriding {@link #animateChange(RecyclerView.ViewHolder, RecyclerView.ViewHolder, int, int, int, int)},
     * then DefaultItemAnimator will call {@link #animateMove(RecyclerView.ViewHolder, int, int, int, int)} and
     * run a move animation instead.
     * </li>
     * </ul>
     */
    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull List<Object> payloads) {
        return !payloads.isEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads);
    }

    public void setTranslationInterpolator(Interpolator translationInterpolator) {
        this.translationInterpolator = translationInterpolator;
    }

    public void checkIsRunning() {

    }
}
