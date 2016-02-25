/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class LetterSectionCell extends FrameLayout {

    private TextView textView;

    public LetterSectionCell(Context context) {
        super(context);
        setLayoutParams(new ViewGroup.LayoutParams(AndroidUtilities.dp(54), AndroidUtilities.dp(64)));

        textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextColor(0xff808080);
        textView.setGravity(Gravity.CENTER);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setLetter(String letter) {
        textView.setText(letter.toUpperCase());
    }

    public void setCellHeight(int height) {
        setLayoutParams(new ViewGroup.LayoutParams(AndroidUtilities.dp(54), height));
    }

    public void setLetterColor(int color) {
        textView.setTextColor(color);
    }

}
