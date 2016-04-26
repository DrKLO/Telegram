/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class TypefaceSpan extends MetricAffectingSpan {

    private Typeface mTypeface;
    private int textSize;
    private int color;

    public TypefaceSpan(Typeface typeface) {
        mTypeface = typeface;
    }

    public TypefaceSpan(Typeface typeface, int size) {
        mTypeface = typeface;
        textSize = size;
    }

    public TypefaceSpan(Typeface typeface, int size, int textColor) {
        mTypeface = typeface;
        textSize = size;
        color = textColor;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        if (mTypeface != null) {
            p.setTypeface(mTypeface);
        }
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        if (mTypeface != null) {
            tp.setTypeface(mTypeface);
        }
        if (textSize != 0) {
            tp.setTextSize(textSize);
        }
        if (color != 0) {
            tp.setColor(color);
        }
        tp.setFlags(tp.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
    }
}
