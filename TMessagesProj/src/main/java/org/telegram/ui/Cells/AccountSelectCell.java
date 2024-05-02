/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */


package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class AccountSelectCell extends FrameLayout {

    private TextView textView;
    private TextView infoTextView;
    private BackupImageView imageView;
    private ImageView checkImageView;
    private AvatarDrawable avatarDrawable;

    private int accountNumber;

    public AccountSelectCell(Context context, boolean hasInfo) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(18));
        addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 10, 10, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);

        if (hasInfo) {
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 61, 7, 8, 0));
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
            textView.setText(LocaleController.getString("VoipGroupDisplayAs", R.string.VoipGroupDisplayAs));

            infoTextView = new TextView(context);
            infoTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            infoTextView.setLines(1);
            infoTextView.setMaxLines(1);
            infoTextView.setSingleLine(true);
            infoTextView.setMaxWidth(AndroidUtilities.dp(320));
            infoTextView.setGravity(Gravity.LEFT | Gravity.TOP);
            infoTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 61, 27, 8, 0));
        } else {
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 61, 0, 56, 0));
            textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

            checkImageView = new ImageView(context);
            checkImageView.setImageResource(R.drawable.account_check);
            checkImageView.setScaleType(ImageView.ScaleType.CENTER);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemCheck), PorterDuff.Mode.MULTIPLY));
            addView(checkImageView, LayoutHelper.createFrame(40, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 6, 0));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkImageView != null || infoTextView != null && getLayoutParams().width != LayoutHelper.WRAP_CONTENT) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (infoTextView == null) {
            textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        }
    }

    public void setObject(TLObject object) {
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            avatarDrawable.setInfo(user);
            infoTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            imageView.setForUserOrChat(user, avatarDrawable);
        } else {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            avatarDrawable.setInfo(chat);
            infoTextView.setText(chat == null ? "" : chat.title);
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
    }

    public void setAccount(int account, boolean check) {
        accountNumber = account;
        TLRPC.User user = UserConfig.getInstance(accountNumber).getCurrentUser();
        avatarDrawable.setInfo(account, user);
        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        imageView.getImageReceiver().setCurrentAccount(account);
        imageView.setForUserOrChat(user, avatarDrawable);
        checkImageView.setVisibility(check && account == UserConfig.selectedAccount ? VISIBLE : INVISIBLE);
    }

    public int getAccountNumber() {
        return accountNumber;
    }
}
