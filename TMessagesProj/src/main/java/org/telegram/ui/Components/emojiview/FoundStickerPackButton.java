package org.telegram.ui.Components.emojiview;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FoundStickerPackButton extends ButtonWithCounterView implements FactorAnimator.Target {
    private final BoolAnimator animatorIsPrimary = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);
    private final Theme.ResourcesProvider resourcesProvider;

    public FoundStickerPackButton(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        setRound();

        setOutlineProvider(ViewOutlineProviderImpl.BOUNDS_ROUND_RECT);
    }

    public void setIsPrimary(boolean isPrimary, boolean animated) {
        animatorIsPrimary.setValue(isPrimary, animated);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        checkUi_colors();
    }

    private boolean uiColorsApplied;

    /** re-applies the blended theme colors in place after a theme color update (Theme.Colorable through the super class) */
    @Override
    public void updateColors() {
        super.updateColors();
        if (animatorIsPrimary == null || !uiColorsApplied) {
            return; // called from the super constructor, or colors never set by checkUi_colors()
        }
        try {
            final float factor = animatorIsPrimary.getFloatValue();
            final int color = ColorUtils.blendARGB(
                getThemedColor(Theme.key_windowBackgroundWhite),
                getThemedColor(Theme.key_featuredStickers_addButton), factor);
            final Drawable background = getBackground();
            if (background instanceof ShapeDrawable) {
                if (((ShapeDrawable) background).getPaint().getColor() != color) {
                    ((ShapeDrawable) background).getPaint().setColor(color);
                    background.invalidateSelf();
                }
            }
            setTextColor(ColorUtils.blendARGB(
                getThemedColor(Theme.key_text_RedBold),
                getThemedColor(Theme.key_featuredStickers_buttonText), factor));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void checkUi_colors() {
        uiColorsApplied = true;
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final float factor = animatorIsPrimary.getFloatValue();

        setElevation(dp(1) * (1f - factor));

        setColor(ColorUtils.blendARGB(
            getThemedColor(Theme.key_windowBackgroundWhite),
            getThemedColor(Theme.key_featuredStickers_addButton), factor));

        setTextColor(ColorUtils.blendARGB(
            getThemedColor(Theme.key_text_RedBold),
            getThemedColor(Theme.key_featuredStickers_buttonText), factor));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (isDark) {
                setOutlineAmbientShadowColor(0x20FFFFFF);
                setOutlineSpotShadowColor(0x20FFFFFF);
            } else {
                setOutlineAmbientShadowColor(0x60000000);
                setOutlineSpotShadowColor(0x60000000);
            }
        }
    }

    private int getThemedColor(int key) {
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(key);
        }
        return Theme.getColor(key);
    }
}
