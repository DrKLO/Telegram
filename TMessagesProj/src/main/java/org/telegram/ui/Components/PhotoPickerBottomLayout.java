/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;

public class PhotoPickerBottomLayout extends LinearLayout {

    public FrameLayout doneButton;
    public TextView cancelButton;
    public TextView doneButtonTextView;
    public TextView doneButtonBadgeTextView;

    public PhotoPickerBottomLayout(Context context) {
        super(context);
        setBackgroundColor(0xff333333);
        setOrientation(HORIZONTAL);

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(0xffffffff);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundResource(R.drawable.bar_selector_picker);
        cancelButton.setPadding(AndroidUtilities.dp(3), 0, 0, 0);
        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(cancelButton);
        LayoutParams layoutParams = (LayoutParams) cancelButton.getLayoutParams();
        layoutParams.width = 0;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.weight = 1;
        cancelButton.setLayoutParams(layoutParams);

        doneButton = new FrameLayout(context);
        doneButton.setBackgroundResource(R.drawable.bar_selector_picker);
        doneButton.setPadding(0, 0, AndroidUtilities.dp(3), 0);
        addView(doneButton);
        layoutParams = (LayoutParams) doneButton.getLayoutParams();
        layoutParams.width = 0;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.weight = 1;
        doneButton.setLayoutParams(layoutParams);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);
        doneButton.addView(linearLayout);
        FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) linearLayout.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.CENTER;
        linearLayout.setLayoutParams(layoutParams1);

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(0xffffffff);
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        doneButtonBadgeTextView.setBackgroundResource(R.drawable.photobadge);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        linearLayout.addView(doneButtonBadgeTextView);
        layoutParams = (LayoutParams) doneButtonBadgeTextView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = AndroidUtilities.dp(23);
        layoutParams.rightMargin = AndroidUtilities.dp(10);
        doneButtonBadgeTextView.setLayoutParams(layoutParams);

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setTextColor(0xffffffff);
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setBackgroundResource(R.drawable.bar_selector_picker);
        doneButtonTextView.setPadding(AndroidUtilities.dp(3), 0, 0, 0);
        doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(doneButtonTextView);
        layoutParams = (LayoutParams) doneButtonTextView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        doneButtonTextView.setLayoutParams(layoutParams);
    }

    public void updateSelectedCount(int count, boolean disable) {
        if (count == 0) {
            doneButtonBadgeTextView.setVisibility(View.GONE);

            if (disable) {
                doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.selectphoto_small_grey, 0, 0, 0);
                doneButtonTextView.setTextColor(0xff999999);
                doneButton.setEnabled(false);
            } else {
                doneButtonTextView.setTextColor(0xffffffff);
                doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.selectphoto_small_active, 0, 0, 0);
            }
        } else {
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", count));

            if (disable) {
                doneButtonTextView.setTextColor(0xffffffff);
                doneButton.setEnabled(true);
            } else {
                doneButtonTextView.setTextColor(0xffffffff);
            }
        }
    }
}
