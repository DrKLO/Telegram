/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class MentionCell extends LinearLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private TextView usernameTextView;
    private AvatarDrawable avatarDrawable;

    public MentionCell(Context context) {
        super(context);

        setOrientation(HORIZONTAL);

        setBackgroundResource(R.drawable.list_selector);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setSmallStyle(true);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(14));
        addView(imageView, LayoutHelper.createLinear(28, 28, 12, 4, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff000000);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        usernameTextView = new TextView(context);
        usernameTextView.setTextColor(0xff999999);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        usernameTextView.setSingleLine(true);
        usernameTextView.setGravity(Gravity.LEFT);
        usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 8, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(event.getX(), event.getY());
            }
        }
        return super.onTouchEvent(event);
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
        nameTextView.setText(UserObject.getUserName(user));
        usernameTextView.setText("@" + user.username);
        imageView.setVisibility(VISIBLE);
        usernameTextView.setVisibility(VISIBLE);
    }

    public void setText(String text) {
        imageView.setVisibility(INVISIBLE);
        usernameTextView.setVisibility(INVISIBLE);
        nameTextView.setText(text);
    }

    public void setBotCommand(String command, String help, TLRPC.User user) {
        if (user != null) {
            imageView.setVisibility(VISIBLE);
            avatarDrawable.setInfo(user);
            if (user.photo != null && user.photo.photo_small != null) {
                imageView.setImage(user.photo.photo_small, "50_50", avatarDrawable);
            } else {
                imageView.setImageDrawable(avatarDrawable);
            }
        } else {
            imageView.setVisibility(INVISIBLE);
        }
        usernameTextView.setVisibility(VISIBLE);
        nameTextView.setText(command);
        usernameTextView.setText(help);
    }

    public void setIsDarkTheme(boolean isDarkTheme) {
        if (isDarkTheme) {
            nameTextView.setTextColor(0xffffffff);
            usernameTextView.setTextColor(0xff999999);
        } else {
            nameTextView.setTextColor(0xff000000);
            usernameTextView.setTextColor(0xff999999);
        }
    }
}
