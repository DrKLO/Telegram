/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

import org.telegram.messenger.FileLog;

public class TightTextView extends TextView {
    private boolean hasMaxWidth;
    public int maxWidth;

    public int lastLineWidth = 0;
    public int linesMaxWidth = 0;
    public int lines = 0;

    public TightTextView(Context context) {
        this(context, null, 0);
    }

    public TightTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TightTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int measuredWidth = getMeasuredWidth();
            Layout layout = getLayout();
            lines = layout.getLineCount();
            float lastLeft = layout.getLineLeft(lines - 1);
            float lastLine = layout.getLineWidth(lines - 1);
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;
            boolean hasNonRTL = false;
            linesMaxWidth = lastLineWidth = (int)Math.ceil(lastLine);
            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int)Math.ceil(lastLine + lastLeft);
            if (lastLeft == 0) {
                hasNonRTL = true;
            }
            if (hasMaxWidth) {
                int specModeW = MeasureSpec.getMode(widthMeasureSpec);
                if (specModeW != MeasureSpec.EXACTLY) {
                    if (lines > 1) {
                        float textRealMaxWidth = 0, textRealMaxWidthWithLeft = 0;
                        for (int n = 0; n < lines; ++n) {
                            float lineWidth;
                            float lineLeft;
                            try {
                                lineWidth = layout.getLineWidth(n);
                                lineLeft = layout.getLineLeft(n);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                                return;
                            }

                            if (lineLeft == 0) {
                                hasNonRTL = true;
                            }
                            textRealMaxWidth = Math.max(textRealMaxWidth, lineWidth);
                            textRealMaxWidthWithLeft = Math.max(textRealMaxWidthWithLeft, lineWidth + lineLeft);
                            linesMaxWidth = Math.max(linesMaxWidth, (int)Math.ceil(lineWidth));
                            linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int)Math.ceil(lineWidth + lineLeft));
                        }
                        if (hasNonRTL) {
                            textRealMaxWidth = textRealMaxWidthWithLeft;
                            lastLineWidth = lastLineWidthWithLeft;
                            linesMaxWidth = linesMaxWidthWithLeft;
                        } else {
                            lastLineWidth = linesMaxWidth;
                        }
                        int w = (int)Math.ceil(textRealMaxWidth);
                        if (w < getMeasuredWidth()) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), heightMeasureSpec);
                        }
                    } else {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(maxWidth, linesMaxWidth), MeasureSpec.AT_MOST), heightMeasureSpec);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            try {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            } catch (Exception e2) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                FileLog.e("tmessages", e2);
            }
        }
    }

    @Override
    public void setMaxWidth(int maxpixels) {
        super.setMaxWidth(maxpixels);
        hasMaxWidth = true;
        maxWidth = maxpixels;
    }

    @Override
    public void setMaxEms(int maxems) {
        super.setMaxEms(maxems);
        hasMaxWidth = true;
    }
}
