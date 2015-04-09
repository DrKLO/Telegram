/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

public class DrawerProfileCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView phoneTextView;
    private ImageView shadowView;
    private Rect srcRect = new Rect();
    private Rect destRect = new Rect();
    private Paint paint = new Paint();

    public DrawerProfileCell(Context context) {
        super(context);
        setBackgroundColor(0xff4c84b5);

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.bottom_shadow);
        addView(shadowView);
        LayoutParams layoutParams = (FrameLayout.LayoutParams) shadowView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(70);
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
        shadowView.setLayoutParams(layoutParams);

        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView);
        layoutParams = (LayoutParams) avatarImageView.getLayoutParams();
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

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable backgroundDrawable = ApplicationLoader.getCachedWallpaper();
        if (ApplicationLoader.isCustomTheme() && backgroundDrawable != null) {
            phoneTextView.setTextColor(0xffffffff);
            shadowView.setVisibility(VISIBLE);
            if (backgroundDrawable instanceof ColorDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
                float scaleX = (float) getMeasuredWidth() / (float) bitmap.getWidth();
                float scaleY = (float) getMeasuredHeight() / (float) bitmap.getHeight();
                float scale = scaleX < scaleY ? scaleY : scaleX;
                int width = (int) (getMeasuredWidth() / scale);
                int height = (int) (getMeasuredHeight() / scale);
                int x = (bitmap.getWidth() - width) / 2;
                int y = (bitmap.getHeight() - height) / 2;
                srcRect.set(x, y, x + width, y + height);
                destRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawBitmap(bitmap, srcRect, destRect, paint);
            }
        } else {
            shadowView.setVisibility(INVISIBLE);
            phoneTextView.setTextColor(0xffc2e5ff);
            super.onDraw(canvas);
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
