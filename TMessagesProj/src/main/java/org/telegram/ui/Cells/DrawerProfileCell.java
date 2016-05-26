/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;

public class DrawerProfileCell extends FrameLayout implements PhotoViewer.PhotoViewerProvider{

    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView phoneTextView;
    private ImageView shadowView;
    private Rect srcRect = new Rect();
    private Rect destRect = new Rect();
    private Paint paint = new Paint();
    private int currentColor;

    public DrawerProfileCell(Context context) {
        super(context);
        setBackgroundColor(Theme.ACTION_BAR_PROFILE_COLOR);

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.bottom_shadow);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM));
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(32));

        int aSize = themePrefs.getInt("drawerAvatarSize", 64);
        boolean centerAvatar = themePrefs.getBoolean("drawerCenterAvatarCheck", false);
        //addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));
        if(!centerAvatar){
            addView(avatarImageView, LayoutHelper.createFrame(aSize, aSize, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));
        }else{
            addView(avatarImageView, LayoutHelper.createFrame(aSize, aSize, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 67));
        }

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
        if(!centerAvatar){
            nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 28));
        }else{
            nameTextView.setGravity(Gravity.CENTER);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 28));
        }

        phoneTextView = new TextView(context);
        phoneTextView.setTextColor(0xffc2e5ff);
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneTextView.setLines(1);
        phoneTextView.setMaxLines(1);
        phoneTextView.setSingleLine(true);
        if(!centerAvatar){
            phoneTextView.setGravity(Gravity.LEFT);
            addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 9));
        }else{
            phoneTextView.setGravity(Gravity.CENTER);
            addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 9));
        }
    }

    public void refreshAvatar(int size, int radius){
        //SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        removeView(avatarImageView);
        removeView(nameTextView);
        removeView(phoneTextView);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(radius));

        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        if(!themePrefs.getBoolean("drawerCenterAvatarCheck", false)){
            addView(avatarImageView, LayoutHelper.createFrame(size, size, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));
            nameTextView.setGravity(Gravity.LEFT);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 28));
            phoneTextView.setGravity(Gravity.LEFT);
            addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 9));
        }else{
            addView(avatarImageView, LayoutHelper.createFrame(size, size, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 67));
            nameTextView.setGravity(Gravity.CENTER);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 28));
            phoneTextView.setGravity(Gravity.CENTER);
            addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 9));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Build.VERSION.SDK_INT >= 21) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148) + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
        } else {
            try {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148), MeasureSpec.EXACTLY));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        //SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences plusPreferences = ApplicationLoader.applicationContext.getSharedPreferences("plusconfig", Activity.MODE_PRIVATE);
        if(plusPreferences.getBoolean("hideMobile", false) && !plusPreferences.getBoolean("showUsername", false)){
            phoneTextView.setVisibility(GONE);
        }else{
            phoneTextView.setVisibility(VISIBLE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable backgroundDrawable = ApplicationLoader.getCachedWallpaper();
        int color = ApplicationLoader.getServiceMessageColor();
        if (currentColor != color) {
            currentColor = color;
            shadowView.getDrawable().setColorFilter(new PorterDuffColorFilter(color | 0xff000000, PorterDuff.Mode.MULTIPLY));
        }
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        if (ApplicationLoader.isCustomTheme() && backgroundDrawable != null && !themePrefs.getBoolean("drawerHeaderBGCheck", false)) {
            phoneTextView.setTextColor(0xffffffff);
            int visible = INVISIBLE;
            if(!themePrefs.getBoolean("drawerHideBGShadowCheck", false)){
                visible = VISIBLE;
            }
            shadowView.setVisibility(visible);
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
        nameTextView.setText(UserObject.getUserName(user));
        //phoneTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("plusconfig", Activity.MODE_PRIVATE);
        String value;
        if(preferences.getBoolean("showUsername", false)) {
            if (user.username != null && user.username.length() != 0) {
                value = "@" + user.username;
            } else {
                value = LocaleController.getString("UsernameEmpty", R.string.UsernameEmpty);
            }
        } else{
            value = PhoneFormat.getInstance().format("+" + user.phone);
        }
        phoneTextView.setText(value);
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(Theme.ACTION_BAR_MAIN_AVATAR_COLOR);
        avatarImageView.setImage(photo, "50_50", avatarDrawable);
        updateTheme();
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
                object.imageReceiver = avatarImageView.getImageReceiver();
                object.user_id = UserConfig.getClientUserId();
                object.thumb = object.imageReceiver.getBitmap();
                object.size = -1;
                object.radius = avatarImageView.getImageReceiver().getRoundRadius();
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
        avatarImageView.getImageReceiver().setVisible(true, true);
    }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public boolean cancelButtonPressed() {
        return true;
    }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void updateTheme(){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int tColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
        int dColor = AndroidUtilities.getIntDarkerColor("themeColor", -0x40);

        int hColor = themePrefs.getInt("drawerHeaderColor", tColor);
        setBackgroundColor(hColor);
        int value = themePrefs.getInt("drawerHeaderGradient", 0);
        if(value > 0) {
            GradientDrawable.Orientation go;
            switch(value) {
                case 2:
                    go = GradientDrawable.Orientation.LEFT_RIGHT;
                    break;
                case 3:
                    go = GradientDrawable.Orientation.TL_BR;
                    break;
                case 4:
                    go = GradientDrawable.Orientation.BL_TR;
                    break;
                default:
                    go = GradientDrawable.Orientation.TOP_BOTTOM;
            }
            int gradColor = themePrefs.getInt("drawerHeaderGradientColor", tColor);
            int[] colors = new int[]{hColor, gradColor};
            GradientDrawable gd = new GradientDrawable(go, colors);
            setBackgroundDrawable(gd);
        }

        nameTextView.setTextColor(themePrefs.getInt("drawerNameColor", 0xffffffff));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, themePrefs.getInt("drawerNameSize", 15));
        phoneTextView.setTextColor(themePrefs.getInt("drawerPhoneColor", dColor));
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, themePrefs.getInt("drawerPhoneSize", 13));
        //SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences plusPreferences = ApplicationLoader.applicationContext.getSharedPreferences("plusconfig", Activity.MODE_PRIVATE);

        if(plusPreferences.getBoolean("hideMobile", false) && !plusPreferences.getBoolean("showUsername", false)){
            phoneTextView.setVisibility(GONE);
        }else{
            phoneTextView.setVisibility(VISIBLE);
        }
        TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
        TLRPC.FileLocation photo = null;
        if (user != null && user.photo != null && user.photo.photo_small != null ) {
           photo = user.photo.photo_small;
        }
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(themePrefs.getInt("drawerAvatarColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15)));
        int radius = AndroidUtilities.dp(themePrefs.getInt("drawerAvatarRadius", 32));
        avatarDrawable.setRadius(radius);
        //avatarImageView.getImageReceiver().setImageCoords(avatarImageView.getImageReceiver(), avatarTop, avatarSize, avatarSize);

        avatarImageView.getImageReceiver().setRoundRadius(radius);
        avatarImageView.setImage(photo, "50_50", avatarDrawable);

    }

    private void updateHeaderBG(){

    }
}
