/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
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
        layoutParams.bottomMargin = AndroidUtilities.dp(20);
        iconImage.setLayoutParams(layoutParams);

        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        addView(nameTextView);
        layoutParams = (LayoutParams) nameTextView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(20);
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        nameTextView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
    }

    public void setIconAndText(int resId, String text) {
        iconImage.setImageResource(resId);
        nameTextView.setText(text);
    }
}
