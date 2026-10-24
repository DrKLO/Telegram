package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

public final class PersianFontSpan extends MetricAffectingSpan {

    private static final float TEXT_SIZE_SCALE = 0.94f;

    private final Typeface typeface;
    private final boolean italic;

    public PersianFontSpan(Typeface typeface) {
        this(typeface, false);
    }

    public PersianFontSpan(Typeface typeface, boolean italic) {
        this.typeface = typeface;
        this.italic = italic;
    }

    boolean matches(PersianFontSpan other) {
        return typeface == other.typeface && italic == other.italic;
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint textPaint) {
        applyTypeface(textPaint);
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        applyTypeface(tp);
    }

    private void applyTypeface(TextPaint paint) {
        if (typeface == null) {
            return;
        }

        paint.setTypeface(typeface);
        // Apply optical sizing to this script run in both measurement and drawing.
        paint.setTextSize(paint.getTextSize() * TEXT_SIZE_SCALE);
        paint.setFakeBoldText(false);
        paint.setTextSkewX(italic ? -0.25f : 0f);
    }
}
