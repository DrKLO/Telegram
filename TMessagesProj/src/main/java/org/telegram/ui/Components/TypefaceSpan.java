/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class TypefaceSpan extends MetricAffectingSpan {

    private Typeface typeface;
    private int textSize;
    private int color;
    private int colorKey = -1;
    Theme.ResourcesProvider resourcesProvider;

    public TypefaceSpan(Typeface tf) {
        typeface = tf;
    }

    public TypefaceSpan(Typeface tf, int size) {
        typeface = tf;
        textSize = size;
    }

    public TypefaceSpan(Typeface tf, int size, int textColor) {
        typeface = tf;
        if (size > 0) {
            textSize = size;
        }
        color = textColor;
    }

    public TypefaceSpan(Typeface tf, int size, int colorKey, Theme.ResourcesProvider resourcesProvider) {
        typeface = tf;
        if (size > 0) {
            textSize = size;
        }
        this.resourcesProvider = resourcesProvider;
        this.colorKey = colorKey;
        color = Theme.getColor(colorKey, resourcesProvider);
    }

    public Typeface getTypeface() {
        return typeface;
    }

    public void setColor(int value) {
        color = value;
    }

    public boolean isMono() {
        return typeface == Typeface.MONOSPACE;
    }

    public boolean isBold() {
        return typeface == AndroidUtilities.bold();
    }

    public boolean isItalic() {
        return typeface == AndroidUtilities.getTypeface("fonts/ritalic.ttf");
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        if (typeface != null) {
            p.setTypeface(typeface);
        }
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        if (colorKey >= 0) {
            color = Theme.getColor(colorKey, resourcesProvider);
        }
        if (typeface != null) {
            tp.setTypeface(typeface);
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
