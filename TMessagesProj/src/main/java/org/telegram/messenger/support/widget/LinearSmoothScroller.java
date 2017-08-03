/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.telegram.messenger.support.widget;

import android.content.Context;
import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * {@link RecyclerView.SmoothScroller} implementation which uses a {@link LinearInterpolator} until
 * the target position becomes a child of the RecyclerView and then uses a
 * {@link DecelerateInterpolator} to slowly approach to target position.
 * <p>
 * If the {@link RecyclerView.LayoutManager} you are using does not implement the
 * {@link RecyclerView.SmoothScroller.ScrollVectorProvider} interface, then you must override the
 * {@link #computeScrollVectorForPosition(int)} method. All the LayoutManagers bundled with
 * the support library implement this interface.
 */
public class LinearSmoothScroller extends RecyclerView.SmoothScroller {

    private static final String TAG = "LinearSmoothScroller";

    private static final boolean DEBUG = false;

    private static final float MILLISECONDS_PER_INCH = 25f;

    private static final int TARGET_SEEK_SCROLL_DISTANCE_PX = 10000;

    /**
     * Align child view's left or top with parent view's left or top
     *
     * @see #calculateDtToFit(int, int, int, int, int)
     * @see #calculateDxToMakeVisible(android.view.View, int)
     * @see #calculateDyToMakeVisible(android.view.View, int)
     */
    public static final int SNAP_TO_START = -1;

    /**
     * Align child view's right or bottom with parent view's right or bottom
     *
     * @see #calculateDtToFit(int, int, int, int, int)
     * @see #calculateDxToMakeVisible(android.view.View, int)
     * @see #calculateDyToMakeVisible(android.view.View, int)
     */
    public static final int SNAP_TO_END = 1;

    /**
     * <p>Decides if the child should be snapped from start or end, depending on where it
     * currently is in relation to its parent.</p>
     * <p>For instance, if the view is virtually on the left of RecyclerView, using
     * {@code SNAP_TO_ANY} is the same as using {@code SNAP_TO_START}</p>
     *
     * @see #calculateDtToFit(int, int, int, int, int)
     * @see #calculateDxToMakeVisible(android.view.View, int)
     * @see #calculateDyToMakeVisible(android.view.View, int)
     */
    public static final int SNAP_TO_ANY = 0;

    // Trigger a scroll to a further distance than TARGET_SEEK_SCROLL_DISTANCE_PX so that if target
    // view is not laid out until interim target position is reached, we can detect the case before
    // scrolling slows down and reschedule another interim target scroll
    private static final float TARGET_SEEK_EXTRA_SCROLL_RATIO = 1.2f;

    protected final LinearInterpolator mLinearInterpolator = new LinearInterpolator();

    protected final DecelerateInterpolator mDecelerateInterpolator = new DecelerateInterpolator();

    protected PointF mTargetVector;

    private final float MILLISECONDS_PER_PX;

    // Temporary variables to keep track of the interim scroll target. These values do not
    // point to a real item position, rather point to an estimated location pixels.
    protected int mInterimTargetDx = 0, mInterimTargetDy = 0;

    public LinearSmoothScroller(Context context) {
        MILLISECONDS_PER_PX = calculateSpeedPerPixel(context.getResources().getDisplayMetrics());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
        final int dx = calculateDxToMakeVisible(targetView, getHorizontalSnapPreference());
        final int dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference());
        final int distance = (int) Math.sqrt(dx * dx + dy * dy);
        final int time = calculateTimeForDeceleration(distance);
        if (time > 0) {
            action.update(-dx, -dy, time, mDecelerateInterpolator);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSeekTargetStep(int dx, int dy, RecyclerView.State state, Action action) {
        if (getChildCount() == 0) {
            stop();
            return;
        }
        //noinspection PointlessBooleanExpression
        if (DEBUG && mTargetVector != null
                && ((mTargetVector.x * dx < 0 || mTargetVector.y * dy < 0))) {
            throw new IllegalStateException("Scroll happened in the opposite direction"
                    + " of the target. Some calculations are wrong");
        }
        mInterimTargetDx = clampApplyScroll(mInterimTargetDx, dx);
        mInterimTargetDy = clampApplyScroll(mInterimTargetDy, dy);

        if (mInterimTargetDx == 0 && mInterimTargetDy == 0) {
            updateActionForInterimTarget(action);
        } // everything is valid, keep going

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        mInterimTargetDx = mInterimTargetDy = 0;
        mTargetVector = null;
    }

    /**
     * Calculates the scroll speed.
     *
     * @param displayMetrics DisplayMetrics to be used for real dimension calculations
     * @return The time (in ms) it should take for each pixel. For instance, if returned value is
     * 2 ms, it means scrolling 1000 pixels with LinearInterpolation should take 2 seconds.
     */
    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
    }

    /**
     * <p>Calculates the time for deceleration so that transition from LinearInterpolator to
     * DecelerateInterpolator looks smooth.</p>
     *
     * @param dx Distance to scroll
     * @return Time for DecelerateInterpolator to smoothly traverse the distance when transitioning
     * from LinearInterpolation
     */
    protected int calculateTimeForDeceleration(int dx) {
        // we want to cover same area with the linear interpolator for the first 10% of the
        // interpolation. After that, deceleration will take control.
        // area under curve (1-(1-x)^2) can be calculated as (1 - x/3) * x * x
        // which gives 0.100028 when x = .3356
        // this is why we divide linear scrolling time with .3356
        return  (int) Math.ceil(calculateTimeForScrolling(dx) / .3356);
    }

    /**
     * Calculates the time it should take to scroll the given distance (in pixels)
     *
     * @param dx Distance in pixels that we want to scroll
     * @return Time in milliseconds
     * @see #calculateSpeedPerPixel(android.util.DisplayMetrics)
     */
    protected int calculateTimeForScrolling(int dx) {
        // In a case where dx is very small, rounding may return 0 although dx > 0.
        // To avoid that issue, ceil the result so that if dx > 0, we'll always return positive
        // time.
        return (int) Math.ceil(Math.abs(dx) * MILLISECONDS_PER_PX);
    }

    /**
     * When scrolling towards a child view, this method defines whether we should align the left
     * or the right edge of the child with the parent RecyclerView.
     *
     * @return SNAP_TO_START, SNAP_TO_END or SNAP_TO_ANY; depending on the current target vector
     * @see #SNAP_TO_START
     * @see #SNAP_TO_END
     * @see #SNAP_TO_ANY
     */
    protected int getHorizontalSnapPreference() {
        return mTargetVector == null || mTargetVector.x == 0 ? SNAP_TO_ANY :
                mTargetVector.x > 0 ? SNAP_TO_END : SNAP_TO_START;
    }

    /**
     * When scrolling towards a child view, this method defines whether we should align the top
     * or the bottom edge of the child with the parent RecyclerView.
     *
     * @return SNAP_TO_START, SNAP_TO_END or SNAP_TO_ANY; depending on the current target vector
     * @see #SNAP_TO_START
     * @see #SNAP_TO_END
     * @see #SNAP_TO_ANY
     */
    protected int getVerticalSnapPreference() {
        return mTargetVector == null || mTargetVector.y == 0 ? SNAP_TO_ANY :
                mTargetVector.y > 0 ? SNAP_TO_END : SNAP_TO_START;
    }

    /**
     * When the target scroll position is not a child of the RecyclerView, this method calculates
     * a direction vector towards that child and triggers a smooth scroll.
     *
     * @see #computeScrollVectorForPosition(int)
     */
    protected void updateActionForInterimTarget(Action action) {
        // find an interim target position
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
        // To avoid UI hiccups, trigger a smooth scroll to a distance little further than the
        // interim target. Since we track the distance travelled in onSeekTargetStep callback, it
        // won't actually scroll more than what we need.
        action.update((int) (mInterimTargetDx * TARGET_SEEK_EXTRA_SCROLL_RATIO)
                , (int) (mInterimTargetDy * TARGET_SEEK_EXTRA_SCROLL_RATIO)
                , (int) (time * TARGET_SEEK_EXTRA_SCROLL_RATIO), mLinearInterpolator);
    }

    private int clampApplyScroll(int tmpDt, int dt) {
        final int before = tmpDt;
        tmpDt -= dt;
        if (before * tmpDt <= 0) { // changed sign, reached 0 or was 0, reset
            return 0;
        }
        return tmpDt;
    }

    /**
     * Helper method for {@link #calculateDxToMakeVisible(android.view.View, int)} and
     * {@link #calculateDyToMakeVisible(android.view.View, int)}
     */
    public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int
            snapPreference) {
        switch (snapPreference) {
            case SNAP_TO_START:
                return boxStart - viewStart;
            case SNAP_TO_END:
                return boxEnd - viewEnd;
            case SNAP_TO_ANY:
                final int dtStart = boxStart - viewStart;
                if (dtStart > 0) {
                    return dtStart;
                }
                final int dtEnd = boxEnd - viewEnd;
                if (dtEnd < 0) {
                    return dtEnd;
                }
                break;
            default:
                throw new IllegalArgumentException("snap preference should be one of the"
                        + " constants defined in SmoothScroller, starting with SNAP_");
        }
        return 0;
    }

    /**
     * Calculates the vertical scroll amount necessary to make the given view fully visible
     * inside the RecyclerView.
     *
     * @param view           The view which we want to make fully visible
     * @param snapPreference The edge which the view should snap to when entering the visible
     *                       area. One of {@link #SNAP_TO_START}, {@link #SNAP_TO_END} or
     *                       {@link #SNAP_TO_ANY}.
     * @return The vertical scroll amount necessary to make the view visible with the given
     * snap preference.
     */
    public int calculateDyToMakeVisible(View view, int snapPreference) {
        final RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null || !layoutManager.canScrollVertically()) {
            return 0;
        }
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                view.getLayoutParams();
        final int top = layoutManager.getDecoratedTop(view) - params.topMargin;
        final int bottom = layoutManager.getDecoratedBottom(view) + params.bottomMargin;
        final int start = layoutManager.getPaddingTop();
        final int end = layoutManager.getHeight() - layoutManager.getPaddingBottom();
        return calculateDtToFit(top, bottom, start, end, snapPreference);
    }

    /**
     * Calculates the horizontal scroll amount necessary to make the given view fully visible
     * inside the RecyclerView.
     *
     * @param view           The view which we want to make fully visible
     * @param snapPreference The edge which the view should snap to when entering the visible
     *                       area. One of {@link #SNAP_TO_START}, {@link #SNAP_TO_END} or
     *                       {@link #SNAP_TO_END}
     * @return The vertical scroll amount necessary to make the view visible with the given
     * snap preference.
     */
    public int calculateDxToMakeVisible(View view, int snapPreference) {
        final RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null || !layoutManager.canScrollHorizontally()) {
            return 0;
        }
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                view.getLayoutParams();
        final int left = layoutManager.getDecoratedLeft(view) - params.leftMargin;
        final int right = layoutManager.getDecoratedRight(view) + params.rightMargin;
        final int start = layoutManager.getPaddingLeft();
        final int end = layoutManager.getWidth() - layoutManager.getPaddingRight();
        return calculateDtToFit(left, right, start, end, snapPreference);
    }

    /**
     * Compute the scroll vector for a given target position.
     * <p>
     * This method can return null if the layout manager cannot calculate a scroll vector
     * for the given position (e.g. it has no current scroll position).
     *
     * @param targetPosition the position to which the scroller is scrolling
     *
     * @return the scroll vector for a given target position
     */
    @Nullable
    public PointF computeScrollVectorForPosition(int targetPosition) {
        RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager instanceof ScrollVectorProvider) {
            return ((ScrollVectorProvider) layoutManager)
                    .computeScrollVectorForPosition(targetPosition);
        }
        Log.w(TAG, "You should override computeScrollVectorForPosition when the LayoutManager" +
                " does not implement " + ScrollVectorProvider.class.getCanonicalName());
        return null;
    }
}
