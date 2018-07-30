/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Keep;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.CompoundButton;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class Switch extends CompoundButton {

    public static class Insets {
        public static final Insets NONE = new Insets(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);

        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        private Insets(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    public static float constrain(float amount, float low, float high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private static final int THUMB_ANIMATION_DURATION = 250;

    private static final int TOUCH_MODE_IDLE = 0;
    private static final int TOUCH_MODE_DOWN = 1;
    private static final int TOUCH_MODE_DRAGGING = 2;

    private Drawable mThumbDrawable;
    private Drawable mTrackDrawable;
    private int mThumbTextPadding;
    private int mSwitchMinWidth;
    private int mSwitchPadding;
    private boolean mSplitTrack;

    private boolean attachedToWindow;
    private boolean wasLayout;

    private int mTouchMode;
    private int mTouchSlop;
    private float mTouchX;
    private float mTouchY;
    private VelocityTracker mVelocityTracker = VelocityTracker.obtain();
    private int mMinFlingVelocity;

    private float thumbPosition;
    private int mSwitchWidth;
    private int mSwitchHeight;
    private int mThumbWidth;
    private int mSwitchLeft;
    private int mSwitchTop;
    private int mSwitchRight;
    private int mSwitchBottom;

    private ObjectAnimator mPositionAnimator;

    private final Rect mTempRect = new Rect();

    public Switch(Context context) {
        super(context);

        mThumbDrawable = context.getResources().getDrawable(R.drawable.switch_thumb);
        if (mThumbDrawable != null) {
            mThumbDrawable.setCallback(this);
        }
        mTrackDrawable = context.getResources().getDrawable(R.drawable.switch_track);
        if (mTrackDrawable != null) {
            mTrackDrawable.setCallback(this);
        }

        if (AndroidUtilities.density < 1) {
            mSwitchMinWidth = AndroidUtilities.dp(30);
        } else {
            mSwitchMinWidth = 0;
        }

        mSwitchPadding = 0;
        mSplitTrack = false;

        final ViewConfiguration config = ViewConfiguration.get(context);
        mTouchSlop = config.getScaledTouchSlop();
        mMinFlingVelocity = config.getScaledMinimumFlingVelocity();

        refreshDrawableState();
        setChecked(isChecked());
    }

    public void setSwitchPadding(int pixels) {
        mSwitchPadding = pixels;
        requestLayout();
    }

    public int getSwitchPadding() {
        return mSwitchPadding;
    }

    public void setSwitchMinWidth(int pixels) {
        mSwitchMinWidth = pixels;
        requestLayout();
    }

    public int getSwitchMinWidth() {
        return mSwitchMinWidth;
    }

    public void setThumbTextPadding(int pixels) {
        mThumbTextPadding = pixels;
        requestLayout();
    }

    public int getThumbTextPadding() {
        return mThumbTextPadding;
    }

    public void setTrackDrawable(Drawable track) {
        if (mTrackDrawable != null) {
            mTrackDrawable.setCallback(null);
        }
        mTrackDrawable = track;
        if (track != null) {
            track.setCallback(this);
        }
        requestLayout();
    }

    public Drawable getTrackDrawable() {
        return mTrackDrawable;
    }

    public void setThumbDrawable(Drawable thumb) {
        if (mThumbDrawable != null) {
            mThumbDrawable.setCallback(null);
        }
        mThumbDrawable = thumb;
        if (thumb != null) {
            thumb.setCallback(this);
        }
        requestLayout();
    }

    public Drawable getThumbDrawable() {
        return mThumbDrawable;
    }

    public void setSplitTrack(boolean splitTrack) {
        mSplitTrack = splitTrack;
        invalidate();
    }

    public boolean getSplitTrack() {
        return mSplitTrack;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final Rect padding = mTempRect;
        int thumbWidth;
        int thumbHeight;
        if (mThumbDrawable != null) {
            mThumbDrawable.getPadding(padding);
            thumbWidth = mThumbDrawable.getIntrinsicWidth() - padding.left - padding.right;
            thumbHeight = mThumbDrawable.getIntrinsicHeight();
        } else {
            thumbWidth = 0;
            thumbHeight = 0;
        }


        mThumbWidth = thumbWidth;

        int trackHeight;
        if (mTrackDrawable != null) {
            mTrackDrawable.getPadding(padding);
            trackHeight = mTrackDrawable.getIntrinsicHeight();
        } else {
            padding.setEmpty();
            trackHeight = 0;
        }

        int paddingLeft = padding.left;
        int paddingRight = padding.right;
        if (mThumbDrawable != null) {
            final Insets inset = Insets.NONE;
            paddingLeft = Math.max(paddingLeft, inset.left);
            paddingRight = Math.max(paddingRight, inset.right);
        }

        final int switchWidth = Math.max(mSwitchMinWidth, 2 * mThumbWidth + paddingLeft + paddingRight);
        final int switchHeight = Math.max(trackHeight, thumbHeight);
        mSwitchWidth = switchWidth;
        mSwitchHeight = switchHeight;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int measuredHeight = getMeasuredHeight();
        if (measuredHeight < switchHeight) {
            setMeasuredDimension(switchWidth, switchHeight);
        }
    }

    private boolean hitThumb(float x, float y) {
        final int thumbOffset = getThumbOffset();

        mThumbDrawable.getPadding(mTempRect);
        final int thumbTop = mSwitchTop - mTouchSlop;
        final int thumbLeft = mSwitchLeft + thumbOffset - mTouchSlop;
        final int thumbRight = thumbLeft + mThumbWidth + mTempRect.left + mTempRect.right + mTouchSlop;
        final int thumbBottom = mSwitchBottom + mTouchSlop;
        return x > thumbLeft && x < thumbRight && y > thumbTop && y < thumbBottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                if (isEnabled() && hitThumb(x, y)) {
                    mTouchMode = TOUCH_MODE_DOWN;
                    mTouchX = x;
                    mTouchY = y;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                switch (mTouchMode) {
                    case TOUCH_MODE_IDLE:
                        break;

                    case TOUCH_MODE_DOWN: {
                        final float x = ev.getX();
                        final float y = ev.getY();
                        if (Math.abs(x - mTouchX) > mTouchSlop || Math.abs(y - mTouchY) > mTouchSlop) {
                            mTouchMode = TOUCH_MODE_DRAGGING;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            mTouchX = x;
                            mTouchY = y;
                            return true;
                        }
                        break;
                    }

                    case TOUCH_MODE_DRAGGING: {
                        final float x = ev.getX();
                        final int thumbScrollRange = getThumbScrollRange();
                        final float thumbScrollOffset = x - mTouchX;
                        float dPos;
                        if (thumbScrollRange != 0) {
                            dPos = thumbScrollOffset / thumbScrollRange;
                        } else {
                            dPos = thumbScrollOffset > 0 ? 1 : -1;
                        }
                        if (LocaleController.isRTL) {
                            dPos = -dPos;
                        }
                        final float newPos = constrain(thumbPosition + dPos, 0, 1);
                        if (newPos != thumbPosition) {
                            mTouchX = x;
                            setThumbPosition(newPos);
                        }
                        return true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mTouchMode == TOUCH_MODE_DRAGGING) {
                    stopDrag(ev);
                    super.onTouchEvent(ev);
                    return true;
                }
                mTouchMode = TOUCH_MODE_IDLE;
                mVelocityTracker.clear();
                break;
            }
        }

        return super.onTouchEvent(ev);
    }

    private void cancelSuperTouch(MotionEvent ev) {
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
    }

    private void stopDrag(MotionEvent ev) {
        mTouchMode = TOUCH_MODE_IDLE;

        final boolean commitChange = ev.getAction() == MotionEvent.ACTION_UP && isEnabled();
        final boolean newState;
        if (commitChange) {
            mVelocityTracker.computeCurrentVelocity(1000);
            final float xvel = mVelocityTracker.getXVelocity();
            if (Math.abs(xvel) > mMinFlingVelocity) {
                newState = LocaleController.isRTL ? (xvel < 0) : (xvel > 0);
            } else {
                newState = getTargetCheckedState();
            }
        } else {
            newState = isChecked();
        }

        setChecked(newState);
        cancelSuperTouch(ev);
    }

    private void animateThumbToCheckedState(boolean newCheckedState) {
        final float targetPosition = newCheckedState ? 1 : 0;
        mPositionAnimator = ObjectAnimator.ofFloat(this, "thumbPosition", targetPosition);
        mPositionAnimator.setDuration(THUMB_ANIMATION_DURATION);
        mPositionAnimator.start();
    }

    private void cancelPositionAnimator() {
        if (mPositionAnimator != null) {
            mPositionAnimator.cancel();
        }
    }

    private boolean getTargetCheckedState() {
        return thumbPosition > 0.5f;
    }

    @Keep
    private void setThumbPosition(float position) {
        thumbPosition = position;
        invalidate();
    }

    public float getThumbPosition() {
        return thumbPosition;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        wasLayout = false;
    }

    public void resetLayout() {
        wasLayout = false;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);

        checked = isChecked();

        if (attachedToWindow && wasLayout) {
            animateThumbToCheckedState(checked);
        } else {
            cancelPositionAnimator();
            setThumbPosition(checked ? 1 : 0);
        }

        if (mTrackDrawable != null) {
            mTrackDrawable.setColorFilter(new PorterDuffColorFilter(checked ? Theme.getColor(Theme.key_switchTrackChecked) : Theme.getColor(Theme.key_switchTrack), PorterDuff.Mode.MULTIPLY));
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.setColorFilter(new PorterDuffColorFilter(checked ? Theme.getColor(Theme.key_switchThumbChecked) : Theme.getColor(Theme.key_switchThumb), PorterDuff.Mode.MULTIPLY));
        }
    }

    public void checkColorFilters() {
        if (mTrackDrawable != null) {
            mTrackDrawable.setColorFilter(new PorterDuffColorFilter(isChecked() ? Theme.getColor(Theme.key_switchTrackChecked) : Theme.getColor(Theme.key_switchTrack), PorterDuff.Mode.MULTIPLY));
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.setColorFilter(new PorterDuffColorFilter(isChecked() ? Theme.getColor(Theme.key_switchThumbChecked) : Theme.getColor(Theme.key_switchThumb), PorterDuff.Mode.MULTIPLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        wasLayout = true;
        int opticalInsetLeft = 0;
        int opticalInsetRight = 0;
        if (mThumbDrawable != null) {
            final Rect trackPadding = mTempRect;
            if (mTrackDrawable != null) {
                mTrackDrawable.getPadding(trackPadding);
            } else {
                trackPadding.setEmpty();
            }

            final Insets insets = Insets.NONE;
            opticalInsetLeft = Math.max(0, insets.left - trackPadding.left);
            opticalInsetRight = Math.max(0, insets.right - trackPadding.right);
        }

        final int switchRight;
        final int switchLeft;
        if (LocaleController.isRTL) {
            switchLeft = getPaddingLeft() + opticalInsetLeft;
            switchRight = switchLeft + mSwitchWidth - opticalInsetLeft - opticalInsetRight;
        } else {
            switchRight = getWidth() - getPaddingRight() - opticalInsetRight;
            switchLeft = switchRight - mSwitchWidth + opticalInsetLeft + opticalInsetRight;
        }

        final int switchTop;
        final int switchBottom;
        switch (getGravity() & Gravity.VERTICAL_GRAVITY_MASK) {
            default:
            case Gravity.TOP:
                switchTop = getPaddingTop();
                switchBottom = switchTop + mSwitchHeight;
                break;

            case Gravity.CENTER_VERTICAL:
                switchTop = (getPaddingTop() + getHeight() - getPaddingBottom()) / 2 - mSwitchHeight / 2;
                switchBottom = switchTop + mSwitchHeight;
                break;

            case Gravity.BOTTOM:
                switchBottom = getHeight() - getPaddingBottom();
                switchTop = switchBottom - mSwitchHeight;
                break;
        }

        mSwitchLeft = switchLeft;
        mSwitchTop = switchTop;
        mSwitchBottom = switchBottom;
        mSwitchRight = switchRight;
    }

    @Override
    public void draw(Canvas c) {
        final Rect padding = mTempRect;
        final int switchLeft = mSwitchLeft;
        final int switchTop = mSwitchTop;
        final int switchRight = mSwitchRight;
        final int switchBottom = mSwitchBottom;

        int thumbInitialLeft = switchLeft + getThumbOffset();

        final Insets thumbInsets;
        if (mThumbDrawable != null) {
            thumbInsets = Insets.NONE;
        } else {
            thumbInsets = Insets.NONE;
        }

        if (mTrackDrawable != null) {
            mTrackDrawable.getPadding(padding);

            thumbInitialLeft += padding.left;

            int trackLeft = switchLeft;
            int trackTop = switchTop;
            int trackRight = switchRight;
            int trackBottom = switchBottom;
            if (thumbInsets != Insets.NONE) {
                if (thumbInsets.left > padding.left) {
                    trackLeft += thumbInsets.left - padding.left;
                }
                if (thumbInsets.top > padding.top) {
                    trackTop += thumbInsets.top - padding.top;
                }
                if (thumbInsets.right > padding.right) {
                    trackRight -= thumbInsets.right - padding.right;
                }
                if (thumbInsets.bottom > padding.bottom) {
                    trackBottom -= thumbInsets.bottom - padding.bottom;
                }
            }
            mTrackDrawable.setBounds(trackLeft, trackTop, trackRight, trackBottom);
        }

        if (mThumbDrawable != null) {
            mThumbDrawable.getPadding(padding);

            final int thumbLeft = thumbInitialLeft - padding.left;
            final int thumbRight = thumbInitialLeft + mThumbWidth + padding.right;
            int offset = (AndroidUtilities.density == 1.5f ? AndroidUtilities.dp(1) : 0);
            mThumbDrawable.setBounds(thumbLeft, switchTop + offset, thumbRight, switchBottom + offset);

            final Drawable background = getBackground();
            if (background != null && Build.VERSION.SDK_INT >= 21) {
                background.setHotspotBounds(thumbLeft, switchTop, thumbRight, switchBottom);
            }
        }

        super.draw(c);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final Rect padding = mTempRect;
        final Drawable trackDrawable = mTrackDrawable;
        if (trackDrawable != null) {
            trackDrawable.getPadding(padding);
        } else {
            padding.setEmpty();
        }

        final int switchTop = mSwitchTop;
        final int switchBottom = mSwitchBottom;

        final Drawable thumbDrawable = mThumbDrawable;
        if (trackDrawable != null) {
            if (mSplitTrack && thumbDrawable != null) {
                final Insets insets = Insets.NONE;
                thumbDrawable.copyBounds(padding);
                padding.left += insets.left;
                padding.right -= insets.right;

                final int saveCount = canvas.save();
                canvas.clipRect(padding, Region.Op.DIFFERENCE);
                trackDrawable.draw(canvas);
                canvas.restoreToCount(saveCount);
            } else {
                trackDrawable.draw(canvas);
            }
        }

        final int saveCount = canvas.save();

        if (thumbDrawable != null) {
            thumbDrawable.draw(canvas);
        }

        canvas.restoreToCount(saveCount);
    }

    @Override
    public int getCompoundPaddingLeft() {
        if (!LocaleController.isRTL) {
            return super.getCompoundPaddingLeft();
        }
        return super.getCompoundPaddingLeft() + mSwitchWidth;
    }

    @Override
    public int getCompoundPaddingRight() {
        if (LocaleController.isRTL) {
            return super.getCompoundPaddingRight();
        }
        return super.getCompoundPaddingRight() + mSwitchWidth;
    }

    private int getThumbOffset() {
        final float position;
        if (LocaleController.isRTL) {
            position = 1 - thumbPosition;
        } else {
            position = thumbPosition;
        }
        return (int) (position * getThumbScrollRange() + 0.5f);
    }

    private int getThumbScrollRange() {
        if (mTrackDrawable != null) {
            final Rect padding = mTempRect;
            mTrackDrawable.getPadding(padding);

            final Insets insets;
            if (mThumbDrawable != null) {
                insets = Insets.NONE;
            } else {
                insets = Insets.NONE;
            }

            return mSwitchWidth - mThumbWidth - padding.left - padding.right - insets.left - insets.right;
        } else {
            return 0;
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        final int[] myDrawableState = getDrawableState();

        if (mThumbDrawable != null) {
            mThumbDrawable.setState(myDrawableState);
        }

        if (mTrackDrawable != null) {
            mTrackDrawable.setState(myDrawableState);
        }

        invalidate();
    }

    @SuppressLint("NewApi")
    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mThumbDrawable != null) {
            mThumbDrawable.setHotspot(x, y);
        }

        if (mTrackDrawable != null) {
            mTrackDrawable.setHotspot(x, y);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mThumbDrawable || who == mTrackDrawable;
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (mThumbDrawable != null) {
            mThumbDrawable.jumpToCurrentState();
        }

        if (mTrackDrawable != null) {
            mTrackDrawable.jumpToCurrentState();
        }

        if (mPositionAnimator != null && mPositionAnimator.isRunning()) {
            mPositionAnimator.end();
            mPositionAnimator = null;
        }
    }
}
