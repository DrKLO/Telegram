/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.ui.Components.FrameLayoutFixed;

public class PhotoEditToolCell extends FrameLayoutFixed {

    private ImageView iconImage;
    private TextView nameTextView;
    private TextView valueTextView;

    public PhotoEditToolCell(Context context) {
        super(context);

        iconImage = new ImageView(context);
        iconImage.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconImage);
        LayoutParams layoutParams = (LayoutParams) iconImage.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.bottomMargin = AndroidUtilities.dp(12);
        iconImage.setLayoutParams(layoutParams);

        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView);
        layoutParams = (LayoutParams) nameTextView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        layoutParams.leftMargin = AndroidUtilities.dp(4);
        layoutParams.rightMargin = AndroidUtilities.dp(4);
        nameTextView.setLayoutParams(layoutParams);

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff6cc3ff);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        valueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(valueTextView);
        layoutParams = (LayoutParams) valueTextView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.leftMargin = AndroidUtilities.dp(57);
        layoutParams.topMargin = AndroidUtilities.dp(3);
        valueTextView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
    }

    public void setIconAndTextAndValue(int resId, String text, float value) {
        iconImage.setImageResource(resId);
        nameTextView.setText(text.toUpperCase());
        if (value == 0) {
            valueTextView.setText("");
        } else if (value > 0) {
            valueTextView.setText("+" + (int) value);
        } else {
            valueTextView.setText("" + (int) value);
        }
    }

    public void setIconAndTextAndValue(int resId, String text, String value) {
        iconImage.setImageResource(resId);
        nameTextView.setText(text.toUpperCase());
        valueTextView.setText(value);
    }
}
