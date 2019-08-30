/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;

import com.google.android.gms.maps.model.LatLng;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LocationActivity;

public class SharingLiveLocationCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView distanceTextView;
    private AvatarDrawable avatarDrawable;

    private RectF rect = new RectF();

    private LocationController.SharingLocationInfo currentInfo;
    private LocationActivity.LiveLocation liveLocation;
    private Location location = new Location("network");

    private int currentAccount;

    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate((int) rect.left - 5, (int) rect.top - 5, (int) rect.right + 5, (int) rect.bottom + 5);
            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
        }
    };

    public SharingLiveLocationCell(Context context, boolean distance, int padding) {
        super(context);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(20));


        avatarDrawable = new AvatarDrawable();

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextSize(16);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        if (distance) {
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 17, 13, LocaleController.isRTL ? 17 : 0, 0));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 73, 12, LocaleController.isRTL ? 73 : padding, 0));

            distanceTextView = new SimpleTextView(context);
            distanceTextView.setTextSize(14);
            distanceTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            distanceTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            addView(distanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 73, 37, LocaleController.isRTL ? 73 : padding, 0));
        } else {
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 17, 7, LocaleController.isRTL ? 17 : 0, 0));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 74, 17, LocaleController.isRTL ? 74 : padding, 0));
        }

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(distanceTextView != null ? 66 : 54), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AndroidUtilities.runOnUIThread(invalidateRunnable);
    }

    public void setDialog(long dialogId, TLRPC.TL_channelLocation chatLocation) {
        currentAccount = UserConfig.selectedAccount;
        String address = chatLocation.address;
        String name = "";
        avatarDrawable = null;
        int lowerId = (int) dialogId;
        if (lowerId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lowerId);
            if (user != null) {
                avatarDrawable = new AvatarDrawable(user);
                name = UserObject.getUserName(user);
                avatarImageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable,  user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
            if (chat != null) {
                avatarDrawable = new AvatarDrawable(chat);
                name = chat.title;
                avatarImageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable,  chat);
            }
        }
        nameTextView.setText(name);

        location.setLatitude(chatLocation.geo_point.lat);
        location.setLongitude(chatLocation.geo_point._long);
        distanceTextView.setText(address);
    }

    public void setDialog(MessageObject messageObject, Location userLocation) {
        int fromId = messageObject.messageOwner.from_id;
        if (messageObject.isForwarded()) {
            if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                fromId = -messageObject.messageOwner.fwd_from.channel_id;
            } else {
                fromId = messageObject.messageOwner.fwd_from.from_id;
            }
        }
        currentAccount = messageObject.currentAccount;
        String address = null;
        String name;
        if (!TextUtils.isEmpty(messageObject.messageOwner.media.address)) {
            address = messageObject.messageOwner.media.address;
        }
        if (!TextUtils.isEmpty(messageObject.messageOwner.media.title)) {
            name = messageObject.messageOwner.media.title;

            Drawable drawable = getResources().getDrawable(R.drawable.pin);
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
            int color = Theme.getColor(Theme.key_location_placeLocationBackground);
            Drawable circle = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40), color, color);
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
            combinedDrawable.setIconSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            avatarImageView.setImageDrawable(combinedDrawable);
        } else {
            name = "";
            avatarDrawable = null;
            if (fromId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromId);
                if (user != null) {
                    avatarDrawable = new AvatarDrawable(user);
                    name = UserObject.getUserName(user);
                    avatarImageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable,  user);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                if (chat != null) {
                    avatarDrawable = new AvatarDrawable(chat);
                    name = chat.title;
                    avatarImageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable,  chat);
                }
            }
        }
        nameTextView.setText(name);

        location.setLatitude(messageObject.messageOwner.media.geo.lat);
        location.setLongitude(messageObject.messageOwner.media.geo._long);
        if (userLocation != null) {
            float distance = location.distanceTo(userLocation);
            if (address != null) {
                distanceTextView.setText(String.format("%s - %s", address, LocaleController.formatDistance(distance)));
            } else {
                distanceTextView.setText(LocaleController.formatDistance(distance));
            }
        } else {
            if (address != null) {
                distanceTextView.setText(address);
            } else {
                distanceTextView.setText(LocaleController.getString("Loading", R.string.Loading));
            }
        }
    }

    public void setDialog(LocationActivity.LiveLocation info, Location userLocation) {
        liveLocation = info;
        int lower_id = info.id;
        if (lower_id > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
            if (user != null) {
                avatarDrawable.setInfo(user);
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                avatarImageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable,  user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
            if (chat != null) {
                avatarDrawable.setInfo(chat);
                nameTextView.setText(chat.title);
                avatarImageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable,  chat);
            }
        }

        LatLng position = info.marker.getPosition();
        location.setLatitude(position.latitude);
        location.setLongitude(position.longitude);

        String time = LocaleController.formatLocationUpdateDate(info.object.edit_date != 0 ? info.object.edit_date : info.object.date);
        if (userLocation != null) {
            distanceTextView.setText(String.format("%s - %s", time, LocaleController.formatDistance(location.distanceTo(userLocation))));
        } else {
            distanceTextView.setText(time);
        }
    }

    public void setDialog(LocationController.SharingLocationInfo info) {
        currentInfo = info;
        int lower_id = (int) info.did;
        if (lower_id > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
            if (user != null) {
                avatarDrawable.setInfo(user);
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                avatarImageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable,  user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
            if (chat != null) {
                avatarDrawable.setInfo(chat);
                nameTextView.setText(chat.title);
                avatarImageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable,  chat);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentInfo == null && liveLocation == null) {
            return;
        }
        int stopTime;
        int period;
        if (currentInfo != null) {
            stopTime = currentInfo.stopTime;
            period = currentInfo.period;
        } else {
            stopTime = liveLocation.object.date + liveLocation.object.media.period;
            period = liveLocation.object.media.period;
        }
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (stopTime < currentTime) {
            return;
        }
        float progress = Math.abs(stopTime - currentTime) / (float) period;
        if (LocaleController.isRTL) {
            rect.set(AndroidUtilities.dp(13), AndroidUtilities.dp(distanceTextView != null ? 18 : 12), AndroidUtilities.dp(43), AndroidUtilities.dp(distanceTextView != null ? 48 : 42));
        } else {
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(43), AndroidUtilities.dp(distanceTextView != null ? 18 : 12), getMeasuredWidth() - AndroidUtilities.dp(13), AndroidUtilities.dp(distanceTextView != null ? 48 : 42));
        }

        int color;
        if (distanceTextView == null) {
            color = Theme.getColor(Theme.key_dialog_liveLocationProgress);
        } else {
            color = Theme.getColor(Theme.key_location_liveLocationProgress);
        }
        Theme.chat_radialProgress2Paint.setColor(color);
        Theme.chat_livePaint.setColor(color);

        canvas.drawArc(rect, -90, -360 * progress, false, Theme.chat_radialProgress2Paint);

        String text = LocaleController.formatLocationLeftTime(stopTime - currentTime);

        float size = Theme.chat_livePaint.measureText(text);

        canvas.drawText(text, rect.centerX() - size / 2, AndroidUtilities.dp(distanceTextView != null ? 37 : 31), Theme.chat_livePaint);
    }
}
