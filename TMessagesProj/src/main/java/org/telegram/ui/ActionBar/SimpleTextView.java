/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class SimpleTextView extends View implements Drawable.Callback {

    private Layout layout;
    private TextPaint textPaint;
    private int gravity = Gravity.LEFT | Gravity.TOP;
    private CharSequence text;
    private SpannableStringBuilder spannableStringBuilder;
    private Drawable leftDrawable;
    private Drawable rightDrawable;
    private int drawablePadding = AndroidUtilities.dp(4);
    private int leftDrawableTopPadding;
    private int rightDrawableTopPadding;

    private int offsetX;
    private int textWidth;
    private int textHeight;
    private boolean wasLayout;

    public enum Alignment {
        ALIGN_NORMAL,
        ALIGN_OPPOSITE,
        ALIGN_CENTER,
        ALIGN_LEFT,
        ALIGN_RIGHT
    }

    public SimpleTextView(Context context) {
        super(context);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        wasLayout = false;
    }

    public void setTextSize(int size) {
        int newSize = AndroidUtilities.dp(size);
        if (newSize == textPaint.getTextSize()) {
            return;
        }
        textPaint.setTextSize(newSize);
        recreateLayoutMaybe();
    }

    public void setGravity(int value) {
        gravity = value;
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
    }

    public int getSideDrawablesSize() {
        int size = 0;
        if (leftDrawable != null) {
            size += leftDrawable.getIntrinsicWidth() + drawablePadding;
        }
        if (rightDrawable != null) {
            size += rightDrawable.getIntrinsicWidth() + drawablePadding;
        }
        return size;
    }

    public Paint getPaint() {
        return textPaint;
    }

    private void createLayout(int width) {
        if (text != null) {
            try {
                if (leftDrawable != null) {
                    width -= leftDrawable.getIntrinsicWidth();
                    width -= drawablePadding;
                }
                if (rightDrawable != null) {
                    width -= rightDrawable.getIntrinsicWidth();
                    width -= drawablePadding;
                }
                width -= getPaddingLeft() + getPaddingRight();
                CharSequence string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                if (layout != null && TextUtils.equals(layout.getText(), string)) {
                    return;
                }
                layout = new StaticLayout(string, 0, string.length(), textPaint, width + AndroidUtilities.dp(8), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                if (layout.getLineCount() > 0) {
                    textWidth = (int) Math.ceil(layout.getLineWidth(0));
                    textHeight = layout.getLineBottom(0);
                    if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                        offsetX = -(int) layout.getLineLeft(0);
                    } else if (layout.getLineLeft(0) == 0) {
                        offsetX = width - textWidth;
                    } else {
                        offsetX = 0;
                    }
                }
            } catch (Exception e) {
                //ignore
            }
        } else {
            layout = null;
            textWidth = 0;
            textHeight = 0;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        createLayout(width - getPaddingLeft() - getPaddingRight());

        int finalHeight;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            finalHeight = height;
        } else {
            finalHeight = textHeight;
        }
        setMeasuredDimension(width, finalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            wasLayout = true;
        }
    }

    public int getTextWidth() {
        return textWidth;
    }

    public int getTextHeight() {
        return textHeight;
    }

    public void setLeftDrawableTopPadding(int value) {
        leftDrawableTopPadding = value;
    }

    public void setRightDrawableTopPadding(int value) {
        rightDrawableTopPadding = value;
    }

    public void setLeftDrawable(int resId) {
        setLeftDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setRightDrawable(int resId) {
        setRightDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setLeftDrawable(Drawable drawable) {
        if (leftDrawable == drawable) {
            return;
        }
        if (leftDrawable != null) {
            leftDrawable.setCallback(null);
        }
        leftDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        recreateLayoutMaybe();
    }

    public void setRightDrawable(Drawable drawable) {
        if (rightDrawable == drawable) {
            return;
        }
        if (rightDrawable != null) {
            rightDrawable.setCallback(null);
        }
        rightDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        recreateLayoutMaybe();
    }

    public void setText(CharSequence value) {
        if (text == null && value == null || text != null && value != null && text.equals(value)) {
            return;
        }
        text = value;
        recreateLayoutMaybe();
    }

    public void setDrawablePadding(int value) {
        if (drawablePadding == value) {
            return;
        }
        drawablePadding = value;
        recreateLayoutMaybe();
    }

    private void recreateLayoutMaybe() {
        if (wasLayout) {
            createLayout(getMeasuredWidth());
            invalidate();
        } else {
            requestLayout();
        }
    }

    public CharSequence getText() {
        if (text == null) {
            return "";
        }
        return text;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int textOffsetX = 0;
        if (leftDrawable != null) {
            int y = (textHeight - leftDrawable.getIntrinsicHeight()) / 2 + leftDrawableTopPadding;
            leftDrawable.setBounds(0, y, leftDrawable.getIntrinsicWidth(), y + leftDrawable.getIntrinsicHeight());
            leftDrawable.draw(canvas);
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                textOffsetX += drawablePadding + leftDrawable.getIntrinsicWidth();
            }
        }
        if (rightDrawable != null) {
            int x = textOffsetX + textWidth + drawablePadding;
            if (leftDrawable != null) {
                x += drawablePadding + leftDrawable.getIntrinsicWidth();
            }
            int y = (textHeight - rightDrawable.getIntrinsicHeight()) / 2 + rightDrawableTopPadding;
            rightDrawable.setBounds(x, y, x + rightDrawable.getIntrinsicWidth(), y + rightDrawable.getIntrinsicHeight());
            rightDrawable.draw(canvas);
        }
        if (layout != null) {
            if (offsetX + textOffsetX != 0) {
                canvas.save();
                canvas.translate(offsetX + textOffsetX, 0);
            }
            layout.draw(canvas);
            if (offsetX + textOffsetX != 0) {
                canvas.restore();
            }
        }
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidate();
    }
}
