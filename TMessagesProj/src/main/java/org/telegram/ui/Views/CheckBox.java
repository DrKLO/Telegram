/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.CompoundButton;

import org.telegram.messenger.R;

public class CheckBox extends CompoundButton {

    private Paint paint;
    private Drawable checkDrawable;

    public CheckBox(Context context) {
        super(context);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xff5ec245);

        checkDrawable = context.getResources().getDrawable(R.drawable.round_check2);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);

        checked = isChecked();
        invalidate();

        /*if (attachedToWindow && wasLayout) {
            animateThumbToCheckedState(checked);
        } else {
            cancelPositionAnimator();
            setThumbPosition(checked ? 1 : 0);
        }*/
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isChecked()) {
            canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, getMeasuredWidth() / 2, paint);
            int x = (getMeasuredWidth() - checkDrawable.getIntrinsicWidth()) / 2;
            int y = (getMeasuredHeight() - checkDrawable.getIntrinsicHeight()) / 2;
            checkDrawable.setBounds(x, y, x + checkDrawable.getIntrinsicWidth(), y + checkDrawable.getIntrinsicHeight());
            checkDrawable.draw(canvas);
        }
    }
}
