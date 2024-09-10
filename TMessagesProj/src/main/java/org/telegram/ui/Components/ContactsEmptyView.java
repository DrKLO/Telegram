/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ContactsEmptyView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private TextView titleTextView;
    private BackupImageView stickerView;
    private ArrayList<TextView> textViews = new ArrayList<>();
    private ArrayList<ImageView> imageViews = new ArrayList<>();
    private LoadingStickerDrawable drawable;

    private int currentAccount = UserConfig.selectedAccount;

    private static final String stickerSetName = AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME;

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
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setText(LocaleController.getString(R.string.NoContactsYet));
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setMaxWidth(AndroidUtilities.dp(260));
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 14));

        LinearLayout linesContainer = new LinearLayout(context);
        linesContainer.setOrientation(VERTICAL);
        addView(linesContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        for (int a = 0; a < 3; a++) {
            if (a == 1) continue;
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linesContainer.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

            ImageView imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
            imageView.setImageResource(R.drawable.list_circle);
            imageViews.add(imageView);

            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textView.setMaxWidth(AndroidUtilities.dp(260));
            textViews.add(textView);
            textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            switch (a) {
                case 0:
                    textView.setText(LocaleController.getString(R.string.NoContactsYetLine1));
                    break;
                case 2:
                    textView.setText(LocaleController.getString(R.string.NoContactsYetLine3));
                    break;
            }
            if (LocaleController.isRTL) {
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 7, 0, 0));
            } else {
                linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 8, 0));
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
        }
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
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerSetName);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerSetName);
        }
        if (set != null && set.documents.size() >= 1) {
            TLRPC.Document document = set.documents.get(0);
            ImageLocation imageLocation = ImageLocation.getForDocument(document);
            stickerView.setImage(imageLocation, "130_130", "tgs", drawable, set);
        } else {
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerSetName, false, true);
            stickerView.setImageDrawable(drawable);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSticker();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (stickerSetName.equals(name)) {
                setSticker();
            }
        }
    }
}
