/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
    private Theme.ResourcesProvider resourcesProvider;

    public PickerBottomLayout(Context context) {
        this(context, true, null);
    }

    public PickerBottomLayout(Context context, boolean darkTheme) {
        this(context, darkTheme, null);
    }

    public PickerBottomLayout(Context context, boolean darkTheme, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(Theme.getColor(darkTheme ? Theme.key_dialogBackground : Theme.key_windowBackgroundWhite, resourcesProvider));

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider) & 0x0fffffff, 0));
        cancelButton.setPadding(AndroidUtilities.dp(33), 0, AndroidUtilities.dp(33), 0);
        cancelButton.setText(LocaleController.getString(R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.bold());
        addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneButton = new LinearLayout(context);
        doneButton.setOrientation(LinearLayout.HORIZONTAL);
        doneButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider) & 0x0fffffff, 0));
        doneButton.setPadding(AndroidUtilities.dp(33), 0, AndroidUtilities.dp(33), 0);
        addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.bold());
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(Theme.getColor(Theme.key_picker_badgeText, resourcesProvider));
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        Drawable drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(11), Theme.getColor(Theme.key_picker_badge, resourcesProvider));
        doneButtonBadgeTextView.setBackgroundDrawable(drawable);
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        doneButton.addView(doneButtonBadgeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setTextColor(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider));
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setText(LocaleController.getString(R.string.Send).toUpperCase());
        doneButtonTextView.setTypeface(AndroidUtilities.bold());
        doneButton.addView(doneButtonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
    }

    public void updateSelectedCount(int count, boolean disable) {
        if (count == 0) {
            doneButtonBadgeTextView.setVisibility(View.GONE);

            if (disable) {
                doneButtonTextView.setTag(Theme.key_picker_disabledButton);
                doneButtonTextView.setTextColor(Theme.getColor(Theme.key_picker_disabledButton, resourcesProvider));
                doneButton.setEnabled(false);
            } else {
                doneButtonTextView.setTag(Theme.key_picker_enabledButton);
                doneButtonTextView.setTextColor(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider));
            }
        } else {
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", count));

            doneButtonTextView.setTag(Theme.key_picker_enabledButton);
            doneButtonTextView.setTextColor(Theme.getColor(Theme.key_picker_enabledButton, resourcesProvider));
            if (disable) {
                doneButton.setEnabled(true);
            }
        }
    }
}
