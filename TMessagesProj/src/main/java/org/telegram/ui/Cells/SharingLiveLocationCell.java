/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
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

    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate((int) rect.left - 5, (int) rect.top - 5, (int) rect.right + 5, (int) rect.bottom + 5);
            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
        }
    };

    public SharingLiveLocationCell(Context context, boolean distance) {
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
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 54 : 73, 12, LocaleController.isRTL ? 73 : 54, 0));

            distanceTextView = new SimpleTextView(context);
            distanceTextView.setTextSize(14);
            distanceTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            distanceTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            addView(distanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 54 : 73, 37, LocaleController.isRTL ? 73 : 54, 0));
        } else {
            addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 17, 7, LocaleController.isRTL ? 17 : 0, 0));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 54 : 74, 17, LocaleController.isRTL ? 74 : 54, 0));
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

    public void setDialog(MessageObject messageObject, Location userLocation) {
        int fromId = messageObject.messageOwner.from_id;
        if (messageObject.isForwarded()) {
            if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                fromId = -messageObject.messageOwner.fwd_from.channel_id;
            } else {
                fromId = messageObject.messageOwner.fwd_from.from_id;
            }
        }
        String address = null;
        TLRPC.FileLocation photo = null;
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
                TLRPC.User user = MessagesController.getInstance().getUser(fromId);
                if (user != null) {
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                    avatarDrawable = new AvatarDrawable(user);
                    name = UserObject.getUserName(user);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(-fromId);
                if (chat != null) {
                    if (chat.photo != null) {
                        photo = chat.photo.photo_small;
                    }
                    avatarDrawable = new AvatarDrawable(chat);
                    name = chat.title;
                }
            }
            avatarImageView.setImage(photo, null, avatarDrawable);
        }
        nameTextView.setText(name);

        location.setLatitude(messageObject.messageOwner.media.geo.lat);
        location.setLongitude(messageObject.messageOwner.media.geo._long);
        if (userLocation != null) {
            float distance = location.distanceTo(userLocation);
            if (address != null) {
                if (distance < 1000) {
                    distanceTextView.setText(String.format("%s - %d %s", address, (int) (distance), LocaleController.getString("MetersAway", R.string.MetersAway)));
                } else {
                    distanceTextView.setText(String.format("%s - %.2f %s", address, distance / 1000.0f, LocaleController.getString("KMetersAway", R.string.KMetersAway)));
                }
            } else {
                if (distance < 1000) {
                    distanceTextView.setText(String.format("%d %s", (int) (distance), LocaleController.getString("MetersAway", R.string.MetersAway)));
                } else {
                    distanceTextView.setText(String.format("%.2f %s", distance / 1000.0f, LocaleController.getString("KMetersAway", R.string.KMetersAway)));
                }
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
        TLRPC.FileLocation photo = null;
        if (lower_id > 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(lower_id);
            avatarDrawable.setInfo(user);
            if (user != null) {
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                if (user.photo != null && user.photo.photo_small != null) {
                    photo = user.photo.photo_small;
                }
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
            if (chat != null) {
                avatarDrawable.setInfo(chat);
                nameTextView.setText(chat.title);
                if (chat.photo != null && chat.photo.photo_small != null) {
                    photo = chat.photo.photo_small;
                }
            }
        }

        LatLng position = info.marker.getPosition();
        location.setLatitude(position.latitude);
        location.setLongitude(position.longitude);

        String time = LocaleController.formatLocationUpdateDate(info.object.edit_date != 0 ? info.object.edit_date : info.object.date);
        if (userLocation != null) {
            float distance = location.distanceTo(userLocation);
            if (distance < 1000) {
                distanceTextView.setText(String.format("%s - %d %s", time, (int) (distance), LocaleController.getString("MetersAway", R.string.MetersAway)));
            } else {
                distanceTextView.setText(String.format("%s - %.2f %s", time, distance / 1000.0f, LocaleController.getString("KMetersAway", R.string.KMetersAway)));
            }
        } else {
            distanceTextView.setText(time);
        }

        avatarImageView.setImage(photo, null, avatarDrawable);
    }

    public void setDialog(LocationController.SharingLocationInfo info) {
        currentInfo = info;
        int lower_id = (int) info.did;
        TLRPC.FileLocation photo = null;
        if (lower_id > 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(lower_id);
            if (user != null) {
                avatarDrawable.setInfo(user);
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                if (user.photo != null && user.photo.photo_small != null) {
                    photo = user.photo.photo_small;
                }
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-lower_id);
            if (chat != null) {
                avatarDrawable.setInfo(chat);
                nameTextView.setText(chat.title);
                if (chat.photo != null && chat.photo.photo_small != null) {
                    photo = chat.photo.photo_small;
                }
            }
        }
        avatarImageView.setImage(photo, null, avatarDrawable);
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
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
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
