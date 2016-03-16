/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class TextCheckCell extends FrameLayoutFixed {

    private TextView textView;
    private TextView valueTextView;
    private Switch checkBox;
    private static Paint paint;
    private boolean needDivider;

    public TextCheckCell(Context context) {
        super(context);

        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        textView = new TextView(context);
        textView.setTextColor(0xff212121);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 0, 17, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff8a8a8a);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 35, 17, 0));

        checkBox = new Switch(context);
        checkBox.setDuplicateParentStateEnabled(false);
        checkBox.setFocusable(false);
        checkBox.setFocusableInTouchMode(false);
        checkBox.setClickable(false);
        addView(checkBox, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 14, 0, 14, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(valueTextView.getVisibility() == VISIBLE ? 64 : 48) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setTextAndCheck(String text, boolean checked, boolean divider) {
        textView.setText(text);
        if (Build.VERSION.SDK_INT < 11) {
            checkBox.resetLayout();
            checkBox.requestLayout();
        }
        checkBox.setChecked(checked);
        needDivider = divider;
        valueTextView.setVisibility(GONE);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.topMargin = 0;
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setTextAndValueAndCheck(String text, String value, boolean checked, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        if (Build.VERSION.SDK_INT < 11) {
            checkBox.resetLayout();
            checkBox.requestLayout();
        }
        checkBox.setChecked(checked);
        needDivider = divider;
        valueTextView.setVisibility(VISIBLE);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.topMargin = AndroidUtilities.dp(10);
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
        }
    }
}
