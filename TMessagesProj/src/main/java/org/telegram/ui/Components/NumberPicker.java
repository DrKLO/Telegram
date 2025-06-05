/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.os.Build;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class NumberPicker extends LinearLayout {

    public static final int DEFAULT_SIZE_PER_COUNT = 42;
    private int SELECTOR_WHEEL_ITEM_COUNT = 3;
    private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;
    private int SELECTOR_MIDDLE_ITEM_INDEX = SELECTOR_WHEEL_ITEM_COUNT / 2;
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 8;
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;
    private static final int SNAP_SCROLL_DURATION = 300;
    private static final float TOP_AND_BOTTOM_FADING_EDGE_STRENGTH = 0.9f;
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT = 2;
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE = 48;
    private static final int DEFAULT_LAYOUT_RESOURCE_ID = 0;
    private static final int SIZE_UNSPECIFIED = -1;

    private int textOffset;
    private TextView mInputText;
    private int mSelectionDividersDistance;
    private int mMinHeight;
    private int mMaxHeight;
    private int mMinWidth;
    private int mMaxWidth;
    private boolean mComputeMaxWidth;
    private int mTextSize;
    private int mSelectorTextGapHeight;
    private String[] mDisplayedValues;
    private int mMinValue;
    private boolean mMinValueSet;
    private int mMaxValue;
    private boolean mMaxValueSet;
    private int mValue;
    private int mFantomValue;
    private OnValueChangeListener mOnValueChangeListener;
    private OnScrollListener mOnScrollListener;
    private Formatter mFormatter;
    private long mLongPressUpdateInterval = DEFAULT_LONG_PRESS_UPDATE_INTERVAL;
    private final SparseArray<String> mSelectorIndexToStringCache = new SparseArray<>();
    private int[] mSelectorIndices = new int[SELECTOR_WHEEL_ITEM_COUNT];
    private Paint mSelectorWheelPaint;
    private int mSelectorElementHeight;
    private int mInitialScrollOffset = Integer.MIN_VALUE;
    private int mCurrentScrollOffset;
    private Scroller mFlingScroller;
    private Scroller mAdjustScroller;
    private int mPreviousScrollerY;
    private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;
    private float mLastDownEventY;
    private long mLastDownEventTime;
    private float mLastDownOrMoveEventY;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private boolean mWrapSelectorWheel, mWrapSelectorWheelSetting;
    private int mSolidColor;
    private Paint mSelectionDivider;
    private int mSelectionDividerHeight;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private boolean mIngonreMoveEvents;
    private int mTopSelectionDividerTop;
    private int mBottomSelectionDividerBottom;
    private int mLastHoveredChildVirtualViewId;
    private boolean mIncrementVirtualButtonPressed;
    private boolean mDecrementVirtualButtonPressed;
    private PressedStateHelper mPressedStateHelper;
    private int mLastHandledDownDpadKeyCode = -1;
    private SeekBarAccessibilityDelegate accessibilityDelegate;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean drawDividers = true;

    public interface OnValueChangeListener {
        void onValueChange(NumberPicker picker, int oldVal, int newVal);
    }

    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChange(NumberPicker view, int scrollState);
    }

    public interface Formatter {
        String format(int value);
    }

    public void setItemCount(int count) {
        if (SELECTOR_WHEEL_ITEM_COUNT == count) {
            return;
        }
        SELECTOR_WHEEL_ITEM_COUNT = count;
        SELECTOR_MIDDLE_ITEM_INDEX = SELECTOR_WHEEL_ITEM_COUNT / 2;
        mSelectorIndices = new int[SELECTOR_WHEEL_ITEM_COUNT];
        initializeSelectorWheelIndices();
    }

    public int getItemsCount() {
        return SELECTOR_WHEEL_ITEM_COUNT;
    }

    private void init() {
        mSolidColor = 0;
        mSelectionDivider = new Paint();
        mSelectionDivider.setColor(getThemedColor(Theme.key_featuredStickers_addButton));

        mSelectionDividerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT, getResources().getDisplayMetrics());
        mSelectionDividersDistance = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE, getResources().getDisplayMetrics());

        mMinHeight = SIZE_UNSPECIFIED;

        mMaxHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 180, getResources().getDisplayMetrics());
        if (mMinHeight != SIZE_UNSPECIFIED && mMaxHeight != SIZE_UNSPECIFIED && mMinHeight > mMaxHeight) {
            throw new IllegalArgumentException("minHeight > maxHeight");
        }

        mMinWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());

        mMaxWidth = SIZE_UNSPECIFIED;
        if (mMinWidth != SIZE_UNSPECIFIED && mMaxWidth != SIZE_UNSPECIFIED && mMinWidth > mMaxWidth) {
            throw new IllegalArgumentException("minWidth > maxWidth");
        }

        mComputeMaxWidth = (mMaxWidth == SIZE_UNSPECIFIED);

        mPressedStateHelper = new PressedStateHelper();

        setWillNotDraw(false);

        mInputText = new TextView(getContext());
        mInputText.setGravity(Gravity.CENTER);
        mInputText.setSingleLine(true);
        mInputText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        mInputText.setBackgroundResource(0);
        mInputText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        mInputText.setVisibility(INVISIBLE);
        addView(mInputText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity() / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(mTextSize);
        paint.setTypeface(mInputText.getTypeface());
        ColorStateList colors = mInputText.getTextColors();
        int color = colors.getColorForState(ENABLED_STATE_SET, Color.WHITE);
        paint.setColor(color);
        mSelectorWheelPaint = paint;

        mFlingScroller = new Scroller(getContext(), null, true);
        mAdjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));

        updateInputTextView();

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityDelegate(accessibilityDelegate = new SeekBarAccessibilityDelegate() {
            @Override
            protected void doScroll(View host, boolean backward) {
                changeValueByOne(!backward);
            }

            @Override
            protected boolean canScrollBackward(View host) {
                return true;
            }

            @Override
            protected boolean canScrollForward(View host) {
                return true;
            }

            @Override
            public CharSequence getContentDescription(View host) {
                return NumberPicker.this.getContentDescription(mValue);
            }
        });
    }

    protected CharSequence getContentDescription(int value) {
        return mInputText.getText();
    }

    public void setTextColor(int color) {
        mInputText.setTextColor(color);
        mSelectorWheelPaint.setColor(color);
    }

    public void setSelectorColor(int color) {
        mSelectionDivider.setColor(color);
    }

    public NumberPicker(Context context) {
        this(context, null);
    }

    public NumberPicker(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 18, resourcesProvider);
    }

    public NumberPicker(Context context, int textSize) {
        this(context, textSize, null);
    }

    public NumberPicker(Context context, int textSize, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        mTextSize = AndroidUtilities.dp(textSize);
        init();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int msrdWdth = getMeasuredWidth();
        final int msrdHght = getMeasuredHeight();

        final int inptTxtMsrdWdth = mInputText.getMeasuredWidth();
        final int inptTxtMsrdHght = mInputText.getMeasuredHeight();
        final int inptTxtLeft = (msrdWdth - inptTxtMsrdWdth) / 2;
        final int inptTxtTop = (msrdHght - inptTxtMsrdHght) / 2;
        final int inptTxtRight = inptTxtLeft + inptTxtMsrdWdth;
        final int inptTxtBottom = inptTxtTop + inptTxtMsrdHght;
        mInputText.layout(inptTxtLeft, inptTxtTop, inptTxtRight, inptTxtBottom);

        if (changed) {
            initializeSelectorWheel();
            initializeFadingEdges();
            mTopSelectionDividerTop = (getHeight() - mTextSize - mSelectorTextGapHeight) / 2;
            mBottomSelectionDividerBottom = (getHeight() + mTextSize + mSelectorTextGapHeight) / 2;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mMaxWidth);
        final int newHeightMeasureSpec = makeMeasureSpec(heightMeasureSpec, mMaxHeight);
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec);
        final int widthSize = resolveSizeAndStateRespectingMinSize(mMinWidth, getMeasuredWidth(), widthMeasureSpec);
        final int heightSize = resolveSizeAndStateRespectingMinSize(mMinHeight, getMeasuredHeight(), heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    private boolean moveToFinalScrollerPosition(Scroller scroller) {
        scroller.forceFinished(true);
        int amountToScroll = scroller.getFinalY() - scroller.getCurrY();
        int futureScrollOffset = (mCurrentScrollOffset + amountToScroll) % mSelectorElementHeight;
        int overshootAdjustment = mInitialScrollOffset - futureScrollOffset;
        if (overshootAdjustment != 0) {
            if (Math.abs(overshootAdjustment) > mSelectorElementHeight / 2) {
                if (overshootAdjustment > 0) {
                    overshootAdjustment -= mSelectorElementHeight;
                } else {
                    overshootAdjustment += mSelectorElementHeight;
                }
            }
            amountToScroll += overshootAdjustment;
            scrollBy(0, amountToScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            removeAllCallbacks();
            mInputText.setVisibility(View.INVISIBLE);
            mLastDownOrMoveEventY = mLastDownEventY = event.getY();
            mLastDownEventTime = event.getEventTime();
            mIngonreMoveEvents = false;
            if (mLastDownEventY < mTopSelectionDividerTop) {
                if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                    mPressedStateHelper.buttonPressDelayed(PressedStateHelper.BUTTON_DECREMENT);
                }
            } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                    mPressedStateHelper.buttonPressDelayed(PressedStateHelper.BUTTON_INCREMENT);
                }
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            if (!mFlingScroller.isFinished()) {
                mFlingScroller.forceFinished(true);
                mAdjustScroller.forceFinished(true);
                onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
            } else if (!mAdjustScroller.isFinished()) {
                mFlingScroller.forceFinished(true);
                mAdjustScroller.forceFinished(true);
            } else if (mLastDownEventY < mTopSelectionDividerTop) {
                postChangeCurrentByOneFromLongPress(false, ViewConfiguration.getLongPressTimeout());
            } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                postChangeCurrentByOneFromLongPress(true, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        return false;
    }

    public void finishScroll() {
        if (!mFlingScroller.isFinished() || !mAdjustScroller.isFinished()) {
            mFlingScroller.forceFinished(true);
            mAdjustScroller.forceFinished(true);
            mCurrentScrollOffset = mInitialScrollOffset;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (mIngonreMoveEvents) {
                    break;
                }
                float currentMoveY = event.getY();
                if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    int deltaDownY = (int) Math.abs(currentMoveY - mLastDownEventY);
                    if (deltaDownY > mTouchSlop) {
                        removeAllCallbacks();
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    }
                } else {
                    int deltaMoveY = (int) ((currentMoveY - mLastDownOrMoveEventY));
                    scrollBy(0, deltaMoveY);
                    invalidate();
                }
                mLastDownOrMoveEventY = currentMoveY;
            }
            break;
            case MotionEvent.ACTION_UP: {
                removeChangeCurrentByOneFromLongPress();
                mPressedStateHelper.cancel();
                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocity = (int) velocityTracker.getYVelocity();
                if (Math.abs(initialVelocity) > mMinimumFlingVelocity) {
                    fling(initialVelocity);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                } else {
                    int eventY = (int) event.getY();
                    int deltaMoveY = (int) Math.abs(eventY - mLastDownEventY);
                    long deltaTime = event.getEventTime() - mLastDownEventTime;
                    if (deltaMoveY <= mTouchSlop && deltaTime < ViewConfiguration.getTapTimeout()) {
                        int selectorIndexOffset = (eventY / mSelectorElementHeight) - SELECTOR_MIDDLE_ITEM_INDEX;
                        if (selectorIndexOffset > 0) {
                            changeValueByOne(true);
                            mPressedStateHelper.buttonTapped(
                                    PressedStateHelper.BUTTON_INCREMENT);
                        } else if (selectorIndexOffset < 0) {
                            changeValueByOne(false);
                            mPressedStateHelper.buttonTapped(
                                    PressedStateHelper.BUTTON_DECREMENT);
                        }
                    } else {
                        ensureScrollWheelAdjusted();
                    }
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            break;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                removeAllCallbacks();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (mWrapSelectorWheel || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                                ? getValue() < getMaxValue() : getValue() > getMinValue()) {
                            requestFocus();
                            mLastHandledDownDpadKeyCode = keyCode;
                            removeAllCallbacks();
                            if (mFlingScroller.isFinished()) {
                                changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
                            }
                            return true;
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        if (mLastHandledDownDpadKeyCode == keyCode) {
                            mLastHandledDownDpadKeyCode = -1;
                            return true;
                        }
                        break;
                }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTrackballEvent(event);
    }

    @Override
    public void computeScroll() {
        Scroller scroller = mFlingScroller;
        if (scroller.isFinished()) {
            scroller = mAdjustScroller;
            if (scroller.isFinished()) {
                return;
            }
        }
        scroller.computeScrollOffset();
        int currentScrollerY = scroller.getCurrY();
        if (mPreviousScrollerY == 0) {
            mPreviousScrollerY = scroller.getStartY();
        }
        scrollBy(0, currentScrollerY - mPreviousScrollerY);
        mPreviousScrollerY = currentScrollerY;
        if (scroller.isFinished()) {
            onScrollerFinished(scroller);
        } else {
            invalidate();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mInputText.setEnabled(enabled);
    }

    @Override
    public void scrollBy(int x, int y) {
        int[] selectorIndices = mSelectorIndices;
        if (!mWrapSelectorWheel && y > 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue && mCurrentScrollOffset + y > mInitialScrollOffset) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        if (!mWrapSelectorWheel && y < 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue && mCurrentScrollOffset + y < mInitialScrollOffset) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        mCurrentScrollOffset += y;
        while (mCurrentScrollOffset - mInitialScrollOffset > mSelectorTextGapHeight) {
            mCurrentScrollOffset -= mSelectorElementHeight;
            decrementSelectorIndices(selectorIndices);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue && mCurrentScrollOffset > mInitialScrollOffset) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        while (mCurrentScrollOffset - mInitialScrollOffset < -mSelectorTextGapHeight) {
            mCurrentScrollOffset += mSelectorElementHeight;
            incrementSelectorIndices(selectorIndices);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue && mCurrentScrollOffset < mInitialScrollOffset) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true);
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mCurrentScrollOffset;
    }

    @Override
    protected int computeVerticalScrollRange() {
        return (mMaxValue - mMinValue + 1) * mSelectorElementHeight;
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return getHeight();
    }

    @Override
    public int getSolidColor() {
        return mSolidColor;
    }

    public void setOnValueChangedListener(OnValueChangeListener onValueChangedListener) {
        mOnValueChangeListener = onValueChangedListener;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

    public void setFormatter(Formatter formatter) {
        if (formatter == mFormatter) {
            return;
        }
        mFormatter = formatter;
        initializeSelectorWheelIndices();
        updateInputTextView();
    }

    public void setValue(int value) {
        setValueInternal(value, false);
    }

    public void setTextOffset(int value) {
        textOffset = value;
        invalidate();
    }

    private void tryComputeMaxWidth() {
        if (!mComputeMaxWidth) {
            return;
        }
        int maxTextWidth = 0;
        if (mDisplayedValues == null) {
            float maxDigitWidth = 0;
            for (int i = 0; i <= 9; i++) {
                final float digitWidth = mSelectorWheelPaint.measureText(formatNumberWithLocale(i));
                if (digitWidth > maxDigitWidth) {
                    maxDigitWidth = digitWidth;
                }
            }
            int numberOfDigits = 0;
            int current = mMaxValue;
            while (current > 0) {
                numberOfDigits++;
                current = current / 10;
            }
            maxTextWidth = (int) (numberOfDigits * maxDigitWidth);
        } else {
            for (String mDisplayedValue : mDisplayedValues) {
                final float textWidth = mSelectorWheelPaint.measureText(mDisplayedValue);
                if (textWidth > maxTextWidth) {
                    maxTextWidth = (int) textWidth;
                }
            }
        }
        maxTextWidth += mInputText.getPaddingLeft() + mInputText.getPaddingRight();
        if (mMaxWidth != maxTextWidth) {
            if (maxTextWidth > mMinWidth) {
                mMaxWidth = maxTextWidth;
            } else {
                mMaxWidth = mMinWidth;
            }
            invalidate();
        }
    }

    public boolean getWrapSelectorWheel() {
        return mWrapSelectorWheel;
    }

    public void setWrapSelectorWheel(boolean wrapSelectorWheel) {
        final boolean wrappingAllowed = !(mMaxValueSet && mMinValueSet) || allItemsCount != null && (mMaxValue - mMinValue + 1) >= allItemsCount;
        mWrapSelectorWheel = wrappingAllowed && (mWrapSelectorWheelSetting = wrapSelectorWheel);
    }

    private Integer allItemsCount;
    public void setAllItemsCount(int allItemsCount) {
        this.allItemsCount = allItemsCount;
        setWrapSelectorWheel(mWrapSelectorWheelSetting);
    }

    public void setOnLongPressUpdateInterval(long intervalMillis) {
        mLongPressUpdateInterval = intervalMillis;
    }

    public int getValue() {
        return mValue;
    }

    public int getMinValue() {
        return mMinValue;
    }

    public void setMinValue(int minValue) {
        mMinValueSet = true;
        if (mMinValue == minValue) {
            return;
        }
        if (minValue < 0) {
            throw new IllegalArgumentException("minValue must be >= 0");
        }
        mMinValue = minValue;
        if (mMinValue > mValue) {
            if (mMinValue <= mFantomValue) {
                mValue = mFantomValue;
            } else {
                mValue = mMinValue;
            }
        }
        setWrapSelectorWheel(mWrapSelectorWheelSetting);
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
        if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE && mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, OnScrollListener.SCROLL_STATE_IDLE);
        }
    }

    public int getMaxValue() {
        return mMaxValue;
    }

    public void setMaxValue(int maxValue) {
        mMaxValueSet = true;
        if (mMaxValue == maxValue) {
            return;
        }
        if (maxValue < 0) {
            throw new IllegalArgumentException("maxValue must be >= 0");
        }
        mMaxValue = maxValue;
        if (mMaxValue < mValue) {
            if (mMaxValue >= mFantomValue) {
                mValue = mFantomValue;
            } else {
                mValue = mMaxValue;
            }
        }
        setWrapSelectorWheel(mWrapSelectorWheelSetting);
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
        if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE && mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, OnScrollListener.SCROLL_STATE_IDLE);
        }
    }

    public String[] getDisplayedValues() {
        return mDisplayedValues;
    }

    public void setDisplayedValues(String[] displayedValues) {
        if (mDisplayedValues == displayedValues) {
            return;
        }
        mDisplayedValues = displayedValues;
        updateInputTextView();
        initializeSelectorWheelIndices();
        tryComputeMaxWidth();
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeAllCallbacks();
    }

    private int thisGravity;
    @Override
    public void setGravity(int gravity) {
        super.setGravity(thisGravity = gravity);
    }

    private final static CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0, 0.5f, 0.5f, 1f);
    @Override
    protected void onDraw(Canvas canvas) {
        float x;
        if (thisGravity == Gravity.RIGHT) {
            mSelectorWheelPaint.setTextAlign(Align.RIGHT);
            x = getWidth();
        } else if (thisGravity == Gravity.LEFT) {
            mSelectorWheelPaint.setTextAlign(Align.LEFT);
            x = 0;
        } else {
            mSelectorWheelPaint.setTextAlign(Align.CENTER);
            x = getWidth() / 2f;
        }
        x += textOffset;
        float y = mCurrentScrollOffset;

        // draw the selector wheel
        int[] selectorIndices = mSelectorIndices;
        for (int i = 0; i < selectorIndices.length; i++) {
            int selectorIndex = selectorIndices[i];
            String scrollSelectorValue = mSelectorIndexToStringCache.get(selectorIndex);
            // Do not draw the middle item if input is visible since the input
            // is shown only if the wheel is static and it covers the middle
            // item. Otherwise, if the user starts editing the text via the
            // IME he may see a dimmed version of the old value intermixed
            // with the new one.
            if (scrollSelectorValue != null && (i != SELECTOR_MIDDLE_ITEM_INDEX || mInputText.getVisibility() != VISIBLE)) {
                if (SELECTOR_WHEEL_ITEM_COUNT > 3) {
                    float p;
                    float cY = getMeasuredHeight() / 2f;
                    float r = getMeasuredHeight() * 0.5f;
                    float localY = y - mSelectorWheelPaint.getTextSize() / 2f;
                    boolean top = true;
                    if (localY < cY) {
                        p = localY / r;
                    } else {
                        p = (getMeasuredHeight() - localY) / r;
                        top = false;
                    }
                    p = interpolator.getInterpolation(Utilities.clamp(p, 1f, 0));
                    float yOffset = (1f - p) * mSelectorWheelPaint.getTextSize();
                    if (!top) {
                        yOffset = -yOffset;
                    }
                    int oldAlpha = -1;

                    canvas.save();
                    canvas.translate(0, yOffset);
                    canvas.scale(0.8f + p * 0.2f, p, x, localY);
                    if (p < 0.1f) {
                        oldAlpha = mSelectorWheelPaint.getAlpha();
                        mSelectorWheelPaint.setAlpha((int) (oldAlpha * p / 0.1f));
                    }
                    canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint);
                    canvas.restore();
                    if (oldAlpha != -1) {
                        mSelectorWheelPaint.setAlpha(oldAlpha);
                    }
                } else {
                    canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint);
                }
            }
            y += mSelectorElementHeight;
        }

        if (drawDividers) {
            int topOfTopDivider = mTopSelectionDividerTop;
            int bottomOfTopDivider = topOfTopDivider + mSelectionDividerHeight;
            canvas.drawRect(0, topOfTopDivider, getRight(), bottomOfTopDivider, mSelectionDivider);

            int bottomOfBottomDivider = mBottomSelectionDividerBottom;
            int topOfBottomDivider = bottomOfBottomDivider - mSelectionDividerHeight;
            canvas.drawRect(0, topOfBottomDivider, getRight(), bottomOfBottomDivider, mSelectionDivider);
        }
    }

    private int makeMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize == SIZE_UNSPECIFIED) {
            return measureSpec;
        }
        final int size = MeasureSpec.getSize(measureSpec);
        final int mode = MeasureSpec.getMode(measureSpec);
        switch (mode) {
            case MeasureSpec.EXACTLY:
                return measureSpec;
            case MeasureSpec.AT_MOST:
                return MeasureSpec.makeMeasureSpec(Math.min(size, maxSize), MeasureSpec.EXACTLY);
            case MeasureSpec.UNSPECIFIED:
                return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY);
            default:
                throw new IllegalArgumentException("Unknown measure mode: " + mode);
        }
    }

    private int resolveSizeAndStateRespectingMinSize(
            int minSize, int measuredSize, int measureSpec) {
        if (minSize != SIZE_UNSPECIFIED) {
            final int desiredWidth = Math.max(minSize, measuredSize);
            return resolveSizeAndState(desiredWidth, measureSpec, 0);
        } else {
            return measuredSize;
        }
    }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize =  MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
                if (specSize < size) {
                    result = specSize | 16777216;
                } else {
                    result = size;
                }
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result | (childMeasuredState & (-16777216));
    }

    private void initializeSelectorWheelIndices() {
        mSelectorIndexToStringCache.clear();
        int[] selectorIndices = mSelectorIndices;
        int current = getValue();
        for (int i = 0; i < mSelectorIndices.length; i++) {
            int selectorIndex = current + (i - SELECTOR_MIDDLE_ITEM_INDEX);
            if (mWrapSelectorWheel) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex);
            }
            selectorIndices[i] = selectorIndex;
            ensureCachedScrollSelectorValue(selectorIndices[i]);
        }
    }

    private void setValueInternal(int current, boolean notifyChange) {
        if (mValue == current) {
            return;
        }
        if (mWrapSelectorWheel) {
            current = getWrappedSelectorIndex(current);
        } else {
            current = Math.max(current, mMinValue);
            current = Math.min(current, mMaxValue);
        }
        int previous = mValue;
        mValue = mFantomValue = current;
        updateInputTextView();
        if (Math.abs(previous - current) > 0.9f) {
            AndroidUtilities.vibrateCursor(this);
        }
        if (notifyChange) {
            notifyChange(previous, current);
        }
        initializeSelectorWheelIndices();
        invalidate();
        if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE && mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, OnScrollListener.SCROLL_STATE_IDLE);
        }
    }

    protected void changeValueByOne(boolean increment) {
        mInputText.setVisibility(View.INVISIBLE);
        if (!moveToFinalScrollerPosition(mFlingScroller)) {
            moveToFinalScrollerPosition(mAdjustScroller);
        }
        mPreviousScrollerY = 0;
        if (increment) {
            mFlingScroller.startScroll(0, 0, 0, -mSelectorElementHeight, SNAP_SCROLL_DURATION);
        } else {
            mFlingScroller.startScroll(0, 0, 0, mSelectorElementHeight, SNAP_SCROLL_DURATION);
        }
        invalidate();
    }

    private void initializeSelectorWheel() {
        initializeSelectorWheelIndices();
        int[] selectorIndices = mSelectorIndices;
        int totalTextHeight = selectorIndices.length * mTextSize;
        float totalTextGapHeight = (getBottom() - getTop() + mTextSize) - totalTextHeight;
        float textGapCount = selectorIndices.length;
        mSelectorTextGapHeight = (int) (totalTextGapHeight / textGapCount + 0.5f);
        mSelectorElementHeight = mTextSize + mSelectorTextGapHeight;
        int editTextTextPosition = mInputText.getBaseline() + mInputText.getTop();
        mInitialScrollOffset = editTextTextPosition - (mSelectorElementHeight * SELECTOR_MIDDLE_ITEM_INDEX);
        mCurrentScrollOffset = mInitialScrollOffset;
        updateInputTextView();
    }

    private void initializeFadingEdges() {
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength((getBottom() - getTop() - mTextSize) / 2);
    }

    private void onScrollerFinished(Scroller scroller) {
        if (scroller == mFlingScroller) {
            if (!ensureScrollWheelAdjusted()) {
                updateInputTextView();
            }
            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
        } else {
            if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                updateInputTextView();
            }
        }
    }

    private void onScrollStateChange(int scrollState) {
        if (mScrollState == scrollState) {
            return;
        }
        mScrollState = scrollState;
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, scrollState);
        }
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am.isTouchExplorationEnabled()) {
                String text = (mDisplayedValues == null) ? formatNumber(mValue) : mDisplayedValues[mValue - mMinValue];
                AccessibilityEvent event = AccessibilityEvent.obtain();
                event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                event.getText().add(text);
                am.sendAccessibilityEvent(event);
            }
        }
    }

    private void fling(int velocityY) {
        mPreviousScrollerY = 0;

        if (velocityY > 0) {
            mFlingScroller.fling(0, 0, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        } else {
            mFlingScroller.fling(0, Integer.MAX_VALUE, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        }

        invalidate();
    }

    private int getWrappedSelectorIndex(int selectorIndex) {
        if (mMaxValueSet && selectorIndex > mMaxValue && mMaxValue - mMinValue != 0) {
            return mMinValue + (selectorIndex - mMaxValue) % (mMaxValue - mMinValue) - 1;
        } else if (mMinValueSet && selectorIndex < mMinValue && mMaxValue - mMinValue != 0) {
            return mMaxValue - (mMinValue - selectorIndex) % (mMaxValue - mMinValue) + 1;
        }
        return selectorIndex;
    }

    private void incrementSelectorIndices(int[] selectorIndices) {
        System.arraycopy(selectorIndices, 1, selectorIndices, 0, selectorIndices.length - 1);
        int nextScrollSelectorIndex = selectorIndices[selectorIndices.length - 2] + 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex > mMaxValue) {
            nextScrollSelectorIndex = mMinValue;
        }
        selectorIndices[selectorIndices.length - 1] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void decrementSelectorIndices(int[] selectorIndices) {
        System.arraycopy(selectorIndices, 0, selectorIndices, 1, selectorIndices.length - 1);
        int nextScrollSelectorIndex = selectorIndices[1] - 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex < mMinValue) {
            nextScrollSelectorIndex = mMaxValue;
        }
        selectorIndices[0] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void ensureCachedScrollSelectorValue(int selectorIndex) {
        SparseArray<String> cache = mSelectorIndexToStringCache;
        String scrollSelectorValue = cache.get(selectorIndex);
        if (scrollSelectorValue != null) {
            return;
        }
        if (selectorIndex < mMinValue || selectorIndex > mMaxValue) {
            scrollSelectorValue = "";
        } else {
            if (mDisplayedValues != null) {
                int displayedValueIndex = selectorIndex - mMinValue;
                scrollSelectorValue = mDisplayedValues[displayedValueIndex];
            } else {
                scrollSelectorValue = formatNumber(selectorIndex);
            }
        }
        cache.put(selectorIndex, scrollSelectorValue);
    }

    private String formatNumber(int value) {
        return (mFormatter != null) ? mFormatter.format(value) : formatNumberWithLocale(value);
    }

    private boolean updateInputTextView() {
        String text = (mDisplayedValues == null) ? formatNumber(mValue) : mDisplayedValues[mValue - mMinValue];
        if (!TextUtils.isEmpty(text) && !text.equals(mInputText.getText().toString())) {
            mInputText.setText(text);
            return true;
        }
        return false;
    }

    private void notifyChange(int previous, int current) {
        if (mOnValueChangeListener != null) {
            mOnValueChangeListener.onValueChange(this, previous, mValue);
        }
    }

    private void postChangeCurrentByOneFromLongPress(boolean increment, long delayMillis) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = new ChangeCurrentByOneFromLongPressCommand();
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mChangeCurrentByOneFromLongPressCommand.setStep(increment);
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis);
    }

    private void removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
    }

    private void removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mPressedStateHelper.cancel();
    }

    private int getSelectedPos(String value) {
        if (mDisplayedValues == null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care
            }
        } else {
            for (int i = 0; i < mDisplayedValues.length; i++) {
                // Don't force the user to type in jan when ja will do
                value = value.toLowerCase();
                if (mDisplayedValues[i].toLowerCase().startsWith(value)) {
                    return mMinValue + i;
                }
            }

            /*
             * The user might have typed in a number into the month field i.e.
             * 10 instead of OCT so support that too.
             */
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care
            }
        }
        return mMinValue;
    }

    private boolean ensureScrollWheelAdjusted() {
        // adjust to the closest value
        int deltaY = mInitialScrollOffset - mCurrentScrollOffset;
        if (deltaY != 0) {
            mPreviousScrollerY = 0;
            if (Math.abs(deltaY) > mSelectorElementHeight / 2) {
                deltaY += (deltaY > 0) ? -mSelectorElementHeight : mSelectorElementHeight;
            }
            mAdjustScroller.startScroll(0, 0, 0, deltaY, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
            invalidate();
            return true;
        }
        return false;
    }

    class PressedStateHelper implements Runnable {
        public static final int BUTTON_INCREMENT = 1;
        public static final int BUTTON_DECREMENT = 2;

        private final int MODE_PRESS = 1;
        private final int MODE_TAPPED = 2;

        private int mManagedButton;
        private int mMode;

        public void cancel() {
            mMode = 0;
            mManagedButton = 0;
            NumberPicker.this.removeCallbacks(this);
            if (mIncrementVirtualButtonPressed) {
                mIncrementVirtualButtonPressed = false;
                invalidate(0, mBottomSelectionDividerBottom, getRight(), getBottom());
            }
            mDecrementVirtualButtonPressed = false;
            if (mDecrementVirtualButtonPressed) {
                invalidate(0, 0, getRight(), mTopSelectionDividerTop);
            }
        }

        public void buttonPressDelayed(int button) {
            cancel();
            mMode = MODE_PRESS;
            mManagedButton = button;
            NumberPicker.this.postDelayed(this, ViewConfiguration.getTapTimeout());
        }

        public void buttonTapped(int button) {
            cancel();
            mMode = MODE_TAPPED;
            mManagedButton = button;
            NumberPicker.this.post(this);
        }

        @Override
        public void run() {
            switch (mMode) {
                case MODE_PRESS: {
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT: {
                            mIncrementVirtualButtonPressed = true;
                            invalidate(0, mBottomSelectionDividerBottom, getRight(), getBottom());
                        }
                        break;
                        case BUTTON_DECREMENT: {
                            mDecrementVirtualButtonPressed = true;
                            invalidate(0, 0, getRight(), mTopSelectionDividerTop);
                        }
                    }
                }
                break;
                case MODE_TAPPED: {
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT: {
                            if (!mIncrementVirtualButtonPressed) {
                                NumberPicker.this.postDelayed(this,
                                        ViewConfiguration.getPressedStateDuration());
                            }
                            mIncrementVirtualButtonPressed ^= true;
                            invalidate(0, mBottomSelectionDividerBottom, getRight(), getBottom());
                        }
                        break;
                        case BUTTON_DECREMENT: {
                            if (!mDecrementVirtualButtonPressed) {
                                NumberPicker.this.postDelayed(this,
                                        ViewConfiguration.getPressedStateDuration());
                            }
                            mDecrementVirtualButtonPressed ^= true;
                            invalidate(0, 0, getRight(), mTopSelectionDividerTop);
                        }
                    }
                }
                break;
            }
        }
    }

    class ChangeCurrentByOneFromLongPressCommand implements Runnable {
        private boolean mIncrement;

        private void setStep(boolean increment) {
            mIncrement = increment;
        }

        @Override
        public void run() {
            changeValueByOne(mIncrement);
            postDelayed(this, mLongPressUpdateInterval);
        }
    }

    static private String formatNumberWithLocale(int value) {
        return String.format(Locale.getDefault(), "%d", value);
    }

    public void setDrawDividers(boolean drawDividers) {
        this.drawDividers = drawDividers;
        invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
