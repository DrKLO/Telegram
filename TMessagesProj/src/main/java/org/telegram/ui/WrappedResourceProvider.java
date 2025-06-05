package org.telegram.ui;

import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

public class WrappedResourceProvider implements Theme.ResourcesProvider {

    public SparseIntArray sparseIntArray = new SparseIntArray();

    Theme.ResourcesProvider resourcesProvider;
    public WrappedResourceProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        appendColors();
    }

    public void appendColors() {

    }

    @Override
    public int getColor(int key) {
        int index = sparseIntArray.indexOfKey(key);
        if (index >= 0) {
            return sparseIntArray.valueAt(index);
        }
        if (resourcesProvider == null) {
            return Theme.getColor(key);
        }
        return resourcesProvider.getColor(key);
    }

    @Override
    public int getColorOrDefault(int key) {
        if (resourcesProvider == null) {
            return Theme.getColor(key);
        }
        return resourcesProvider.getColorOrDefault(key);
    }

    @Override
    public int getCurrentColor(int key) {
        if (resourcesProvider == null) return Theme.getColor(key);
        return resourcesProvider.getCurrentColor(key);
    }

    @Override
    public void setAnimatedColor(int key, int color) {
        if (resourcesProvider != null) {
            resourcesProvider.setAnimatedColor(key, color);
        }
    }

    @Override
    public Drawable getDrawable(String drawableKey) {
        if (resourcesProvider == null) {
            return Theme.getThemeDrawable(drawableKey);
        }
        return resourcesProvider.getDrawable(drawableKey);
    }

    @Override
    public Paint getPaint(String paintKey) {
        if (resourcesProvider == null) {
            return Theme.getThemePaint(paintKey);
        }
        return resourcesProvider.getPaint(paintKey);
    }

    @Override
    public boolean hasGradientService() {
        if (resourcesProvider == null) {
            return Theme.hasGradientService();
        }
        return resourcesProvider.hasGradientService();
    }

    @Override
    public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
        if (resourcesProvider == null) {
            Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
        } else {
            resourcesProvider.applyServiceShaderMatrix(w, h, translationX, translationY);
        }
    }

    @Override
    public ColorFilter getAnimatedEmojiColorFilter() {
        if (resourcesProvider == null) {
            return Theme.getAnimatedEmojiColorFilter(null);
        }
        return resourcesProvider.getAnimatedEmojiColorFilter();
    }
}
