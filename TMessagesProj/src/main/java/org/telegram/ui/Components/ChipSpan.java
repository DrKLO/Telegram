/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import org.telegram.messenger.AndroidUtilities;

public class ChipSpan extends ImageSpan {

    public int uid;

    public ChipSpan(Drawable d, int verticalAlignment) {
        super(d, verticalAlignment);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm == null) {
            fm = new Paint.FontMetricsInt();
        }

        int sz = super.getSize(paint, text, start, end, fm);
        int offset = AndroidUtilities.dp(6);
        int w = (fm.bottom - fm.top) / 2;
        fm.top = -w - offset;
        fm.bottom = w - offset;
        fm.ascent = -w - offset;
        fm.leading = 0;
        fm.descent = w - offset;
        return sz;
    }
}
