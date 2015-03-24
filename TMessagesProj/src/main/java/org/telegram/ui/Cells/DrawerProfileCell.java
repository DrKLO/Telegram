/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.PhotoViewer;

public class DrawerProfileCell extends FrameLayout implements PhotoViewer.PhotoViewerProvider{

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
        final Activity activity = (Activity) context;
        avatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity == null) {
                    return;
                }
                TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
                if (user.photo != null && user.photo.photo_big != null) {
                    PhotoViewer.getInstance().setParentActivity(activity);
                    PhotoViewer.getInstance().openPhoto(user.photo.photo_big, DrawerProfileCell.this);
                }
            }
        });

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
        updateTheme();
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

    @Override
    public void updatePhotoAtIndex(int index) {}

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (fileLocation == null) {
            return null;
        }
        TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
        if (user != null && user.photo != null && user.photo.photo_big != null) {
            TLRPC.FileLocation photoBig = user.photo.photo_big;
            if (photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
                int coords[] = new int[2];
                avatarImageView.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                object.parentView = avatarImageView;
                object.imageReceiver = avatarImageView.imageReceiver;
                object.user_id = UserConfig.getClientUserId();
                object.thumb = object.imageReceiver.getBitmap();
                object.size = -1;
                object.radius = avatarImageView.imageReceiver.getRoundRadius();
                return object;
            }
        }
        return null;
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() {
        avatarImageView.imageReceiver.setVisible(true, true);
    }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void updateTheme(){
        int tColor = AndroidUtilities.getIntColor("themeColor");
        int dColor = AndroidUtilities.getIntDarkerColor("themeColor",-0x40);
        setBackgroundColor(AndroidUtilities.getIntDef("drawerHeaderColor", tColor));
        nameTextView.setTextColor(AndroidUtilities.getIntDef("drawerNameColor", 0xffffffff));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.getIntDef("drawerNameSize", 15));
        phoneTextView.setTextColor(AndroidUtilities.getIntDef("drawerPhoneColor", dColor));
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.getIntDef("drawerPhoneSize", 13));
        TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
        TLRPC.FileLocation photo = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
        }
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(AndroidUtilities.getIntDef("drawerAvatarColor",AndroidUtilities.getIntDarkerColor("themeColor", 0x15)));
        int radius = AndroidUtilities.dp(AndroidUtilities.getIntDef("drawerAvatarRadius", 32));
        avatarDrawable.setRadius(radius);
        avatarImageView.imageReceiver.setRoundRadius(radius);
        avatarImageView.setImage(photo, "50_50", avatarDrawable);
        if(AndroidUtilities.getBoolMain("hideMobile")){
            phoneTextView.setVisibility(GONE);
        }else{
            phoneTextView.setVisibility(VISIBLE);
        }
    }
}
