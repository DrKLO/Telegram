/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

public class DrawerProfileCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView phoneTextView;

    public DrawerProfileCell(Context context) {
        super(context);
        setBackgroundColor(0xff4c84b5);

        avatarImageView = new BackupImageView(context);
        avatarImageView.imageReceiver.setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView);
        LayoutParams layoutParams = (LayoutParams) avatarImageView.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(64);
        layoutParams.height = AndroidUtilities.dp(64);
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        layoutParams.leftMargin = AndroidUtilities.dp(16);
        layoutParams.bottomMargin = AndroidUtilities.dp(67);
        avatarImageView.setLayoutParams(layoutParams);

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.LEFT);
        addView(nameTextView);
        layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        layoutParams.leftMargin = AndroidUtilities.dp(16);
        layoutParams.bottomMargin = AndroidUtilities.dp(28);
        layoutParams.rightMargin = AndroidUtilities.dp(16);
        nameTextView.setLayoutParams(layoutParams);

        phoneTextView = new TextView(context);
        phoneTextView.setTextColor(0xffc2e5ff);
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneTextView.setLines(1);
        phoneTextView.setMaxLines(1);
        phoneTextView.setSingleLine(true);
        phoneTextView.setGravity(Gravity.LEFT);
        addView(phoneTextView);
        layoutParams = (FrameLayout.LayoutParams) phoneTextView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        layoutParams.leftMargin = AndroidUtilities.dp(16);
        layoutParams.bottomMargin = AndroidUtilities.dp(9);
        layoutParams.rightMargin = AndroidUtilities.dp(16);
        phoneTextView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Build.VERSION.SDK_INT >= 21) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148) + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148), MeasureSpec.EXACTLY));
        }
    }

    public void setUser(TLRPC.User user) {
        if (user == null) {
            return;
        }
        TLRPC.FileLocation photo = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
        }
        nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
        phoneTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(0xff5c98cd);
        avatarImageView.setImage(photo, "50_50", avatarDrawable);
    }
}
