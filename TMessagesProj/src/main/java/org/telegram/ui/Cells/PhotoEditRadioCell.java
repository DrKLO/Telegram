/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

public class PhotoEditRadioCell extends FrameLayout {

    private TextView nameTextView;
    private int currentType;
    private LinearLayout tintButtonsContainer;
    private OnClickListener onClickListener;
    private int currentColor;

    private final int[] tintShadowColors = new int[] {
            0x00000000,
            0xffff4d4d,
            0xfff48022,
            0xffffcd00,
            0xff81d281,
            0xff71c5d6,
            0xff0072bc,
            0xff662d91
    };

    private final int[] tintHighlighsColors = new int[] {
            0x00000000,
            0xffef9286,
            0xffeacea2,
            0xfff2e17c,
            0xffa4edae,
            0xff89dce5,
            0xff2e8bc8,
            0xffcd98e5
    };

    public PhotoEditRadioCell(Context context) {
        super(context);

        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.RIGHT);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(80, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        tintButtonsContainer = new LinearLayout(context);
        tintButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        for (int a = 0; a < tintShadowColors.length; a++) {
            RadioButton radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setTag(a);
            tintButtonsContainer.addView(radioButton, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f / tintShadowColors.length));
            radioButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    RadioButton radioButton = (RadioButton) v;
                    if (currentType == 0) {
                        currentColor = tintShadowColors[(Integer) radioButton.getTag()];
                    } else {
                        currentColor = tintHighlighsColors[(Integer) radioButton.getTag()];
                    }
                    updateSelectedTintButton(true);
                    onClickListener.onClick(PhotoEditRadioCell.this);
                }
            });
        }
        addView(tintButtonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 96, 0, 24, 0));
    }

    public int getCurrentColor() {
        return currentColor;
    }

    private void updateSelectedTintButton(boolean animated) {
        int childCount = tintButtonsContainer.getChildCount();
        for (int a = 0; a < childCount; a++) {
            View child = tintButtonsContainer.getChildAt(a);
            if (child instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) child;
                int num = (Integer) radioButton.getTag();
                int color2 = currentType == 0 ? tintShadowColors[num] : tintHighlighsColors[num];
                radioButton.setChecked(currentColor == color2, animated);
                radioButton.setColor(num == 0 ? 0xffffffff : (currentType == 0 ? tintShadowColors[num] : tintHighlighsColors[num]), num == 0 ? 0xffffffff : (currentType == 0 ? tintShadowColors[num] : tintHighlighsColors[num]));
            }
        }
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        onClickListener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
    }

    public void setIconAndTextAndValue(String text, int type, int value) {
        currentType = type;
        currentColor = value;
        nameTextView.setText(text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase());
        updateSelectedTintButton(false);
    }
}
