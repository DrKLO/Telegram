/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;

public class TextSettingsCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private ImageView valueImageView;
    private static Paint paint;
    private boolean needDivider;

    public TextSettingsCell(Context context) {
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
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.leftMargin = AndroidUtilities.dp(17);
        layoutParams.rightMargin = AndroidUtilities.dp(17);
        layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
        textView.setLayoutParams(layoutParams);

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff2f8cc9);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueTextView);
        layoutParams = (LayoutParams) valueTextView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.leftMargin = AndroidUtilities.dp(17);
        layoutParams.rightMargin = AndroidUtilities.dp(17);
        layoutParams.gravity = LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT;
        valueTextView.setLayoutParams(layoutParams);

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        valueImageView.setVisibility(INVISIBLE);
        addView(valueImageView);
        layoutParams = (LayoutParams) valueImageView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 17 : 0);
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 17);
        layoutParams.gravity = (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        valueImageView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(48) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        int width = availableWidth / 2;
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);
        } else {
            width = availableWidth;
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(String text, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(String text, String value, boolean divider) {
        textView.setText(text);
        valueImageView.setVisibility(INVISIBLE);
        if (value != null) {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndIcon(String text, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        if (resId != 0) {
            valueImageView.setVisibility(VISIBLE);
            valueImageView.setImageResource(resId);
        } else {
            valueImageView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
        }
    }
}
