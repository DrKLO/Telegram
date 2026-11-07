package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import org.telegram.messenger.R;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CombinedDrawable;

@SuppressLint("ViewConstructor")
public class TextInfoCell extends TextInfoPrivacyCell {
    private final Theme.ResourcesProvider resourcesProvider;

    public TextInfoCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
    }

    public void setBackground(boolean onlyTopDivider) {
        Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), onlyTopDivider ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
        Drawable background = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        themedBackground = (ColorDrawable) background;
        themedShadow = shadowDrawable;
        CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        setBackground(combinedDrawable);
    }

    private ColorDrawable themedBackground;
    private Drawable themedShadow;
    private int themedShadowColor;
    private boolean themedShadowColorSet;

    @Override
    public void updateColors() {
        super.updateColors();
        try {
            // fields are null while the super constructor runs
            if (themedBackground != null) {
                themedBackground.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            }
            if (themedShadow != null) {
                final int shadowColor = Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider);
                if (shadowColor != themedShadowColor || !themedShadowColorSet) {
                    themedShadowColor = shadowColor;
                    themedShadowColorSet = true;
                    themedShadow.setColorFilter(new PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
