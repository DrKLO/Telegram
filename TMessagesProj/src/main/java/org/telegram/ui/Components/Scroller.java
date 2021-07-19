/*
 * Copyright (C) 2006 The Android Open Source Project
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

package org.telegram.ui.Components;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;


/**
 * This class encapsulates scrolling.  The duration of the scroll
 * can be passed in the constructor and specifies the maximum time that
 * the scrolling animation should take.  Past this time, the scrolling is 
 * automatically moved to its final stage and computeScrollOffset()
 * will always return false to indicate that scrolling is over.
 */
public class Scroller  {
    private int mMode;

    private int mStartX;
    private int mStartY;
    private int mFinalX;
    private int mFinalY;

    private int mMinX;
    private int mMaxX;
    private int mMinY;
    private int mMaxY;

    private int mCurrX;
    private int mCurrY;
    private long mStartTime;
    private int mDuration;
    private float mDurationReciprocal;
    private float mDeltaX;
    private float mDeltaY;
    private boolean mFinished;
    private Interpolator mInterpolator;
    private boolean mFlywheel;

    private float mVelocity;

    private static final int DEFAULT_DURATION = 250;
    private static final int SCROLL_MODE = 0;
    private static final int FLING_MODE = 1;

    private static float DECELERATION_RATE = (float) (Math.log(0.75) / Math.log(0.9));
    private static float START_TENSION = 0.4f; // Tension at start: (0.4 * total T, 1.0 * Distance)
    private static float END_TENSION = 1.0f - START_TENSION;
    private static final int NB_SAMPLES = 100;
    private static final float[] SPLINE = new float[NB_SAMPLES + 1];

    private float mDeceleration;
    private final float mPpi;

    static {
        float x_min = 0.0f;
        for (int i = 0; i <= NB_SAMPLES; i++) {
            final float t = (float) i / NB_SAMPLES;
            float x_max = 1.0f;
            float x, tx, coef;
            while (true) {
                x = x_min + (x_max - x_min) / 2.0f;
                coef = 3.0f * x * (1.0f - x);
                tx = coef * ((1.0f - x) * START_TENSION + x * END_TENSION) + x * x * x;
                if (Math.abs(tx - t) < 1E-5) break;
                if (tx > t) x_max = x;
                else x_min = x;
            }
            final float d = coef + x * x * x;
            SPLINE[i] = d;
        }
        SPLINE[NB_SAMPLES] = 1.0f;

        // This controls the viscous fluid effect (how much of it)
        sViscousFluidScale = 8.0f;
        // must be set to 1.0 (used in viscousFluid())
        sViscousFluidNormalize = 1.0f;
        sViscousFluidNormalize = 1.0f / viscousFluid(1.0f);
    }

    private static float sViscousFluidScale;
    private static float sViscousFluidNormalize;

    /**
     * Create a Scroller with the default duration and interpolator.
     */
    public Scroller(Context context) {
        this(context, null);
    }

    /**
     * Create a Scroller with the specified interpolator. If the interpolator is
     * null, the default (viscous) interpolator will be used. "Flywheel" behavior will
     * be in effect for apps targeting Honeycomb or newer.
     */
    public Scroller(Context context, Interpolator interpolator) {
        this(context, interpolator, true);
    }

    /**
     * Create a Scroller with the specified interpolator. If the interpolator is
     * null, the default (viscous) interpolator will be used. Specify whether or
     * not to support progressive "flywheel" behavior in flinging.
     */
    public Scroller(Context context, Interpolator interpolator, boolean flywheel) {
        mFinished = true;
        mInterpolator = interpolator;
        mPpi = context.getResources().getDisplayMetrics().density * 160.0f;
        mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction());
        mFlywheel = flywheel;
    }

    /**
     * The amount of friction applied to flings. The default value
     * is {@link android.view.ViewConfiguration#getScrollFriction}.
     * 
     * @param friction A scalar dimension-less value representing the coefficient of
     *         friction.
     */
    public final void setFriction(float friction) {
        mDeceleration = computeDeceleration(friction);
    }
    
    private float computeDeceleration(float friction) {
        return SensorManager.GRAVITY_EARTH   // g (m/s^2)
                      * 39.37f               // inch/meter
                      * mPpi                 // pixels per inch
                      * friction;
    }

    /**
     * 
     * Returns whether the scroller has finished scrolling.
     * 
     * @return True if the scroller has finished scrolling, false otherwise.
     */
    public final boolean isFinished() {
        return mFinished;
    }
    
    /**
     * Force the finished field to a particular value.
     *  
     * @param finished The new finished value.
     */
    public final void forceFinished(boolean finished) {
        mFinished = finished;
    }
    
    /**
     * Returns how long the scroll event will take, in milliseconds.
     * 
     * @return The duration of the scroll in milliseconds.
     */
    public final int getDuration() {
        return mDuration;
    }
    
    /**
     * Returns the current X offset in the scroll. 
     * 
     * @return The new X offset as an absolute distance from the origin.
     */
    public final int getCurrX() {
        return mCurrX;
    }
    
    /**
     * Returns the current Y offset in the scroll. 
     * 
     * @return The new Y offset as an absolute distance from the origin.
     */
    public final int getCurrY() {
        return mCurrY;
    }
    
    /**
     * Returns the current velocity.
     *
     * @return The original velocity less the deceleration. Result may be
     * negative.
     */
    public float getCurrVelocity() {
        return mVelocity - mDeceleration * timePassed() / 2000.0f;
    }

    /**
     * Returns the start X offset in the scroll. 
     * 
     * @return The start X offset as an absolute distance from the origin.
     */
    public final int getStartX() {
        return mStartX;
    }
    
    /**
     * Returns the start Y offset in the scroll. 
     * 
     * @return The start Y offset as an absolute distance from the origin.
     */
    public final int getStartY() {
        return mStartY;
    }
    
    /**
     * Returns where the scroll will end. Valid only for "fling" scrolls.
     * 
     * @return The final X offset as an absolute distance from the origin.
     */
    public final int getFinalX() {
        return mFinalX;
    }
    
    /**
     * Returns where the scroll will end. Valid only for "fling" scrolls.
     * 
     * @return The final Y offset as an absolute distance from the origin.
     */
    public final int getFinalY() {
        return mFinalY;
    }

    /**
     * Call this when you want to know the new location.  If it returns true,
     * the animation is not yet finished.  loc will be altered to provide the
     * new location.
     */ 
    public boolean computeScrollOffset() {
        if (mFinished) {
            return false;
        }

        int timePassed = (int)(AnimationUtils.currentAnimationTimeMillis() - mStartTime);
    
        if (timePassed < mDuration) {
            switch (mMode) {
            case SCROLL_MODE:
                float x = timePassed * mDurationReciprocal;
    
                if (mInterpolator == null)
                    x = viscousFluid(x); 
                else
                    x = mInterpolator.getInterpolation(x);
    
                mCurrX = mStartX + Math.round(x * mDeltaX);
                mCurrY = mStartY + Math.round(x * mDeltaY);
                break;
            case FLING_MODE:
                final float t = (float) timePassed / mDuration;
                final int index = (int) (NB_SAMPLES * t);
                final float t_inf = (float) index / NB_SAMPLES;
                final float t_sup = (float) (index + 1) / NB_SAMPLES;
                final float d_inf = SPLINE[index];
                final float d_sup = SPLINE[index + 1];
                final float distanceCoef = d_inf + (t - t_inf) / (t_sup - t_inf) * (d_sup - d_inf);
                
                mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX));
                // Pin to mMinX <= mCurrX <= mMaxX
                mCurrX = Math.min(mCurrX, mMaxX);
                mCurrX = Math.max(mCurrX, mMinX);
                
                mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY));
                // Pin to mMinY <= mCurrY <= mMaxY
                mCurrY = Math.min(mCurrY, mMaxY);
                mCurrY = Math.max(mCurrY, mMinY);

                if (mCurrX == mFinalX && mCurrY == mFinalY) {
                    mFinished = true;
                }

                break;
            }
        }
        else {
            mCurrX = mFinalX;
            mCurrY = mFinalY;
            mFinished = true;
        }
        return true;
    }
    
    /**
     * Start scrolling by providing a starting point and the distance to travel.
     * The scroll will use the default value of 250 milliseconds for the
     * duration.
     * 
     * @param startX Starting horizontal scroll offset in pixels. Positive
     *        numbers will scroll the content to the left.
     * @param startY Starting vertical scroll offset in pixels. Positive numbers
     *        will scroll the content up.
     * @param dx Horizontal distance to travel. Positive numbers will scroll the
     *        content to the left.
     * @param dy Vertical distance to travel. Positive numbers will scroll the
     *        content up.
     */
    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION);
    }

    /**
     * Start scrolling by providing a starting point and the distance to travel.
     * 
     * @param startX Starting horizontal scroll offset in pixels. Positive
     *        numbers will scroll the content to the left.
     * @param startY Starting vertical scroll offset in pixels. Positive numbers
     *        will scroll the content up.
     * @param dx Horizontal distance to travel. Positive numbers will scroll the
     *        content to the left.
     * @param dy Vertical distance to travel. Positive numbers will scroll the
     *        content up.
     * @param duration Duration of the scroll in milliseconds.
     */
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mMode = SCROLL_MODE;
        mFinished = false;
        mDuration = duration;
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mStartX = startX;
        mStartY = startY;
        mFinalX = startX + dx;
        mFinalY = startY + dy;
        mDeltaX = dx;
        mDeltaY = dy;
        mDurationReciprocal = 1.0f / (float) mDuration;
    }

    /**
     * Start scrolling based on a fling gesture. The distance travelled will
     * depend on the initial velocity of the fling.
     * 
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per
     *        second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per
     *        second
     * @param minX Minimum X value. The scroller will not scroll past this
     *        point.
     * @param maxX Maximum X value. The scroller will not scroll past this
     *        point.
     * @param minY Minimum Y value. The scroller will not scroll past this
     *        point.
     * @param maxY Maximum Y value. The scroller will not scroll past this
     *        point.
     */
    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY) {
        // Continue a scroll or fling in progress
        if (mFlywheel && !mFinished) {
            float oldVel = getCurrVelocity();

            float dx = (float) (mFinalX - mStartX);
            float dy = (float) (mFinalY - mStartY);
            float hyp = (float) Math.sqrt(dx * dx + dy * dy);

            float ndx = dx / hyp;
            float ndy = dy / hyp;

            float oldVelocityX = ndx * oldVel;
            float oldVelocityY = ndy * oldVel;
            if (Math.signum(velocityX) == Math.signum(oldVelocityX) &&
                    Math.signum(velocityY) == Math.signum(oldVelocityY)) {
                velocityX += oldVelocityX;
                velocityY += oldVelocityY;
            }
        }

        mMode = FLING_MODE;
        mFinished = false;

        float velocity = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
     
        mVelocity = velocity;
        float ALPHA = 800;
        final double l = Math.log(START_TENSION * velocity / ALPHA);
        mDuration = (int) (1000.0 * Math.exp(l / (DECELERATION_RATE - 1.0)));
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mStartX = startX;
        mStartY = startY;

        float coeffX = velocity == 0 ? 1.0f : velocityX / velocity;
        float coeffY = velocity == 0 ? 1.0f : velocityY / velocity;

        int totalDistance =
                (int) (ALPHA * Math.exp(DECELERATION_RATE / (DECELERATION_RATE - 1.0) * l));
        
        mMinX = minX;
        mMaxX = maxX;
        mMinY = minY;
        mMaxY = maxY;

        mFinalX = startX + Math.round(totalDistance * coeffX);
        // Pin to mMinX <= mFinalX <= mMaxX
        mFinalX = Math.min(mFinalX, mMaxX);
        mFinalX = Math.max(mFinalX, mMinX);
        
        mFinalY = startY + Math.round(totalDistance * coeffY);
        // Pin to mMinY <= mFinalY <= mMaxY
        mFinalY = Math.min(mFinalY, mMaxY);
        mFinalY = Math.max(mFinalY, mMinY);
    }
    
    static float viscousFluid(float x)
    {
        x *= sViscousFluidScale;
        if (x < 1.0f) {
            x -= (1.0f - (float)Math.exp(-x));
        } else {
            float start = 0.36787944117f;   // 1/e == exp(-1)
            x = 1.0f - (float)Math.exp(1.0f - x);
            x = start + x * (1.0f - start);
        }
        x *= sViscousFluidNormalize;
        return x;
    }
    
    /**
     * Stops the animation. Contrary to {@link #forceFinished(boolean)},
     * aborting the animating cause the scroller to move to the final x and y
     * position
     *
     * @see #forceFinished(boolean)
     */
    public void abortAnimation() {
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
    }
    
    /**
     * Extend the scroll animation. This allows a running animation to scroll
     * further and longer, when used with {@link #setFinalX(int)} or {@link #setFinalY(int)}.
     *
     * @param extend Additional time to scroll in milliseconds.
     * @see #setFinalX(int)
     * @see #setFinalY(int)
     */
    public void extendDuration(int extend) {
        int passed = timePassed();
        mDuration = passed + extend;
        mDurationReciprocal = 1.0f / mDuration;
        mFinished = false;
    }

    /**
     * Returns the time elapsed since the beginning of the scrolling.
     *
     * @return The elapsed time in milliseconds.
     */
    public int timePassed() {
        return (int)(AnimationUtils.currentAnimationTimeMillis() - mStartTime);
    }

    /**
     * Sets the final position (X) for this scroller.
     *
     * @param newX The new X offset as an absolute distance from the origin.
     * @see #extendDuration(int)
     * @see #setFinalY(int)
     */
    public void setFinalX(int newX) {
        mFinalX = newX;
        mDeltaX = mFinalX - mStartX;
        mFinished = false;
    }

    /**
     * Sets the final position (Y) for this scroller.
     *
     * @param newY The new Y offset as an absolute distance from the origin.
     * @see #extendDuration(int)
     * @see #setFinalX(int)
     */
    public void setFinalY(int newY) {
        mFinalY = newY;
        mDeltaY = mFinalY - mStartY;
        mFinished = false;
    }

    public boolean isScrollingInDirection(float xvel, float yvel) {
        return !mFinished && Math.signum(xvel) == Math.signum(mFinalX - mStartX) &&
                Math.signum(yvel) == Math.signum(mFinalY - mStartY);
    }
}
