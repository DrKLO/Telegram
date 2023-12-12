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
        return resourcesProvider.getColor(key);
    }

    @Override
    public int getColorOrDefault(int key) {
        return resourcesProvider.getColorOrDefault(key);
    }

    @Override
    public int getCurrentColor(int key) {
        return resourcesProvider.getCurrentColor(key);
    }

    @Override
    public void setAnimatedColor(int key, int color) {
        resourcesProvider.setAnimatedColor(key, color);
    }

    @Override
    public Drawable getDrawable(String drawableKey) {
        return resourcesProvider.getDrawable(drawableKey);
    }

    @Override
    public Paint getPaint(String paintKey) {
        return resourcesProvider.getPaint(paintKey);
    }

    @Override
    public boolean hasGradientService() {
        return resourcesProvider.hasGradientService();
    }

    @Override
    public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
        resourcesProvider.applyServiceShaderMatrix(w, h, translationX, translationY);
    }

    @Override
    public ColorFilter getAnimatedEmojiColorFilter() {
        return resourcesProvider.getAnimatedEmojiColorFilter();
    }
}
