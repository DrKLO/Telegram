/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

public class TextPaintUrlSpan extends MetricAffectingSpan {

    private TextPaint textPaint;
    private int textSize;
    private int color;
    private String currentUrl;

    public TextPaintUrlSpan(TextPaint paint, String url) {
        textPaint = paint;
        currentUrl = url;
    }

    public String getUrl() {
        return currentUrl;
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        if (textPaint != null) {
            p.setColor(textPaint.getColor());
            p.setTypeface(textPaint.getTypeface());
            p.setFlags(textPaint.getFlags());
        }
    }

    @Override
    public void updateDrawState(TextPaint p) {
        if (textPaint != null) {
            p.setColor(textPaint.getColor());
            p.setTypeface(textPaint.getTypeface());
            p.setFlags(textPaint.getFlags());
        }
    }
}
