/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class SimpleTextView extends View {

    private Layout layout;
    private TextPaint textPaint;
    private int gravity;
    private CharSequence text;
    private SpannableStringBuilder spannableStringBuilder;

    private int offsetX;
    private boolean wasLayout = false;

    public SimpleTextView(Context context) {
        super(context);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
    }

    public void setTextSize(int size) {
        textPaint.setTextSize(AndroidUtilities.dp(size));
    }

    public void setGravity(int value) {
        gravity = value;
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
    }

    private void createLayout(int width) {
        if (text != null) {
            try {
                CharSequence string = TextUtils.ellipsize(text, textPaint, width, TextUtils.TruncateAt.END);
                layout = new StaticLayout(string, 0, string.length(), textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                /*if (metrics == null) {
                    metrics = BoringLayout.isBoring(text, textPaint);
                }
                if (layout == null) {
                    layout = BoringLayout.make(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, metrics, false, TextUtils.TruncateAt.END, width);
                } else {
                    layout = ((BoringLayout) layout).replaceOrMake(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, metrics, false, TextUtils.TruncateAt.END, width);
                }*/

                /*if (spannableStringBuilder == null) {
                    spannableStringBuilder = new SpannableStringBuilder(text);
                    layout = new DynamicLayout(text, text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width);
                } else {
                    spannableStringBuilder.replace(0, text.length(), text);
                }*/

                if (layout.getLineCount() > 0) {
                    if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
                        offsetX = -(int) layout.getLineLeft(0);
                    } else if (layout.getLineLeft(0) == 0) {
                        offsetX = (int) (width - layout.getLineWidth(0));
                    } else {
                        offsetX = 0;
                    }
                    offsetX += getPaddingLeft();
                }
            } catch (Exception e) {
                //ignore
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            createLayout(right - left - getPaddingLeft() - getPaddingRight());
            invalidate();
            wasLayout = true;
        }
    }

    public void setText(CharSequence value) {
        text = value;
        if (wasLayout) {
            createLayout(getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            invalidate();
        } else {
            requestLayout();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (layout != null) {
            if (offsetX != 0) {
                canvas.save();
                canvas.translate(offsetX, 0);
            }
            layout.draw(canvas);
            if (offsetX != 0) {
                canvas.restore();
            }
        }
    }
}
