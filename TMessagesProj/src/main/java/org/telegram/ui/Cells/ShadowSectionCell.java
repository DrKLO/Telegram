/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

public class ShadowSectionCell extends View {

    private int size;

    private int backgroundColor;
    private Theme.ResourcesProvider resourcesProvider;

    private boolean top = true;
    private boolean bottom = true;

    public ShadowSectionCell(Context context) {
        this(context, 12, null);
    }

    public ShadowSectionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 12, resourcesProvider);
    }

    public ShadowSectionCell(Context context,  int s) {
        this(context, s, null);
    }

    public ShadowSectionCell(Context context, int s, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.size = s;
        updateBackground();
    }

    public ShadowSectionCell(Context context, int s, int backgroundColor) {
        this(context, s, backgroundColor, null);
    }

    public ShadowSectionCell(Context context, int s, int backgroundColor, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.backgroundColor = backgroundColor;
        this.size = s;
        updateBackground();
    }

    public void setTopBottom(boolean top, boolean bottom) {
        if (this.top != top || this.bottom != bottom) {
            this.top = top;
            this.bottom = bottom;
            updateBackground();
        }
    }

    private void updateBackground() {
        if (backgroundColor == 0) {
            if (!top && !bottom) {
                setBackground(null);
            } else {
                setBackground(Theme.getThemedDrawable(getContext(), getBackgroundResId(), Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider)));
            }
        } else {
            if (!top && !bottom) {
                setBackgroundColor(backgroundColor);
            } else {
                Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), getBackgroundResId(), Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                Drawable background = new ColorDrawable(backgroundColor);
                CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                combinedDrawable.setFullsize(true);
                setBackground(combinedDrawable);
            }
        }
    }

    private int getBackgroundResId() {
        if (top && bottom) {
            return R.drawable.greydivider;
        } else if (top) {
            return R.drawable.greydivider_bottom;
        } else if (bottom) {
            return R.drawable.greydivider_top;
        } else {
            return R.drawable.transparent;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(size), MeasureSpec.EXACTLY));
    }
}
