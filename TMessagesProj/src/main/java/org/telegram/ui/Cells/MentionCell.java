/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

public class MentionCell extends LinearLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private TextView usernameTextView;
    private AvatarDrawable avatarDrawable;

    public MentionCell(Context context) {
        super(context);

        setOrientation(HORIZONTAL);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setSmallStyle(true);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(14));
        addView(imageView);
        LayoutParams layoutParams = (LayoutParams) imageView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(12);
        layoutParams.topMargin = AndroidUtilities.dp(4);
        layoutParams.width = AndroidUtilities.dp(28);
        layoutParams.height = AndroidUtilities.dp(28);
        imageView.setLayoutParams(layoutParams);

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff000000);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView);
        layoutParams = (LayoutParams) nameTextView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(12);
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        nameTextView.setLayoutParams(layoutParams);

        usernameTextView = new TextView(context);
        usernameTextView.setTextColor(0xff999999);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        usernameTextView.setSingleLine(true);
        usernameTextView.setGravity(Gravity.LEFT);
        usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(usernameTextView);
        layoutParams = (LayoutParams) usernameTextView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(12);
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        usernameTextView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
    }

    public void setUser(TLRPC.User user) {
        if (user == null) {
            nameTextView.setText("");
            usernameTextView.setText("");
            imageView.setImageDrawable(null);
            return;
        }
        avatarDrawable.setInfo(user);
        if (user.photo != null && user.photo.photo_small != null) {
            imageView.setImage(user.photo.photo_small, "50_50", avatarDrawable);
        } else {
            imageView.setImageDrawable(avatarDrawable);
        }
        nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
        usernameTextView.setText("@" + user.username);
        imageView.setVisibility(VISIBLE);
        usernameTextView.setVisibility(VISIBLE);
    }

    public void setText(String text) {
        imageView.setVisibility(INVISIBLE);
        usernameTextView.setVisibility(INVISIBLE);
        nameTextView.setText(text);
    }
}
