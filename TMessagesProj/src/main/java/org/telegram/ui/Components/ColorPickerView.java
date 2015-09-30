/*
 * Copyright 2012 Lars Werkman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class ColorPickerView extends View {

    private static final String STATE_PARENT = "parent";
    private static final String STATE_ANGLE = "angle";
    private static final String STATE_OLD_COLOR = "color";
    private static final String STATE_SHOW_OLD_COLOR = "showColor";

    private static final int[] COLORS = new int[] { 0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFFFF, 0xFFFFFF00, 0xFFFF0000 };

    private Paint mColorWheelPaint;
    private Paint mPointerHaloPaint;
    private Paint mPointerColor;
    private int mColorWheelThickness;
    private int mColorWheelRadius;
    private int mPreferredColorWheelRadius;
    private int mColorCenterRadius;
    private int mPreferredColorCenterRadius;
    private int mColorCenterHaloRadius;
    private int mPreferredColorCenterHaloRadius;
    private int mColorPointerRadius;
    private int mColorPointerHaloRadius;
    private RectF mColorWheelRectangle = new RectF();
    private RectF mCenterRectangle = new RectF();
    private boolean mUserIsMovingPointer = false;
    private int mCenterOldColor;
    private boolean mShowCenterOldColor;
    private int mCenterNewColor;
    private float mTranslationOffset;
    private float mSlopX;
    private float mSlopY;
    private float mAngle;
    private Paint mCenterOldPaint;
    private Paint mCenterNewPaint;
    private Paint mCenterHaloPaint;
    private float[] mHSV = new float[3];

    private OnColorChangedListener onColorChangedListener;
    private OnColorSelectedListener onColorSelectedListener;

    private int oldChangedListenerColor;
    private int oldSelectedListenerColor;

    public ColorPickerView(Context context) {
        super(context);
        init(null, 0);
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.onColorChangedListener = listener;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.onColorSelectedListener = listener;
    }

    private void init(AttributeSet attrs, int defStyle) {
        mColorWheelThickness = AndroidUtilities.dp(8);
        mColorWheelRadius = AndroidUtilities.dp(124);
        mPreferredColorWheelRadius = mColorWheelRadius;
        mColorCenterRadius = AndroidUtilities.dp(54);
        mPreferredColorCenterRadius = mColorCenterRadius;
        mColorCenterHaloRadius = AndroidUtilities.dp(60);
        mPreferredColorCenterHaloRadius = mColorCenterHaloRadius;
        mColorPointerRadius = AndroidUtilities.dp(14);
        mColorPointerHaloRadius = AndroidUtilities.dp(18);

        mAngle = (float) (-Math.PI / 2);

        Shader s = new SweepGradient(0, 0, COLORS, null);

        mColorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mColorWheelPaint.setShader(s);
        mColorWheelPaint.setStyle(Paint.Style.STROKE);
        mColorWheelPaint.setStrokeWidth(mColorWheelThickness);

        mPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointerHaloPaint.setColor(Color.BLACK);
        mPointerHaloPaint.setAlpha(0x50);

        mPointerColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointerColor.setColor(calculateColor(mAngle));

        mCenterNewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterNewPaint.setColor(calculateColor(mAngle));
        mCenterNewPaint.setStyle(Paint.Style.FILL);

        mCenterOldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterOldPaint.setColor(calculateColor(mAngle));
        mCenterOldPaint.setStyle(Paint.Style.FILL);

        mCenterHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterHaloPaint.setColor(Color.BLACK);
        mCenterHaloPaint.setAlpha(0x00);

        mCenterNewColor = calculateColor(mAngle);
        mCenterOldColor = calculateColor(mAngle);
        mShowCenterOldColor = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(mTranslationOffset, mTranslationOffset);
        canvas.drawOval(mColorWheelRectangle, mColorWheelPaint);

        float[] pointerPosition = calculatePointerPosition(mAngle);

        canvas.drawCircle(pointerPosition[0], pointerPosition[1], mColorPointerHaloRadius, mPointerHaloPaint);
        canvas.drawCircle(pointerPosition[0], pointerPosition[1], mColorPointerRadius, mPointerColor);
        canvas.drawCircle(0, 0, mColorCenterHaloRadius, mCenterHaloPaint);

        if (mShowCenterOldColor) {
            canvas.drawArc(mCenterRectangle, 90, 180, true, mCenterOldPaint);
            canvas.drawArc(mCenterRectangle, 270, 180, true, mCenterNewPaint);
        } else {
            canvas.drawArc(mCenterRectangle, 0, 360, true, mCenterNewPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int intrinsicSize = 2 * (mPreferredColorWheelRadius + mColorPointerHaloRadius);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(intrinsicSize, widthSize);
        } else {
            width = intrinsicSize;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(intrinsicSize, heightSize);
        } else {
            height = intrinsicSize;
        }

        int min = Math.min(width, height);
        setMeasuredDimension(min, min);
        mTranslationOffset = min * 0.5f;

        mColorWheelRadius = min / 2 - mColorWheelThickness - mColorPointerHaloRadius;
        mColorWheelRectangle.set(-mColorWheelRadius, -mColorWheelRadius, mColorWheelRadius, mColorWheelRadius);

        mColorCenterRadius = (int) ((float) mPreferredColorCenterRadius * ((float) mColorWheelRadius / (float) mPreferredColorWheelRadius));
        mColorCenterHaloRadius = (int) ((float) mPreferredColorCenterHaloRadius * ((float) mColorWheelRadius / (float) mPreferredColorWheelRadius));
        mCenterRectangle.set(-mColorCenterRadius, -mColorCenterRadius, mColorCenterRadius, mColorCenterRadius);
    }

    private int ave(int s, int d, float p) {
        return s + java.lang.Math.round(p * (d - s));
    }

    private int calculateColor(float angle) {
        float unit = (float) (angle / (2 * Math.PI));
        if (unit < 0) {
            unit += 1;
        }

        if (unit <= 0) {
            return COLORS[0];
        }
        if (unit >= 1) {
            return COLORS[COLORS.length - 1];
        }

        float p = unit * (COLORS.length - 1);
        int i = (int) p;
        p -= i;

        int c0 = COLORS[i];
        int c1 = COLORS[i + 1];
        int a = ave(Color.alpha(c0), Color.alpha(c1), p);
        int r = ave(Color.red(c0), Color.red(c1), p);
        int g = ave(Color.green(c0), Color.green(c1), p);
        int b = ave(Color.blue(c0), Color.blue(c1), p);

        return Color.argb(a, r, g, b);
    }

    public int getColor() {
        return mCenterNewColor;
    }

    public void setColor(int color) {
        mAngle = colorToAngle(color);
        mPointerColor.setColor(calculateColor(mAngle));
        mCenterNewPaint.setColor(calculateColor(mAngle));

        invalidate();
    }

    private float colorToAngle(int color) {
        float[] colors = new float[3];
        Color.colorToHSV(color, colors);

        return (float) Math.toRadians(-colors[0]);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);

        float x = event.getX() - mTranslationOffset;
        float y = event.getY() - mTranslationOffset;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float[] pointerPosition = calculatePointerPosition(mAngle);
                if (x >= (pointerPosition[0] - mColorPointerHaloRadius) && x <= (pointerPosition[0] + mColorPointerHaloRadius) && y >= (pointerPosition[1] - mColorPointerHaloRadius) && y <= (pointerPosition[1] + mColorPointerHaloRadius)) {
                    mSlopX = x - pointerPosition[0];
                    mSlopY = y - pointerPosition[1];
                    mUserIsMovingPointer = true;
                    invalidate();
                } else if (x >= -mColorCenterRadius && x <= mColorCenterRadius && y >= -mColorCenterRadius && y <= mColorCenterRadius && mShowCenterOldColor) {
                    mCenterHaloPaint.setAlpha(0x50);
                    setColor(getOldCenterColor());
                    invalidate();
                } else {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mUserIsMovingPointer) {
                    mAngle = (float) java.lang.Math.atan2(y - mSlopY, x - mSlopX);
                    mPointerColor.setColor(calculateColor(mAngle));
                    setNewCenterColor(mCenterNewColor = calculateColor(mAngle));
                    invalidate();
                } else {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                break;
            case MotionEvent.ACTION_UP:
                mUserIsMovingPointer = false;
                mCenterHaloPaint.setAlpha(0x00);

                if (onColorSelectedListener != null && mCenterNewColor != oldSelectedListenerColor) {
                    onColorSelectedListener.onColorSelected(mCenterNewColor);
                    oldSelectedListenerColor = mCenterNewColor;
                }

                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (onColorSelectedListener != null && mCenterNewColor != oldSelectedListenerColor) {
                    onColorSelectedListener.onColorSelected(mCenterNewColor);
                    oldSelectedListenerColor = mCenterNewColor;
                }
                break;
        }
        return true;
    }

    private float[] calculatePointerPosition(float angle) {
        float x = (float) (mColorWheelRadius * Math.cos(angle));
        float y = (float) (mColorWheelRadius * Math.sin(angle));

        return new float[] { x, y };
    }

    public void setNewCenterColor(int color) {
        mCenterNewColor = color;
        mCenterNewPaint.setColor(color);
        if (mCenterOldColor == 0) {
            mCenterOldColor = color;
            mCenterOldPaint.setColor(color);
        }
        if (onColorChangedListener != null && color != oldChangedListenerColor ) {
            onColorChangedListener.onColorChanged(color);
            oldChangedListenerColor  = color;
        }
        invalidate();
    }

    public void setOldCenterColor(int color) {
        mCenterOldColor = color;
        mCenterOldPaint.setColor(color);
        invalidate();
    }

    public int getOldCenterColor() {
        return mCenterOldColor;
    }

    public void setShowOldCenterColor(boolean show) {
        mShowCenterOldColor = show;
        invalidate();
    }

    public boolean getShowOldCenterColor() {
        return mShowCenterOldColor;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        Bundle state = new Bundle();
        state.putParcelable(STATE_PARENT, superState);
        state.putFloat(STATE_ANGLE, mAngle);
        state.putInt(STATE_OLD_COLOR, mCenterOldColor);
        state.putBoolean(STATE_SHOW_OLD_COLOR, mShowCenterOldColor);

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle savedState = (Bundle) state;

        Parcelable superState = savedState.getParcelable(STATE_PARENT);
        super.onRestoreInstanceState(superState);

        mAngle = savedState.getFloat(STATE_ANGLE);
        setOldCenterColor(savedState.getInt(STATE_OLD_COLOR));
        mShowCenterOldColor = savedState.getBoolean(STATE_SHOW_OLD_COLOR);
        int currentColor = calculateColor(mAngle);
        mPointerColor.setColor(currentColor);
        setNewCenterColor(currentColor);
    }
}
