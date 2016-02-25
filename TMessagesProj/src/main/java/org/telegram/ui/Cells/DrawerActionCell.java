/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerActionCell extends FrameLayout {

    private TextView textView;

    public DrawerActionCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(0xff444444);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(34));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 14, 0, 16, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        updateTheme();
    }

    public void setTextAndIcon(String text, int resId) {
        try {
            textView.setText(text);
            //textView.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0);
            int color = AndroidUtilities.getIntDef("drawerIconColor", 0xff737373);
            Drawable d = getResources().getDrawable(resId);
            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            textView.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public void setTextAndIcon(String text, Drawable drawable) {
        textView.setText(text);
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
    }

    private void updateTheme(){
        //Log.e("DrawerActionCell","updateTheme");
        textView.setTextColor(AndroidUtilities.getIntDef("drawerOptionColor", 0xff444444));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.getIntDef("drawerOptionSize", 15));
        //Drawable[] drawables = textView.getCompoundDrawables();
        //if(drawables[0].getConstantState().equals(getResources().getDrawable(R.drawable.menu_themes).getConstantState())){
        //    return;
        //}
        //int color = AndroidUtilities.getIntDef("drawerIconColor", 0xff737373);
        //if(drawables[0] != null)drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }
}
