/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class TextPaintSpan extends MetricAffectingSpan {

    private TextPaint textPaint;

    public TextPaintSpan(TextPaint paint) {
        textPaint = paint;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        p.setColor(textPaint.getColor());
        p.setTypeface(textPaint.getTypeface());
        p.setFlags(textPaint.getFlags());
        p.setTextSize(textPaint.getTextSize());
        p.baselineShift = textPaint.baselineShift;
        p.bgColor = textPaint.bgColor;
    }

    @Override
    public void updateDrawState(TextPaint p) {
        p.setColor(textPaint.getColor());
        p.setTypeface(textPaint.getTypeface());
        p.setFlags(textPaint.getFlags());
        p.setTextSize(textPaint.getTextSize());
        p.baselineShift = textPaint.baselineShift;
        p.bgColor = textPaint.bgColor;
    }
}
