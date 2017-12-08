/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class PickerBottomLayout extends FrameLayout {

    public LinearLayout doneButton;
    public TextView cancelButton;
    public TextView doneButtonTextView;
    public TextView doneButtonBadgeTextView;

    private boolean isDarkTheme;

    public PickerBottomLayout(Context context) {
        this(context, true);
    }

    public PickerBottomLayout(Context context, boolean darkTheme) {
        super(context);
        isDarkTheme = darkTheme;

        setBackgroundColor(isDarkTheme ? 0xff1a1a1a : Theme.getColor(Theme.key_windowBackgroundWhite));

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(isDarkTheme ? 0xffffffff : Theme.getColor(Theme.key_picker_enabledButton));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(isDarkTheme ? Theme.ACTION_BAR_PICKER_SELECTOR_COLOR : Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneButton = new LinearLayout(context);
        doneButton.setOrientation(LinearLayout.HORIZONTAL);
        doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(isDarkTheme ? Theme.ACTION_BAR_PICKER_SELECTOR_COLOR : Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(isDarkTheme ? 0xffffffff : Theme.getColor(Theme.key_picker_badgeText));
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        Drawable drawable;
        if (isDarkTheme) {
            drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(11), 0xff66bffa);
        } else {
            drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(11), Theme.getColor(Theme.key_picker_badge));
        }
        doneButtonBadgeTextView.setBackgroundDrawable(drawable);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        doneButton.addView(doneButtonBadgeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : Theme.getColor(Theme.key_picker_enabledButton));
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButton.addView(doneButtonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
    }

    public void updateSelectedCount(int count, boolean disable) {
        if (count == 0) {
            doneButtonBadgeTextView.setVisibility(View.GONE);

            if (disable) {
                doneButtonTextView.setTag(isDarkTheme ? null : Theme.key_picker_disabledButton);
                doneButtonTextView.setTextColor(isDarkTheme ? 0xff999999 : Theme.getColor(Theme.key_picker_disabledButton));
                doneButton.setEnabled(false);
            } else {
                doneButtonTextView.setTag(isDarkTheme ? null : Theme.key_picker_enabledButton);
                doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : Theme.getColor(Theme.key_picker_enabledButton));
            }
        } else {
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", count));

            doneButtonTextView.setTag(isDarkTheme ? null : Theme.key_picker_enabledButton);
            doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : Theme.getColor(Theme.key_picker_enabledButton));
            if (disable) {
                doneButton.setEnabled(true);
            }
        }
    }
}
