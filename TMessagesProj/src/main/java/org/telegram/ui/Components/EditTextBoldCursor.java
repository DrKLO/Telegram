/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EditTextBoldCursor extends EditText {

    private Object editor;
    private static Field mEditor;
    private static Field mShowCursorField;
    private static Field mCursorDrawableField;
    private static Field mScrollYField;
    private static Method getVerticalOffsetMethod;
    private static Field mCursorDrawableResField;
    private Drawable[] mCursorDrawable;
    private GradientDrawable gradientDrawable;
    private int cursorSize;
    private int ignoreTopCount;
    private int ignoreBottomCount;
    private int scrollY;
    private float lineSpacingExtra;
    private Rect rect = new Rect();
    private StaticLayout hintLayout;
    private int hintColor;
    private boolean hintVisible = true;
    private float hintAlpha = 1.0f;
    private long lastUpdateTime;
    private boolean allowDrawCursor = true;
    private float cursorWidth = 2.0f;

    public EditTextBoldCursor(Context context) {
        super(context);

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
            mCursorDrawable = (Drawable[]) mCursorDrawableField.get(editor);
            mCursorDrawableResField.set(this, R.drawable.field_carret_empty);
        } catch (Exception e) {
            FileLog.e(e);
        }
        cursorSize = AndroidUtilities.dp(24);
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

    public void setHintText(String value) {
        hintLayout = new StaticLayout(value, getPaint(), AndroidUtilities.dp(1000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
        if (length() == 0 && hintLayout != null && (hintVisible || hintAlpha != 0)) {
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
            getPaint().setColor(hintColor);
            getPaint().setAlpha((int) (255 * hintAlpha));
            canvas.save();
            int left = 0;
            float lineLeft = hintLayout.getLineLeft(0);
            if (lineLeft != 0) {
                left -= lineLeft;
            }
            canvas.translate(left, (getMeasuredHeight() - hintLayout.getHeight()) / 2.0f);
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
    }
}
