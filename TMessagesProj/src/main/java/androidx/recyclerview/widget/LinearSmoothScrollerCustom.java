/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package androidx.recyclerview.widget;

import android.content.Context;
import android.graphics.PointF;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class LinearSmoothScrollerCustom extends RecyclerView.SmoothScroller {

    private static final float MILLISECONDS_PER_INCH = 25f;

    private static final int TARGET_SEEK_SCROLL_DISTANCE_PX = 10000;

    private static final float TARGET_SEEK_EXTRA_SCROLL_RATIO = 1.2f;

    protected final LinearInterpolator mLinearInterpolator = new LinearInterpolator();

    protected final DecelerateInterpolator mDecelerateInterpolator = new DecelerateInterpolator(1.5f);

    protected PointF mTargetVector;

    private final float MILLISECONDS_PER_PX;

    protected int mInterimTargetDx = 0, mInterimTargetDy = 0;
    private int scrollPosition;

    public static final int POSITION_MIDDLE = 0;
    public static final int POSITION_END = 1;
    public static final int POSITION_TOP = 2;

    private float durationMultiplier = 1f;
    private int offset;

    public LinearSmoothScrollerCustom(Context context, int position) {
        MILLISECONDS_PER_PX = MILLISECONDS_PER_INCH / context.getResources().getDisplayMetrics().densityDpi;
        scrollPosition = position;
    }

    public LinearSmoothScrollerCustom(Context context, int position, float durationMultiplier) {
        this.durationMultiplier = durationMultiplier;
        MILLISECONDS_PER_PX = MILLISECONDS_PER_INCH / context.getResources().getDisplayMetrics().densityDpi * durationMultiplier;
        scrollPosition = position;
    }

    @Override
    protected void onStart() {

    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
        final int dy = calculateDyToMakeVisible(targetView);
        final int time = calculateTimeForDeceleration(dy);
        if (time > 0) {
            action.update(0, -dy, Math.max((int) (400 * durationMultiplier), time), mDecelerateInterpolator);
        } else {
            onEnd();
        }
    }

    @Override
    protected void onSeekTargetStep(int dx, int dy, RecyclerView.State state, Action action) {
        if (getChildCount() == 0) {
            stop();
            return;
        }
        mInterimTargetDx = clampApplyScroll(mInterimTargetDx, dx);
        mInterimTargetDy = clampApplyScroll(mInterimTargetDy, dy);

        if (mInterimTargetDx == 0 && mInterimTargetDy == 0) {
            updateActionForInterimTarget(action);
        }
    }

    @Override
    protected void onStop() {
        mInterimTargetDx = mInterimTargetDy = 0;
        mTargetVector = null;
    }

    protected int calculateTimeForDeceleration(int dx) {
        return (int) Math.ceil(calculateTimeForScrolling(dx) / .3356);
    }

    protected int calculateTimeForScrolling(int dx) {
        return (int) Math.ceil(Math.abs(dx) * MILLISECONDS_PER_PX);
    }

    protected void updateActionForInterimTarget(Action action) {
        PointF scrollVector = computeScrollVectorForPosition(getTargetPosition());
        if (scrollVector == null || (scrollVector.x == 0 && scrollVector.y == 0)) {
            final int target = getTargetPosition();
            action.jumpTo(target);
            stop();
            return;
        }
        normalize(scrollVector);
        mTargetVector = scrollVector;

        mInterimTargetDx = (int) (TARGET_SEEK_SCROLL_DISTANCE_PX * scrollVector.x);
        mInterimTargetDy = (int) (TARGET_SEEK_SCROLL_DISTANCE_PX * scrollVector.y);
        final int time = calculateTimeForScrolling(TARGET_SEEK_SCROLL_DISTANCE_PX);
        action.update((int) (mInterimTargetDx * TARGET_SEEK_EXTRA_SCROLL_RATIO), (int) (mInterimTargetDy * TARGET_SEEK_EXTRA_SCROLL_RATIO), (int) (time * TARGET_SEEK_EXTRA_SCROLL_RATIO), mLinearInterpolator);
    }

    private int clampApplyScroll(int tmpDt, int dt) {
        final int before = tmpDt;
        tmpDt -= dt;
        if (before * tmpDt <= 0) {
            return 0;
        }
        return tmpDt;
    }

    public int calculateDyToMakeVisible(View view) {
        final RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null || !layoutManager.canScrollVertically()) {
            return 0;
        }
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        int top = layoutManager.getDecoratedTop(view) - params.topMargin;
        int bottom = layoutManager.getDecoratedBottom(view) + params.bottomMargin;
        int start = layoutManager.getPaddingTop();
        int end = layoutManager.getHeight() - layoutManager.getPaddingBottom();

        int boxSize = end - start;
        int viewSize = bottom - top;
        if (scrollPosition == POSITION_TOP) {
            start = layoutManager.getPaddingTop() + offset;
        } else if (viewSize > boxSize) {
            start = 0;
        } else if (scrollPosition == POSITION_MIDDLE) {
            start = (boxSize - viewSize) / 2;
        } else {
            start = (layoutManager.getPaddingTop() + offset - AndroidUtilities.dp(88));
        }
        end = start + viewSize;
        final int dtStart = start - top;
        if (dtStart > 0) {
            return dtStart;
        }
        final int dtEnd = end - bottom;
        if (dtEnd < 0) {
            return dtEnd;
        }
        return 0;
    }

    @Nullable
    public PointF computeScrollVectorForPosition(int targetPosition) {
        RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager instanceof ScrollVectorProvider) {
            return ((ScrollVectorProvider) layoutManager).computeScrollVectorForPosition(targetPosition);
        }
        return null;
    }

    public void onEnd() {

    }
}
