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
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class AccountSelectCell extends FrameLayout {

    private TextView textView;
    private BackupImageView imageView;
    private ImageView checkImageView;
    private AvatarDrawable avatarDrawable;

    private int accountNumber;

    public AccountSelectCell(Context context) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(18));
        addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 10, 10, 0, 0));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 61, 0, 56, 0));

        checkImageView = new ImageView(context);
        checkImageView.setImageResource(R.drawable.account_check);
        checkImageView.setScaleType(ImageView.ScaleType.CENTER);
        checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemCheck), PorterDuff.Mode.MULTIPLY));
        addView(checkImageView, LayoutHelper.createFrame(40, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 6, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
    }

    public void setAccount(int account, boolean check) {
        accountNumber = account;
        TLRPC.User user = UserConfig.getInstance(accountNumber).getCurrentUser();
        avatarDrawable.setInfo(user);
        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        imageView.getImageReceiver().setCurrentAccount(account);
        imageView.setImage(ImageLocation.getForUser(user,false), "50_50", avatarDrawable, user);
        checkImageView.setVisibility(check && account == UserConfig.selectedAccount ? VISIBLE : INVISIBLE);
    }

    public int getAccountNumber() {
        return accountNumber;
    }
}
