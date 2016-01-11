/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class ShadowBottomSectionCell extends View {

    public ShadowBottomSectionCell(Context context) {
        super(context);
        setBackgroundResource(R.drawable.greydivider_bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(6), MeasureSpec.EXACTLY));
    }
}
