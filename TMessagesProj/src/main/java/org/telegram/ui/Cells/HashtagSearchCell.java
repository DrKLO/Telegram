/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;

public class HashtagSearchCell extends TextView {

    private boolean needDivider;
    private static Paint paint;

    public HashtagSearchCell(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        setTextColor(0xff000000);
        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffdcdcdc);
        }
    }

    public void setNeedDivider(boolean value) {
        needDivider = value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(48) + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);
        }
    }
}
