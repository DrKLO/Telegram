/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;

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
        addView(textView);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.LEFT;
        layoutParams.leftMargin = AndroidUtilities.dp(14);
        layoutParams.rightMargin = AndroidUtilities.dp(16);
        textView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        updateTheme();
    }

    public void setTextAndIcon(String text, int resId) {
        textView.setText(text);
        //textView.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0);
        textView.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(resId), null, null, null);
    }

    private void updateTheme(){
        textView.setTextColor(AndroidUtilities.getIntDef("drawerOptionColor", 0xff444444));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.getIntDef("drawerOptionSize", 15));
        int color = AndroidUtilities.getIntDef("drawerIconColor", 0xff737373);
        Drawable icon = getResources().getDrawable(R.drawable.menu_newgroup_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_secret_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_broadcast_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_contacts_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_theming_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_settings_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon = getResources().getDrawable(R.drawable.menu_forum_white);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }
}
