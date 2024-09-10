/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class PickerBottomLayoutViewer extends FrameLayout {

    public TextView cancelButton;
    public TextView doneButton;
    public TextView doneButtonBadgeTextView;

    private boolean isDarkTheme;

    public PickerBottomLayoutViewer(Context context) {
        this(context, true);
    }

    public PickerBottomLayoutViewer(Context context, boolean darkTheme) {
        super(context);
        isDarkTheme = darkTheme;

        setBackgroundColor(isDarkTheme ? 0xff1a1a1a : 0xffffffff);

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(isDarkTheme ? Theme.ACTION_BAR_PICKER_SELECTOR_COLOR : Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        cancelButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        cancelButton.setText(LocaleController.getString(R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.bold());
        addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneButton = new TextView(context);
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButton.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(isDarkTheme ? Theme.ACTION_BAR_PICKER_SELECTOR_COLOR : Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        doneButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        doneButton.setText(LocaleController.getString(R.string.Send).toUpperCase());
        doneButton.setTypeface(AndroidUtilities.bold());
        addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.bold());
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(0xffffffff);
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        doneButtonBadgeTextView.setBackgroundResource(isDarkTheme ? R.drawable.photobadge : R.drawable.bluecounter);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        addView(doneButtonBadgeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.TOP | Gravity.RIGHT, 0, 0, 7, 0));
    }

    public void updateSelectedCount(int count, boolean disable) {
        if (count == 0) {
            doneButtonBadgeTextView.setVisibility(View.GONE);

            if (disable) {
                doneButton.setTextColor(0xff999999);
                doneButton.setEnabled(false);
            } else {
                doneButton.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
            }
        } else {
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", count));

            doneButton.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
            if (disable) {
                doneButton.setEnabled(true);
            }
        }
    }
}
