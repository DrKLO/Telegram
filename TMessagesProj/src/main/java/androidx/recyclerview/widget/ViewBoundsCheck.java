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

import android.view.View;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A utility class used to check the boundaries of a given view within its parent view based on
 * a set of boundary flags.
 */
class ViewBoundsCheck {

    static final int GT = 1 << 0;
    static final int EQ = 1 << 1;
    static final int LT = 1 << 2;


    static final int CVS_PVS_POS = 0;
    /**
     * The child view's start should be strictly greater than parent view's start.
     */
    static final int FLAG_CVS_GT_PVS = GT << CVS_PVS_POS;

    /**
     * The child view's start can be equal to its parent view's start. This flag follows with GT
     * or LT indicating greater (less) than or equal relation.
     */
    static final int FLAG_CVS_EQ_PVS = EQ << CVS_PVS_POS;

    /**
     * The child view's start should be strictly less than parent view's start.
     */
    static final int FLAG_CVS_LT_PVS = LT << CVS_PVS_POS;


    static final int CVS_PVE_POS = 4;
    /**
     * The child view's start should be strictly greater than parent view's end.
     */
    static final int FLAG_CVS_GT_PVE = GT << CVS_PVE_POS;

    /**
     * The child view's start can be equal to its parent view's end. This flag follows with GT
     * or LT indicating greater (less) than or equal relation.
     */
    static final int FLAG_CVS_EQ_PVE = EQ << CVS_PVE_POS;

    /**
     * The child view's start should be strictly less than parent view's end.
     */
    static final int FLAG_CVS_LT_PVE = LT << CVS_PVE_POS;


    static final int CVE_PVS_POS = 8;
    /**
     * The child view's end should be strictly greater than parent view's start.
     */
    static final int FLAG_CVE_GT_PVS = GT << CVE_PVS_POS;

    /**
     * The child view's end can be equal to its parent view's start. This flag follows with GT
     * or LT indicating greater (less) than or equal relation.
     */
    static final int FLAG_CVE_EQ_PVS = EQ << CVE_PVS_POS;

    /**
     * The child view's end should be strictly less than parent view's start.
     */
    static final int FLAG_CVE_LT_PVS = LT << CVE_PVS_POS;


    static final int CVE_PVE_POS = 12;
    /**
     * The child view's end should be strictly greater than parent view's end.
     */
    static final int FLAG_CVE_GT_PVE = GT << CVE_PVE_POS;

    /**
     * The child view's end can be equal to its parent view's end. This flag follows with GT
     * or LT indicating greater (less) than or equal relation.
     */
    static final int FLAG_CVE_EQ_PVE = EQ << CVE_PVE_POS;

    /**
     * The child view's end should be strictly less than parent view's end.
     */
    static final int FLAG_CVE_LT_PVE = LT << CVE_PVE_POS;

    static final int MASK = GT | EQ | LT;

    final Callback mCallback;
    BoundFlags mBoundFlags;
    /**
     * The set of flags that can be passed for checking the view boundary conditions.
     * CVS in the flag name indicates the child view, and PV indicates the parent view.\
     * The following S, E indicate a view's start and end points, respectively.
     * GT and LT indicate a strictly greater and less than relationship.
     * Greater than or equal (or less than or equal) can be specified by setting both GT and EQ (or
     * LT and EQ) flags.
     * For instance, setting both {@link #FLAG_CVS_GT_PVS} and {@link #FLAG_CVS_EQ_PVS} indicate the
     * child view's start should be greater than or equal to its parent start.
     */
    @IntDef(flag = true, value = {
            FLAG_CVS_GT_PVS, FLAG_CVS_EQ_PVS, FLAG_CVS_LT_PVS,
            FLAG_CVS_GT_PVE, FLAG_CVS_EQ_PVE, FLAG_CVS_LT_PVE,
            FLAG_CVE_GT_PVS, FLAG_CVE_EQ_PVS, FLAG_CVE_LT_PVS,
            FLAG_CVE_GT_PVE, FLAG_CVE_EQ_PVE, FLAG_CVE_LT_PVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewBounds {}

    ViewBoundsCheck(Callback callback) {
        mCallback = callback;
        mBoundFlags = new BoundFlags();
    }

    static class BoundFlags {
        int mBoundFlags = 0;
        int mRvStart, mRvEnd, mChildStart, mChildEnd;

        void setBounds(int rvStart, int rvEnd, int childStart, int childEnd) {
            mRvStart = rvStart;
            mRvEnd = rvEnd;
            mChildStart = childStart;
            mChildEnd = childEnd;
        }

        void addFlags(@ViewBounds int flags) {
            mBoundFlags |= flags;
        }

        void resetFlags() {
            mBoundFlags = 0;
        }

        int compare(int x, int y) {
            if (x > y) {
                return GT;
            }
            if (x == y) {
                return EQ;
            }
            return LT;
        }

        boolean boundsMatch() {
            if ((mBoundFlags & (MASK << CVS_PVS_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildStart, mRvStart) << CVS_PVS_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVS_PVE_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildStart, mRvEnd) << CVS_PVE_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVE_PVS_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildEnd, mRvStart) << CVE_PVS_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVE_PVE_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildEnd, mRvEnd) << CVE_PVE_POS)) == 0) {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * Returns the first view starting from fromIndex to toIndex in views whose bounds lie within
     * its parent bounds based on the provided preferredBoundFlags. If no match is found based on
     * the preferred flags, and a nonzero acceptableBoundFlags is specified, the last view whose
     * bounds lie within its parent view based on the acceptableBoundFlags is returned. If no such
     * view is found based on either of these two flags, null is returned.
     * @param fromIndex The view position index to start the search from.
     * @param toIndex The view position index to end the search at.
     * @param preferredBoundFlags The flags indicating the preferred match. Once a match is found
     *                            based on this flag, that view is returned instantly.
     * @param acceptableBoundFlags The flags indicating the acceptable match if no preferred match
     *                             is found. If so, and if acceptableBoundFlags is non-zero, the
     *                             last matching acceptable view is returned. Otherwise, null is
     *                             returned.
     * @return The first view that satisfies acceptableBoundFlags or the last view satisfying
     * acceptableBoundFlags boundary conditions.
     */
    View findOneViewWithinBoundFlags(int fromIndex, int toIndex,
            @ViewBounds int preferredBoundFlags,
            @ViewBounds int acceptableBoundFlags) {
        final int start = mCallback.getParentStart();
        final int end = mCallback.getParentEnd();
        final int next = toIndex > fromIndex ? 1 : -1;
        View acceptableMatch = null;
        for (int i = fromIndex; i != toIndex; i += next) {
            final View child = mCallback.getChildAt(i);
            final int childStart = mCallback.getChildStart(child);
            final int childEnd = mCallback.getChildEnd(child);
            mBoundFlags.setBounds(start, end, childStart, childEnd);
            if (preferredBoundFlags != 0) {
                mBoundFlags.resetFlags();
                mBoundFlags.addFlags(preferredBoundFlags);
                if (mBoundFlags.boundsMatch()) {
                    // found a perfect match
                    return child;
                }
            }
            if (acceptableBoundFlags != 0) {
                mBoundFlags.resetFlags();
                mBoundFlags.addFlags(acceptableBoundFlags);
                if (mBoundFlags.boundsMatch()) {
                    acceptableMatch = child;
                }
            }
        }
        return acceptableMatch;
    }

    /**
     * Returns whether the specified view lies within the boundary condition of its parent view.
     * @param child The child view to be checked.
     * @param boundsFlags The flag against which the child view and parent view are matched.
     * @return True if the view meets the boundsFlag, false otherwise.
     */
    boolean isViewWithinBoundFlags(View child, @ViewBounds int boundsFlags) {
        mBoundFlags.setBounds(mCallback.getParentStart(), mCallback.getParentEnd(),
                mCallback.getChildStart(child), mCallback.getChildEnd(child));
        if (boundsFlags != 0) {
            mBoundFlags.resetFlags();
            mBoundFlags.addFlags(boundsFlags);
            return mBoundFlags.boundsMatch();
        }
        return false;
    }

    /**
     * Callback provided by the user of this class in order to retrieve information about child and
     * parent boundaries.
     */
    interface Callback {
        View getChildAt(int index);
        int getParentStart();
        int getParentEnd();
        int getChildStart(View view);
        int getChildEnd(View view);
    }
}
