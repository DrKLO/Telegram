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

import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Implementation of the {@link SnapHelper} supporting pager style snapping in either vertical or
 * horizontal orientation.
 *
 * <p>
 *
 * PagerSnapHelper can help achieve a similar behavior to
 * {@link androidx.viewpager.widget.ViewPager}. Set both {@link RecyclerView} and the items of the
 * {@link RecyclerView.Adapter} to have
 * {@link android.view.ViewGroup.LayoutParams#MATCH_PARENT} height and width and then attach
 * PagerSnapHelper to the {@link RecyclerView} using {@link #attachToRecyclerView(RecyclerView)}.
 */
public class PagerSnapHelper extends SnapHelper {
    private static final int MAX_SCROLL_ON_FLING_DURATION = 100; // ms

    // Orientation helpers are lazily created per LayoutManager.
    @Nullable
    private OrientationHelper mVerticalHelper;
    @Nullable
    private OrientationHelper mHorizontalHelper;

    @Nullable
    @Override
    public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager,
            @NonNull View targetView) {
        int[] out = new int[2];
        if (layoutManager.canScrollHorizontally()) {
            out[0] = distanceToCenter(layoutManager, targetView,
                    getHorizontalHelper(layoutManager));
        } else {
            out[0] = 0;
        }

        if (layoutManager.canScrollVertically()) {
            out[1] = distanceToCenter(layoutManager, targetView,
                    getVerticalHelper(layoutManager));
        } else {
            out[1] = 0;
        }
        return out;
    }

    @Nullable
    @Override
    public View findSnapView(RecyclerView.LayoutManager layoutManager) {
        if (layoutManager.canScrollVertically()) {
            return findCenterView(layoutManager, getVerticalHelper(layoutManager));
        } else if (layoutManager.canScrollHorizontally()) {
            return findCenterView(layoutManager, getHorizontalHelper(layoutManager));
        }
        return null;
    }

    @Override
    public int findTargetSnapPosition(RecyclerView.LayoutManager layoutManager, int velocityX,
            int velocityY) {
        final int itemCount = layoutManager.getItemCount();
        if (itemCount == 0) {
            return RecyclerView.NO_POSITION;
        }

        final OrientationHelper orientationHelper = getOrientationHelper(layoutManager);
        if (orientationHelper == null) {
            return RecyclerView.NO_POSITION;
        }

        // A child that is exactly in the center is eligible for both before and after
        View closestChildBeforeCenter = null;
        int distanceBefore = Integer.MIN_VALUE;
        View closestChildAfterCenter = null;
        int distanceAfter = Integer.MAX_VALUE;

        // Find the first view before the center, and the first view after the center
        final int childCount = layoutManager.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = layoutManager.getChildAt(i);
            if (child == null) {
                continue;
            }
            final int distance = distanceToCenter(layoutManager, child, orientationHelper);

            if (distance <= 0 && distance > distanceBefore) {
                // Child is before the center and closer then the previous best
                distanceBefore = distance;
                closestChildBeforeCenter = child;
            }
            if (distance >= 0 && distance < distanceAfter) {
                // Child is after the center and closer then the previous best
                distanceAfter = distance;
                closestChildAfterCenter = child;
            }
        }

        // Return the position of the first child from the center, in the direction of the fling
        final boolean forwardDirection = isForwardFling(layoutManager, velocityX, velocityY);
        if (forwardDirection && closestChildAfterCenter != null) {
            return layoutManager.getPosition(closestChildAfterCenter);
        } else if (!forwardDirection && closestChildBeforeCenter != null) {
            return layoutManager.getPosition(closestChildBeforeCenter);
        }

        // There is no child in the direction of the fling. Either it doesn't exist (start/end of
        // the list), or it is not yet attached (very rare case when children are larger then the
        // viewport). Extrapolate from the child that is visible to get the position of the view to
        // snap to.
        View visibleView = forwardDirection ? closestChildBeforeCenter : closestChildAfterCenter;
        if (visibleView == null) {
            return RecyclerView.NO_POSITION;
        }
        int visiblePosition = layoutManager.getPosition(visibleView);
        int snapToPosition = visiblePosition
                + (isReverseLayout(layoutManager) == forwardDirection ? -1 : +1);

        if (snapToPosition < 0 || snapToPosition >= itemCount) {
            return RecyclerView.NO_POSITION;
        }
        return snapToPosition;
    }

    private boolean isForwardFling(RecyclerView.LayoutManager layoutManager, int velocityX,
            int velocityY) {
        if (layoutManager.canScrollHorizontally()) {
            return velocityX > 0;
        } else {
            return velocityY > 0;
        }
    }

    private boolean isReverseLayout(RecyclerView.LayoutManager layoutManager) {
        final int itemCount = layoutManager.getItemCount();
        if ((layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            RecyclerView.SmoothScroller.ScrollVectorProvider vectorProvider =
                    (RecyclerView.SmoothScroller.ScrollVectorProvider) layoutManager;
            PointF vectorForEnd = vectorProvider.computeScrollVectorForPosition(itemCount - 1);
            if (vectorForEnd != null) {
                return vectorForEnd.x < 0 || vectorForEnd.y < 0;
            }
        }
        return false;
    }

    @Override
    protected LinearSmoothScroller createSnapScroller(RecyclerView.LayoutManager layoutManager) {
        if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            return null;
        }
        return new LinearSmoothScroller(mRecyclerView.getContext()) {
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                int[] snapDistances = calculateDistanceToFinalSnap(mRecyclerView.getLayoutManager(),
                        targetView);
                final int dx = snapDistances[0];
                final int dy = snapDistances[1];
                final int time = calculateTimeForDeceleration(Math.max(Math.abs(dx), Math.abs(dy)));
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator);
                }
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
            }

            @Override
            protected int calculateTimeForScrolling(int dx) {
                return Math.min(MAX_SCROLL_ON_FLING_DURATION, super.calculateTimeForScrolling(dx));
            }
        };
    }

    private int distanceToCenter(@NonNull RecyclerView.LayoutManager layoutManager,
            @NonNull View targetView, OrientationHelper helper) {
        final int childCenter = helper.getDecoratedStart(targetView)
                + (helper.getDecoratedMeasurement(targetView) / 2);
        final int containerCenter;
        if (layoutManager.getClipToPadding()) {
            containerCenter = helper.getStartAfterPadding() + helper.getTotalSpace() / 2;
        } else {
            containerCenter = helper.getEnd() / 2;
        }
        return childCenter - containerCenter;
    }

    /**
     * Return the child view that is currently closest to the center of this parent.
     *
     * @param layoutManager The {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}.
     * @param helper The relevant {@link OrientationHelper} for the attached {@link RecyclerView}.
     *
     * @return the child view that is currently closest to the center of this parent.
     */
    @Nullable
    private View findCenterView(RecyclerView.LayoutManager layoutManager,
            OrientationHelper helper) {
        int childCount = layoutManager.getChildCount();
        if (childCount == 0) {
            return null;
        }

        View closestChild = null;
        final int center;
        if (layoutManager.getClipToPadding()) {
            center = helper.getStartAfterPadding() + helper.getTotalSpace() / 2;
        } else {
            center = helper.getEnd() / 2;
        }
        int absClosest = Integer.MAX_VALUE;

        for (int i = 0; i < childCount; i++) {
            final View child = layoutManager.getChildAt(i);
            int childCenter = helper.getDecoratedStart(child)
                    + (helper.getDecoratedMeasurement(child) / 2);
            int absDistance = Math.abs(childCenter - center);

            /* if child center is closer than previous closest, set it as closest  */
            if (absDistance < absClosest) {
                absClosest = absDistance;
                closestChild = child;
            }
        }
        return closestChild;
    }

    @Nullable
    private OrientationHelper getOrientationHelper(RecyclerView.LayoutManager layoutManager) {
        if (layoutManager.canScrollVertically()) {
            return getVerticalHelper(layoutManager);
        } else if (layoutManager.canScrollHorizontally()) {
            return getHorizontalHelper(layoutManager);
        } else {
            return null;
        }
    }

    @NonNull
    private OrientationHelper getVerticalHelper(@NonNull RecyclerView.LayoutManager layoutManager) {
        if (mVerticalHelper == null || mVerticalHelper.mLayoutManager != layoutManager) {
            mVerticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return mVerticalHelper;
    }

    @NonNull
    private OrientationHelper getHorizontalHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        if (mHorizontalHelper == null || mHorizontalHelper.mLayoutManager != layoutManager) {
            mHorizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
        }
        return mHorizontalHelper;
    }
}
