/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

public class TextInfoCell extends FrameLayout {

    private TextView textView;

    public TextInfoCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(0xffa3a3a3);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, AndroidUtilities.dp(19), 0, AndroidUtilities.dp(19));
        addView(textView);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.width = LayoutHelper.WRAP_CONTENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.leftMargin = AndroidUtilities.dp(17);
        layoutParams.rightMargin = AndroidUtilities.dp(17);
        layoutParams.gravity = Gravity.CENTER;
        textView.setLayoutParams(layoutParams);
        //setDarkTheme();
    }

    private void setDarkTheme(){
        textView.setTextColor(0xff5c5c5c);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if( textView.getContext() instanceof LaunchActivity ){
            textView.setTextColor(AndroidUtilities.getIntDef("drawerVersionColor", 0xffa3a3a3));
            textView.setTextSize(AndroidUtilities.getIntDef("drawerVersionSize", 13));
        }
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextSize(int size) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP,size);
    }
}
