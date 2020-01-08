/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class WalletDateCell extends FrameLayout {

    private TextView dateTextView;

    public WalletDateCell(Context context) {
        super(context);

        dateTextView = new TextView(context);
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        dateTextView.setTextColor(Theme.getColor(Theme.key_wallet_blackText));
        dateTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 18, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(AndroidUtilities.dp(38)), MeasureSpec.EXACTLY));
    }

    public void setDate(long date) {
        dateTextView.setText(LocaleController.formatDateChat(date));
    }

    public void setText(String text) {
        dateTextView.setText(text);
    }
}
