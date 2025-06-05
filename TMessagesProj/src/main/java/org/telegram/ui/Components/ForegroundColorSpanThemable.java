package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;

public class ForegroundColorSpanThemable extends CharacterStyle implements UpdateAppearance {

    private int color;
    private int colorKey;
    private final Theme.ResourcesProvider resourcesProvider;

    public ForegroundColorSpanThemable(int colorKey) {
        this(colorKey, null);
    }

    public ForegroundColorSpanThemable(int colorKey, Theme.ResourcesProvider resourcesProvider) {
        this.colorKey = colorKey;
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        color = Theme.getColor(colorKey, resourcesProvider);
        if (textPaint.getColor() != color) {
            textPaint.setColor(color);
        }
    }
}
