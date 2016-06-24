/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoEditToolCell extends FrameLayout {

    private ImageView iconImage;
    private TextView nameTextView;
    private TextView valueTextView;

    public PhotoEditToolCell(Context context) {
        super(context);

        iconImage = new ImageView(context);
        iconImage.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 12));

        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 4, 0, 4, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff6cc3ff);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        valueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 57, 3, 0, 0));
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
