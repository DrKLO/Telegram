/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class ContactsEmptyView extends LinearLayout {

    private final TextView titleTextView;
    private final TextView subtitleTextView;

    private final BackupImageView stickerView;
    private final LoadingStickerDrawable drawable;
    private final ButtonWithCounterView button;

    private final int currentAccount = UserConfig.selectedAccount;

    public static final String svg = "m418 282.6c13.4-21.1 20.2-44.9 20.2-70.8 0-88.3-79.8-175.3-178.9-175.3-100.1 0-178.9 88-178.9 175.3 0 46.6 16.9 73.1 29.1 86.1-19.3 23.4-30.9 52.3-34.6 86.1-2.5 22.7 3.2 41.4 17.4 57.3 14.3 16 51.7 35 148.1 35 41.2 0 119.9-5.3 156.7-18.3 49.5-17.4 59.2-41.1 59.2-76.2 0-41.5-12.9-74.8-38.3-99.2z";
    public ContactsEmptyView(Context context) {
        super(context);

        setOrientation(LinearLayout.VERTICAL);

        stickerView = new BackupImageView(context);
        drawable = new LoadingStickerDrawable(stickerView, svg, dp(110), dp(110));
        stickerView.setImageDrawable(drawable);
        if (!AndroidUtilities.isTablet()) {
            addView(stickerView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        }

        titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setText(LocaleController.getString(R.string.NoContactsYet3));
        titleTextView.setTypeface(AndroidUtilities.bold());
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 7));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        subtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleTextView.setText(LocaleController.getString(R.string.NoContactsYet3Sub));
        subtitleTextView.setMaxWidth(dp(260));
        subtitleTextView.setLineSpacing(AndroidUtilities.dp(2), 1);
        addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 19));

        button = new ButtonWithCounterView(context, null);
        button.setUseWrapContent(true);
        button.setRound();
        button.setPadding(dp(28), 0, dp(28), 0);
        SpannableStringBuilder ssb = new SpannableStringBuilder("c");
        ssb.setSpan(new ColoredImageSpan(R.drawable.filled_new_contact_24), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append("  ").append(getString(R.string.NewContact));
        button.setText(ssb, false);
        addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    }

    protected void onInviteClick() {
        Activity activity = AndroidUtilities.findActivity(getContext());
        if (activity == null || activity.isFinishing()) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String text = ContactsController.getInstance(currentAccount).getInviteText(0);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivityForResult(Intent.createChooser(intent, text), 500);
    }

    public void setColors() {
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        button.updateColors();
    }

    private void setSticker() {
        stickerView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(110), dp(110)));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSticker();
    }
}
