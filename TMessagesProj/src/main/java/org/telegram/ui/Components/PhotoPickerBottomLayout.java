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

public class PhotoPickerBottomLayout extends FrameLayout {

    public LinearLayout doneButton;
    public TextView cancelButton;
    public TextView doneButtonTextView;
    public TextView doneButtonBadgeTextView;

    public PhotoPickerBottomLayout(Context context) {
        super(context);
        setBackgroundColor(0xff1a1a1a);

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(0xffffffff);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundResource(R.drawable.bar_selector_picker);
        cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(cancelButton);
        LayoutParams layoutParams = (LayoutParams) cancelButton.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        cancelButton.setLayoutParams(layoutParams);

        doneButton = new LinearLayout(context);
        doneButton.setOrientation(LinearLayout.HORIZONTAL);
        doneButton.setBackgroundResource(R.drawable.bar_selector_picker);
        doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        addView(doneButton);
        layoutParams = (LayoutParams) doneButton.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
        doneButton.setLayoutParams(layoutParams);

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(0xffffffff);
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        doneButtonBadgeTextView.setBackgroundResource(R.drawable.photobadge);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        doneButton.addView(doneButtonBadgeTextView);
        LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) doneButtonBadgeTextView.getLayoutParams();
        layoutParams1.width = LayoutParams.WRAP_CONTENT;
        layoutParams1.height = AndroidUtilities.dp(23);
        layoutParams1.rightMargin = AndroidUtilities.dp(10);
        layoutParams1.gravity = Gravity.CENTER_VERTICAL;
        doneButtonBadgeTextView.setLayoutParams(layoutParams1);

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setTextColor(0xffffffff);
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButton.addView(doneButtonTextView);
        layoutParams1 = (LinearLayout.LayoutParams) doneButtonTextView.getLayoutParams();
        layoutParams1.width = LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.CENTER_VERTICAL;
        layoutParams1.height = LayoutParams.WRAP_CONTENT;
        doneButtonTextView.setLayoutParams(layoutParams1);
    }

    public void updateSelectedCount(int count, boolean disable) {
        if (count == 0) {
            doneButtonBadgeTextView.setVisibility(View.GONE);

            if (disable) {
                doneButtonTextView.setTextColor(0xff999999);
                doneButton.setEnabled(false);
            } else {
                doneButtonTextView.setTextColor(0xffffffff);
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
