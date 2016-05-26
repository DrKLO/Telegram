/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class ShadowSectionCell extends View {

    private int size = 12;

    boolean bTheme;
    public ShadowSectionCell(Context context) {
        super(context);
        setBackgroundResource(R.drawable.greydivider);
        bTheme = true;
    }

    public void setSize(int value) {
        size = value;



    }

    public ShadowSectionCell(Context context, boolean theme) {
        super(context);
        setBackgroundResource(R.drawable.greydivider);
        bTheme = theme;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(size), MeasureSpec.EXACTLY));
        if(bTheme)setTheme();
    }

    private void setTheme(){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int shadowColor = preferences.getInt("prefShadowColor", 0xfff0f0f0);
        if(shadowColor == 0xfff0f0f0) {
            setBackgroundResource(R.drawable.greydivider);
        } else {
            setBackgroundColor(shadowColor);
        }
    }

}
