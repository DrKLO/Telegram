/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.SystemClock;
import android.support.annotation.Keep;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EditTextBoldCursor extends EditText {

    private static Field mEditor;
    private static Field mShowCursorField;
    private static Field mCursorDrawableField;
    private static Field mScrollYField;
    private static Method getVerticalOffsetMethod;
    private static Field mCursorDrawableResField;

    private Drawable[] mCursorDrawable;
    private Object editor;

    private GradientDrawable gradientDrawable;

    private Paint linePaint;
    private TextPaint errorPaint;

    private int cursorSize;
    private int ignoreTopCount;
    private int ignoreBottomCount;
    private int scrollY;
    private float lineSpacingExtra;
    private Rect rect = new Rect();
    private StaticLayout hintLayout;
    private StaticLayout errorLayout;
    private CharSequence errorText;
    private int hintColor;
    private int headerHintColor;
    private boolean hintVisible = true;
    private float hintAlpha = 1.0f;
    private long lastUpdateTime;
    private boolean allowDrawCursor = true;
    private float cursorWidth = 2.0f;
    private boolean supportRtlHint;

    private int lineColor;
    private int activeLineColor;
    private int errorLineColor;
    private float lineY;

    private boolean nextSetTextAnimated;
    private boolean transformHintToHeader;
    private boolean currentDrawHintAsHeader;
    private AnimatorSet headerTransformAnimation;
    private float headerAnimationProgress;

    public EditTextBoldCursor(Context context) {
        super(context);

        linePaint = new Paint();
        errorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setTextSize(AndroidUtilities.dp(11));

        if (mCursorDrawableField == null) {
            try {
                mScrollYField = View.class.getDeclaredField("mScrollY");
                mScrollYField.setAccessible(true);
                mCursorDrawableResField = TextView.class.getDeclaredField("mCursorDrawableRes");
                mCursorDrawableResField.setAccessible(true);
                mEditor = TextView.class.getDeclaredField("mEditor");
                mEditor.setAccessible(true);
                Class editorClass = Class.forName("android.widget.Editor");
                mShowCursorField = editorClass.getDeclaredField("mShowCursor");
                mShowCursorField.setAccessible(true);
                mCursorDrawableField = editorClass.getDeclaredField("mCursorDrawable");
                mCursorDrawableField.setAccessible(true);
                getVerticalOffsetMethod = TextView.class.getDeclaredMethod("getVerticalOffset", boolean.class);
                getVerticalOffsetMethod.setAccessible(true);
            } catch (Throwable e) {
                //
            }
        }
        try {
            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xff54a1db, 0xff54a1db});
            editor = mEditor.get(this);
            if (mCursorDrawableField != null) {
                mCursorDrawable = (Drawable[]) mCursorDrawableField.get(editor);
                mCursorDrawableResField.set(this, R.drawable.field_carret_empty);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        cursorSize = AndroidUtilities.dp(24);
    }

    public void setTransformHintToHeader(boolean value) {
        if (transformHintToHeader == value) {
            return;
        }
        transformHintToHeader = value;
        if (headerTransformAnimation != null) {
            headerTransformAnimation.cancel();
            headerTransformAnimation = null;
        }
    }

    public void setAllowDrawCursor(boolean value) {
        allowDrawCursor = value;
    }

    public void setCursorWidth(float width) {
        cursorWidth = width;
    }

    public void setCursorColor(int color) {
        gradientDrawable.setColor(color);
        invalidate();
    }

    public void setCursorSize(int value) {
        cursorSize = value;
    }

    public void setErrorLineColor(int error) {
        errorLineColor = error;
        errorPaint.setColor(errorLineColor);
        invalidate();
    }

    public void setLineColors(int color, int active, int error) {
        lineColor = color;
        activeLineColor = active;
        errorLineColor = error;
        errorPaint.setColor(errorLineColor);
        invalidate();
    }

    public void setHintVisible(boolean value) {
        if (hintVisible == value) {
            return;
        }
        lastUpdateTime = System.currentTimeMillis();
        hintVisible = value;
        invalidate();
    }

    public void setHintColor(int value) {
        hintColor = value;
        invalidate();
    }

    public void setHeaderHintColor(int value) {
        headerHintColor = value;
        invalidate();
    }

    public void setNextSetTextAnimated(boolean value) {
        nextSetTextAnimated = value;
    }

    public void setErrorText(CharSequence text) {
        if (TextUtils.equals(text, errorText)) {
            return;
        }
        errorText = text;
        requestLayout();
    }

    public boolean hasErrorText() {
        return !TextUtils.isEmpty(errorText);
    }

    public StaticLayout getErrorLayout(int width) {
        if (TextUtils.isEmpty(errorText)) {
            return null;
        } else {
            return new StaticLayout(errorText, errorPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }

    public float getLineY() {
        return lineY;
    }

    public void setSupportRtlHint(boolean value) {
        supportRtlHint = value;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        checkHeaderVisibility(nextSetTextAnimated);
        nextSetTextAnimated = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (hintLayout != null) {
            lineY = (getMeasuredHeight() - hintLayout.getHeight()) / 2.0f + hintLayout.getHeight() + AndroidUtilities.dp(6);
        }
    }

    public void setHintText(String text) {
        hintLayout = new StaticLayout(text, getPaint(), AndroidUtilities.dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        checkHeaderVisibility(true);
    }

    private void checkHeaderVisibility(boolean animated) {
        boolean newHintHeader = transformHintToHeader && (isFocused() || getText().length() > 0);
        if (currentDrawHintAsHeader != newHintHeader) {
            if (headerTransformAnimation != null) {
                headerTransformAnimation.cancel();
                headerTransformAnimation = null;
            }
            currentDrawHintAsHeader = newHintHeader;
            if (animated) {
                headerTransformAnimation = new AnimatorSet();
                headerTransformAnimation.playTogether(ObjectAnimator.ofFloat(this, "headerAnimationProgress", newHintHeader ? 1.0f : 0.0f));
                headerTransformAnimation.setDuration(200);
                headerTransformAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                headerTransformAnimation.start();
            } else {
                headerAnimationProgress = newHintHeader ? 1.0f : 0.0f;
            }
            invalidate();
        }
    }

    @Keep
    public void setHeaderAnimationProgress(float value) {
        headerAnimationProgress = value;
        invalidate();
    }

    @Keep
    public float getHeaderAnimationProgress() {
        return headerAnimationProgress;
    }

    @Override
    public void setLineSpacing(float add, float mult) {
        super.setLineSpacing(add, mult);
        lineSpacingExtra = add;
    }

    @Override
    public int getExtendedPaddingTop() {
        if (ignoreTopCount != 0) {
            ignoreTopCount--;
            return 0;
        }
        return super.getExtendedPaddingTop();
    }

    @Override
    public int getExtendedPaddingBottom() {
        if (ignoreBottomCount != 0) {
            ignoreBottomCount--;
            return scrollY != Integer.MAX_VALUE ? -scrollY : 0;
        }
        return super.getExtendedPaddingBottom();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int topPadding = getExtendedPaddingTop();
        scrollY = Integer.MAX_VALUE;
        try {
            scrollY = mScrollYField.getInt(this);
            mScrollYField.set(this, 0);
        } catch (Exception e) {
            //
        }
        ignoreTopCount = 1;
        ignoreBottomCount = 1;
        canvas.save();
        canvas.translate(0, topPadding);
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            //
        }
        if (scrollY != Integer.MAX_VALUE) {
            try {
                mScrollYField.set(this, scrollY);
            } catch (Exception e) {
                //
            }
        }
        canvas.restore();
        if ((length() == 0 || transformHintToHeader) && hintLayout != null && (hintVisible || hintAlpha != 0)) {
            if (hintVisible && hintAlpha != 1.0f || !hintVisible && hintAlpha != 0.0f) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastUpdateTime;
                if (dt < 0 || dt > 17) {
                    dt = 17;
                }
                lastUpdateTime = newTime;
                if (hintVisible) {
                    hintAlpha += dt / 150.0f;
                    if (hintAlpha > 1.0f) {
                        hintAlpha = 1.0f;
                    }
                } else {
                    hintAlpha -= dt / 150.0f;
                    if (hintAlpha < 0.0f) {
                        hintAlpha = 0.0f;
                    }
                }
                invalidate();
            }
            int oldColor = getPaint().getColor();

            canvas.save();
            int left = 0;
            float lineLeft = hintLayout.getLineLeft(0);
            float hintWidth = hintLayout.getLineWidth(0);
            if (lineLeft != 0) {
                left -= lineLeft;
            }
            if (supportRtlHint && LocaleController.isRTL) {
                float offset = getMeasuredWidth() - hintWidth;
                canvas.translate(left + getScrollX() + offset, lineY - hintLayout.getHeight() - AndroidUtilities.dp(6));
            } else {
                canvas.translate(left + getScrollX(), lineY - hintLayout.getHeight() - AndroidUtilities.dp(6));
            }
            if (transformHintToHeader) {
                float scale = 1.0f - 0.3f * headerAnimationProgress;
                float translation = -AndroidUtilities.dp(22) * headerAnimationProgress;
                int rF = Color.red(headerHintColor);
                int gF = Color.green(headerHintColor);
                int bF = Color.blue(headerHintColor);
                int aF = Color.alpha(headerHintColor);
                int rS = Color.red(hintColor);
                int gS = Color.green(hintColor);
                int bS = Color.blue(hintColor);
                int aS = Color.alpha(hintColor);

                if (supportRtlHint && LocaleController.isRTL) {
                    canvas.translate((hintWidth + lineLeft) - (hintWidth + lineLeft) * scale, 0);
                }
                canvas.scale(scale, scale);
                canvas.translate(0, translation);
                getPaint().setColor(Color.argb((int) (aS + (aF - aS) * headerAnimationProgress), (int) (rS + (rF - rS) * headerAnimationProgress), (int) (gS + (gF - gS) * headerAnimationProgress), (int) (bS + (bF - bS) * headerAnimationProgress)));
            } else {
                getPaint().setColor(hintColor);
                getPaint().setAlpha((int) (255 * hintAlpha * (Color.alpha(hintColor) / 255.0f)));
            }
            hintLayout.draw(canvas);
            getPaint().setColor(oldColor);
            canvas.restore();
        }
        try {
            if (allowDrawCursor && mShowCursorField != null && mCursorDrawable != null && mCursorDrawable[0] != null) {
                long mShowCursor = mShowCursorField.getLong(editor);
                boolean showCursor = (SystemClock.uptimeMillis() - mShowCursor) % (2 * 500) < 500 && isFocused();
                if (showCursor) {
                    canvas.save();
                    int voffsetCursor = 0;
                    if ((getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                        voffsetCursor = (int) getVerticalOffsetMethod.invoke(this, true);
                    }
                    canvas.translate(getPaddingLeft(), getExtendedPaddingTop() + voffsetCursor);
                    Layout layout = getLayout();
                    int line = layout.getLineForOffset(getSelectionStart());
                    int lineCount = layout.getLineCount();
                    Rect bounds = mCursorDrawable[0].getBounds();
                    rect.left = bounds.left;
                    rect.right = bounds.left + AndroidUtilities.dp(cursorWidth);
                    rect.bottom = bounds.bottom;
                    rect.top = bounds.top;
                    if (lineSpacingExtra != 0 && line < lineCount - 1) {
                        rect.bottom -= lineSpacingExtra;
                    }
                    rect.top = rect.centerY() - cursorSize / 2;
                    rect.bottom = rect.top + cursorSize;
                    gradientDrawable.setBounds(rect);
                    gradientDrawable.draw(canvas);
                    canvas.restore();
                }
            }
        } catch (Throwable e) {
            //ignore
        }
        if (lineColor != 0 && hintLayout != null) {
            int h;
            if (!TextUtils.isEmpty(errorText)) {
                linePaint.setColor(errorLineColor);
                h = AndroidUtilities.dp(2);
            } else if (isFocused()) {
                linePaint.setColor(activeLineColor);
                h = AndroidUtilities.dp(2);
            } else {
                linePaint.setColor(lineColor);
                h = AndroidUtilities.dp(1);
            }
            canvas.drawRect(getScrollX(), (int) lineY, getScrollX() + getMeasuredWidth(), lineY + h, linePaint);
        }
        /*if (errorLayout != null) {
            canvas.save();
            canvas.translate(getScrollX(), lineY + AndroidUtilities.dp(3));
            errorLayout.draw(canvas);
            canvas.restore();
        }*/
    }
}
