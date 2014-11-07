/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

public class DrawerDividerCell extends FrameLayout {

    public DrawerDividerCell(Context context) {
        super(context);
        setBackgroundColor(0xffd9d9d9);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY));
    }
}
