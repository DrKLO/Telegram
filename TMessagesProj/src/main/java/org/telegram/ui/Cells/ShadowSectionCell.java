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
        setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider)));
        size = s;
    }

    public ShadowSectionCell(Context context, int s, int backgroundColor) {
        this(context, s, backgroundColor, null);
    }

    public ShadowSectionCell(Context context, int s, int backgroundColor, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
        Drawable background = new ColorDrawable(backgroundColor);
        CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        setBackgroundDrawable(combinedDrawable);
        size = s;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(size), MeasureSpec.EXACTLY));
    }
}
