/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ContactsEmptyView extends LinearLayout {

    private TextView titleTextView;
    private TextView subtitleTextView;
    private LinkSpanDrawable.LinksTextView buttonTextView;

    private BackupImageView stickerView;
    private ArrayList<TextView> textViews = new ArrayList<>();
    private ArrayList<ImageView> imageViews = new ArrayList<>();
    private LoadingStickerDrawable drawable;

    private int currentAccount = UserConfig.selectedAccount;

    public static final String svg = "m418 282.6c13.4-21.1 20.2-44.9 20.2-70.8 0-88.3-79.8-175.3-178.9-175.3-100.1 0-178.9 88-178.9 175.3 0 46.6 16.9 73.1 29.1 86.1-19.3 23.4-30.9 52.3-34.6 86.1-2.5 22.7 3.2 41.4 17.4 57.3 14.3 16 51.7 35 148.1 35 41.2 0 119.9-5.3 156.7-18.3 49.5-17.4 59.2-41.1 59.2-76.2 0-41.5-12.9-74.8-38.3-99.2z";
    public ContactsEmptyView(Context context) {
        super(context);

        setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        setOrientation(LinearLayout.VERTICAL);

        stickerView = new BackupImageView(context);
        drawable = new LoadingStickerDrawable(stickerView, svg, AndroidUtilities.dp(130), AndroidUtilities.dp(130));
        stickerView.setImageDrawable(drawable);
        if (!AndroidUtilities.isTablet()) {
            addView(stickerView, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setText(LocaleController.getString(R.string.NoContactsYet2));
        titleTextView.setTypeface(AndroidUtilities.bold());
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 9));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleTextView.setText(LocaleController.getString(R.string.NoContactsYet2Sub));
        subtitleTextView.setMaxWidth(AndroidUtilities.dp(260));
        addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 14));

        buttonTextView = new LinkSpanDrawable.LinksTextView(context);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        buttonTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        buttonTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonTextView.setPadding(dp(8), dp(2), dp(2), dp(8));
        buttonTextView.setDisablePaddingsOffsetY(true);
        buttonTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(LocaleController.getString(R.string.NoContactsYet2Invite), () -> {
            onInviteClick();
        }), true, dp(8f / 3f), dp(1)));
        buttonTextView.setMaxWidth(AndroidUtilities.dp(260));
        addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 14));
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
        for (int a = 0; a < textViews.size(); a++) {
            textViews.get(a).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        for (int a = 0; a < imageViews.size(); a++) {
            imageViews.get(a).setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
        }
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
    }

    private void setSticker() {
        stickerView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(130), dp(130)));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSticker();
    }
}
