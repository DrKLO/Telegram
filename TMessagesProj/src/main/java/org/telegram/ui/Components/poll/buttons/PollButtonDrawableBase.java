package org.telegram.ui.Components.poll.buttons;

import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

public abstract class PollButtonDrawableBase extends Drawable {
    protected final Theme.ResourcesProvider resourcesProvider;
    protected final Drawable selectorDrawable;
    protected int selectorDrawableColor;


    public PollButtonDrawableBase(Theme.ResourcesProvider resourcesProvider) {
        this.selectorDrawableColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.selectorDrawable = Theme.createRadSelectorDrawable(selectorDrawableColor, 0, 0);
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    @CallSuper
    public void setupCallbacks(Drawable.Callback callback) {
        setCallback(callback);
        selectorDrawable.setCallback(callback);
    }

    @CallSuper
    public boolean verifyDrawable(Drawable who) {
        return who == this || who == selectorDrawable;
    }

    public final void setSelectorsColor(int color) {
        if (selectorDrawableColor != color) {
            onSelectorColorChanged(color);
            selectorDrawableColor = color;
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        selectorDrawable.setBounds(bounds);
    }

    @CallSuper
    public void resetSelectors() {
        selectorDrawable.setState(StateSet.NOTHING);
    }

    @CallSuper
    protected void onAlphaChanged(int alpha) {
        selectorDrawable.setAlpha(alpha);
    }

    private int alpha = 255;

    @Override
    public final void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            onAlphaChanged(alpha);
        }
    }

    @Override
    public final int getAlpha() {
        return alpha;
    }

    @CallSuper
    protected void onSelectorColorChanged(int color) {
        Theme.setSelectorDrawableColor(selectorDrawable, color, false);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
