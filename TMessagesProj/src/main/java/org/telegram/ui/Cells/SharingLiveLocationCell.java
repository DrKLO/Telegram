/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.LocationActivity;

import java.util.HashSet;
import java.util.List;

public class SharingLiveLocationCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private int distanceTextViewHeight;
    private TextView distanceTextView;
    private boolean distanceTextViewSingle;
    private AvatarDrawable avatarDrawable;

    private int padding;
    private RectF rect = new RectF();

    private LocationController.SharingLocationInfo currentInfo;
    private LocationActivity.LiveLocation liveLocation;
    private Location location = new Location("network");
    private final Theme.ResourcesProvider resourcesProvider;

    private int currentAccount = UserConfig.selectedAccount;

    private Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate((int) rect.left - 5, (int) rect.top - 5, (int) rect.right + 5, (int) rect.bottom + 5);
            AndroidUtilities.runOnUIThread(invalidateRunnable, 1000);
        }
    };

    public SharingLiveLocationCell(Context context, boolean distance, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.padding = padding;

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(dp(21));

        avatarDrawable = new AvatarDrawable();

        nameTextView = new SimpleTextView(context);
        NotificationCenter.listenEmojiLoading(nameTextView);
        nameTextView.setTextSize(16);
        nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        nameTextView.setScrollNonFitText(true);

        if (distance) {
            addView(avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 15, 12, LocaleController.isRTL ? 15 : 0, 0));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 73, 12, LocaleController.isRTL ? 73 : 16, 0));

            distanceTextView = new TextView(context);
            distanceTextView.setSingleLine();
            distanceTextViewSingle = true;
            distanceTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            distanceTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
            distanceTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            addView(distanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 73, 33, LocaleController.isRTL ? 73 : padding, 0));
        } else {
            addView(avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 15, 6, LocaleController.isRTL ? 15 : 0, 0));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? padding : 74, 17, LocaleController.isRTL ? 74 : padding, 0));
        }

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(distanceTextView != null ? 66 : 54) + (distanceTextView != null && !distanceTextViewSingle ? -dp(20) + distanceTextViewHeight : 0), MeasureSpec.EXACTLY)
        );
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
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                avatarDrawable = new AvatarDrawable(user);
                name = UserObject.getUserName(user);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                avatarDrawable = new AvatarDrawable(chat);
                name = chat.title;
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
        }
        nameTextView.setText(name);

        location.setLatitude(chatLocation.geo_point.lat);
        location.setLongitude(chatLocation.geo_point._long);
        distanceTextView.setSingleLine(distanceTextViewSingle = true);
        distanceTextView.setText(address);
    }

    private boolean loading;
    private double lastLat, lastLong;
    private SpannableString loadingString;
    private CharSequence lastName = "";
    private CharSequence getName(double lat, double _long) {
        if (loading) {
            return lastName;
        }
        if (Math.abs(lastLat - lat) > 0.000001d || Math.abs(lastLong - _long) > 0.000001d || TextUtils.isEmpty(lastName)) {
            loading = true;
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    Geocoder geocoder = new Geocoder(ApplicationLoader.applicationContext, LocaleController.getInstance().getCurrentLocale());
                    List<Address> addresses = geocoder.getFromLocation(lat, _long, 1);
                    if (addresses.isEmpty()) {
                        lastName = LocationController.detectOcean(_long, lat);
                        if (lastName == null) {
                            lastName = "";
                        } else {
                            lastName = "🌊 " + lastName;
                        }
                    } else {
                        Address addr = addresses.get(0);

                        StringBuilder sb = new StringBuilder();

                        HashSet<String> parts = new HashSet<>();
                        parts.add(addr.getSubAdminArea());
                        parts.add(addr.getAdminArea());
                        parts.add(addr.getLocality());
                        parts.add(addr.getCountryName());
                        for (String part : parts) {
                            if (TextUtils.isEmpty(part)) {
                                continue;
                            }
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append(part);
                        }
                        lastName = sb.toString();
                        String emoji = LocationController.countryCodeToEmoji(addr.getCountryCode());
                        if (emoji != null && Emoji.getEmojiDrawable(emoji) != null) {
                            lastName = emoji + " " + lastName;
                        }
                    }
                } catch (Exception ignore) {}
                AndroidUtilities.runOnUIThread(() -> {
                    lastLat = lat;
                    lastLong = _long;
                    loading = false;
                    lastName = Emoji.replaceEmoji(lastName, nameTextView.getPaint().getFontMetricsInt(), false);
                    nameTextView.setText(lastName);
                });
            });
        }
        return lastName;
    }


    public void setDialog(MessageObject messageObject, Location userLocation, boolean userLocationDenied) {
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.local_id == -1) {
            Drawable drawable = getResources().getDrawable(R.drawable.pin);
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
            int color = getThemedColor(Theme.key_location_placeLocationBackground);
            Drawable circle = Theme.createSimpleSelectorCircleDrawable(dp(42), color, color);
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(dp(42), dp(42));
            combinedDrawable.setIconSize(dp(24), dp(24));
            avatarImageView.setImageDrawable(combinedDrawable);

            nameTextView.setText(Emoji.replaceEmoji(MessagesController.getInstance(currentAccount).getPeerName(DialogObject.getPeerDialogId(messageObject.messageOwner.peer_id)), nameTextView.getPaint().getFontMetricsInt(), false));
            distanceTextView.setSingleLine(distanceTextViewSingle = false);
            String text = messageObject.messageOwner.media.address;
            distanceTextViewHeight = new StaticLayout(text, distanceTextView.getPaint(), AndroidUtilities.displaySize.x - dp(padding + 73), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false).getHeight();
            distanceTextView.setText(text);
            requestLayout();

            return;
        } else {
            distanceTextView.setSingleLine(distanceTextViewSingle = true);
        }
        long fromId = messageObject.getFromChatId();
        if (messageObject.isForwarded()) {
            fromId = MessageObject.getPeerId(messageObject.messageOwner.fwd_from.from_id);
        }
        currentAccount = messageObject.currentAccount;
        String address = null;
        CharSequence name = "";
        if (!TextUtils.isEmpty(messageObject.messageOwner.media.address)) {
            address = messageObject.messageOwner.media.address;
        }
        boolean noTitle = TextUtils.isEmpty(messageObject.messageOwner.media.title);
        if (noTitle) {
            name = "";
            avatarDrawable = null;
            if (fromId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromId);
                if (user != null) {
                    avatarDrawable = new AvatarDrawable(user);
                    name = UserObject.getUserName(user);
                    avatarImageView.setForUserOrChat(user, avatarDrawable);
                } else {
                    noTitle = false;
                    name = getName(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                if (chat != null) {
                    avatarDrawable = new AvatarDrawable(chat);
                    name = chat.title;
                    avatarImageView.setForUserOrChat(chat, avatarDrawable);
                } else {
                    noTitle = false;
                    name = getName(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long);
                }
            }
        } else {
            name = "";
        }
        if (TextUtils.isEmpty(name)) {
            if (loadingString == null) {
                loadingString = new SpannableString("dkaraush has been here");
                loadingString.setSpan(new LoadingSpan(nameTextView, dp(100), 0, resourcesProvider), 0, loadingString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            name = loadingString;
        }
        if (!noTitle) {
            if (!TextUtils.isEmpty(messageObject.messageOwner.media.title)) {
                name = messageObject.messageOwner.media.title;
            }

            Drawable drawable = getResources().getDrawable(R.drawable.pin);
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_sendLocationIcon), PorterDuff.Mode.MULTIPLY));
            int color = getThemedColor(Theme.key_location_placeLocationBackground);
            Drawable circle = Theme.createSimpleSelectorCircleDrawable(dp(42), color, color);
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(dp(42), dp(42));
            combinedDrawable.setIconSize(dp(24), dp(24));
            avatarImageView.setImageDrawable(combinedDrawable);
        }
        nameTextView.setText(name);

        location.setLatitude(messageObject.messageOwner.media.geo.lat);
        location.setLongitude(messageObject.messageOwner.media.geo._long);
        if (userLocation != null) {
            float distance = location.distanceTo(userLocation);
            if (address != null) {
                distanceTextView.setText(String.format("%s - %s", address, LocaleController.formatDistance(distance, 0)));
            } else {
                distanceTextView.setText(LocaleController.formatDistance(distance, 0));
            }
        } else {
            if (address != null) {
                distanceTextView.setText(address);
            } else if (!userLocationDenied) {
                distanceTextView.setText(LocaleController.getString(R.string.Loading));
            } else {
                distanceTextView.setText("");
            }
        }
    }

    public void setDialog(LocationActivity.LiveLocation info, Location userLocation) {
        liveLocation = info;
        if (DialogObject.isUserDialog(info.id)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(info.id);
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-info.id);
            if (chat != null) {
                avatarDrawable.setInfo(currentAccount, chat);
                nameTextView.setText(chat.title);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
        }

        IMapsProvider.LatLng position = info.marker.getPosition();
        location.setLatitude(position.latitude);
        location.setLongitude(position.longitude);

        String time = LocaleController.formatLocationUpdateDate(info.object.edit_date != 0 ? info.object.edit_date : info.object.date);
        if (userLocation != null) {
            distanceTextView.setText(String.format("%s - %s", time, LocaleController.formatDistance(location.distanceTo(userLocation), 0)));
        } else {
            distanceTextView.setText(time);
        }
    }

    public void setDialog(LocationController.SharingLocationInfo info) {
        currentInfo = info;
        currentAccount = info.account;
        avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
        if (DialogObject.isUserDialog(info.did)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(info.did);
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-info.did);
            if (chat != null) {
                avatarDrawable.setInfo(currentAccount, chat);
                nameTextView.setText(chat.title);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
        }
    }

    private Drawable foreverDrawable;
    private int foreverDrawableColor;

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
        boolean forever = period == 0x7FFFFFFF;
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (stopTime < currentTime && !forever) {
            return;
        }
        float progress = forever ? 1 : Math.abs(stopTime - currentTime) / (float) period;
        if (LocaleController.isRTL) {
            rect.set(dp(13), dp(distanceTextView != null ? 18 : 12), dp(43), dp(distanceTextView != null ? 48 : 42));
        } else {
            rect.set(getMeasuredWidth() - dp(43), dp(distanceTextView != null ? 18 : 12), getMeasuredWidth() - dp(13), dp(distanceTextView != null ? 48 : 42));
        }

        int color;
        if (distanceTextView == null) {
            color = getThemedColor(Theme.key_dialog_liveLocationProgress);
        } else {
            color = getThemedColor(Theme.key_location_liveLocationProgress);
        }
        Theme.chat_radialProgress2Paint.setColor(color);
        Theme.chat_livePaint.setColor(color);

        int a = Theme.chat_radialProgress2Paint.getAlpha();
        Theme.chat_radialProgress2Paint.setAlpha((int) (.20f * a));
        canvas.drawArc(rect, -90, 360, false, Theme.chat_radialProgress2Paint);
        Theme.chat_radialProgress2Paint.setAlpha((int) (a));
        canvas.drawArc(rect, -90, -360 * progress, false, Theme.chat_radialProgress2Paint);
        Theme.chat_radialProgress2Paint.setAlpha(a);

        if (forever) {
            if (foreverDrawable == null) {
                foreverDrawable = getContext().getResources().getDrawable(R.drawable.filled_location_forever).mutate();
            }
            if (Theme.chat_livePaint.getColor() != foreverDrawableColor) {
                foreverDrawable.setColorFilter(new PorterDuffColorFilter(foreverDrawableColor = Theme.chat_livePaint.getColor(), PorterDuff.Mode.SRC_IN));
            }
            foreverDrawable.setBounds(
                    (int) rect.centerX() - foreverDrawable.getIntrinsicWidth() / 2,
                    (int) rect.centerY() - foreverDrawable.getIntrinsicHeight() / 2,
                    (int) rect.centerX() + foreverDrawable.getIntrinsicWidth() / 2,
                    (int) rect.centerY() + foreverDrawable.getIntrinsicHeight() / 2
            );
            foreverDrawable.draw(canvas);
        } else {
            String text = LocaleController.formatLocationLeftTime(stopTime - currentTime);
            float size = Theme.chat_livePaint.measureText(text);
            canvas.drawText(text, rect.centerX() - size / 2, dp(distanceTextView != null ? 37 : 31), Theme.chat_livePaint);
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
