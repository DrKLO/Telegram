package org.telegram.ui.Components.Crop;

import android.content.Context;
import androidx.core.view.MotionEventCompat;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;

public class CropGestureDetector {
    private ScaleGestureDetector mDetector;
    private CropGestureListener mListener;
    private float mLastTouchX;
    private float mLastTouchY;
    private final float mTouchSlop;
    private final float mMinimumVelocity;
    private VelocityTracker mVelocityTracker;
    private boolean mIsDragging;

    private static final int INVALID_POINTER_ID = -1;
    private int mActivePointerId;
    private int mActivePointerIndex;

    private long touchTime;

    private boolean started;

    public interface CropGestureListener {
        void onDrag(float dx, float dy);
        void onFling(float startX, float startY, float velocityX, float velocityY);
        void onScale(float scaleFactor, float focusX, float focusY);
        void onTapUp();
    }

    public CropGestureDetector(Context context) {
        final ViewConfiguration configuration = ViewConfiguration
                .get(context);
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mTouchSlop = AndroidUtilities.dp(1);

        this.mActivePointerId = INVALID_POINTER_ID;
        this.mActivePointerIndex = 0;

        ScaleGestureDetector.OnScaleGestureListener mScaleListener = new ScaleGestureDetector.OnScaleGestureListener() {

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();

                if (Float.isNaN(scaleFactor) || Float.isInfinite(scaleFactor))
                    return false;

                mListener.onScale(scaleFactor,
                        detector.getFocusX(), detector.getFocusY());
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                // NO-OP
            }
        };
        mDetector = new ScaleGestureDetector(context, mScaleListener);
    }

    float getActiveX(MotionEvent ev) {
        try {
            return ev.getX(this.mActivePointerIndex);
        } catch (Exception e) {
            return ev.getX();
        }
    }

    float getActiveY(MotionEvent ev) {
        try {
            return ev.getY(this.mActivePointerIndex);
        } catch (Exception e) {
            return ev.getY();
        }
    }

    public void setOnGestureListener(CropGestureListener listener) {
        this.mListener = listener;
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    public boolean isDragging() {
        return mIsDragging;
    }


    public boolean onTouchEvent(MotionEvent ev) {
        mDetector.onTouchEvent(ev);

        int i = 0;
        switch (ev.getAction() & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                this.mActivePointerId = ev.getPointerId(0);
                touchTime = SystemClock.elapsedRealtime();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (!mIsDragging && SystemClock.elapsedRealtime() - touchTime < 800) {
                    mListener.onTapUp();
                }
                this.mActivePointerId = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int pointerIndex = (MotionEventCompat.ACTION_POINTER_INDEX_MASK & ev.getAction()) >> 8;
                if (ev.getPointerId(pointerIndex) == this.mActivePointerId) {
                    int newPointerIndex;
                    if (pointerIndex == 0) {
                        newPointerIndex = 1;
                    } else {
                        newPointerIndex = 0;
                    }
                    this.mActivePointerId = ev.getPointerId(newPointerIndex);
                    this.mLastTouchX = ev.getX(newPointerIndex);
                    this.mLastTouchY = ev.getY(newPointerIndex);
                    break;
                }
                break;
        }
        if (this.mActivePointerId != INVALID_POINTER_ID) {
            i = this.mActivePointerId;
        }
        this.mActivePointerIndex = ev.findPointerIndex(i);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (!started) {
                    mVelocityTracker = VelocityTracker.obtain();
                    if (null != mVelocityTracker) {
                        mVelocityTracker.addMovement(ev);
                    }

                    mLastTouchX = getActiveX(ev);
                    mLastTouchY = getActiveY(ev);
                    mIsDragging = false;

                    started = true;
                    return true;
                }

                final float x = getActiveX(ev);
                final float y = getActiveY(ev);
                final float dx = x - mLastTouchX, dy = y - mLastTouchY;

                if (!mIsDragging) {
                    // Use Pythagoras to see if drag length is larger than
                    // touch slop
                    mIsDragging = (float) Math.sqrt((dx * dx) + (dy * dy)) >= mTouchSlop;
                }

                if (mIsDragging) {
                    mListener.onDrag(dx, dy);
                    mLastTouchX = x;
                    mLastTouchY = y;

                    if (null != mVelocityTracker) {
                        mVelocityTracker.addMovement(ev);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                // Recycle Velocity Tracker
                if (null != mVelocityTracker) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                started = false;
                mIsDragging = false;
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mIsDragging) {
                    if (null != mVelocityTracker) {
                        mLastTouchX = getActiveX(ev);
                        mLastTouchY = getActiveY(ev);

                        // Compute velocity within the last 1000ms
                        mVelocityTracker.addMovement(ev);
                        mVelocityTracker.computeCurrentVelocity(1000);

                        final float vX = mVelocityTracker.getXVelocity(), vY = mVelocityTracker
                                .getYVelocity();

                        // If the velocity is greater than minVelocity, call
                        // listener
                        if (Math.max(Math.abs(vX), Math.abs(vY)) >= mMinimumVelocity) {
                            mListener.onFling(mLastTouchX, mLastTouchY, -vX,
                                    -vY);
                        }
                    }

                    mIsDragging = false;
                }

                // Recycle Velocity Tracker
                if (null != mVelocityTracker) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                started = false;
                break;
            }
        }

        return true;
    }

}