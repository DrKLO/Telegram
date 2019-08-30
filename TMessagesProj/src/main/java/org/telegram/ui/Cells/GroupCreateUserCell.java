/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class GroupCreateUserCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable;
    private TLObject currentObject;
    private CharSequence currentName;
    private CharSequence currentStatus;

    private int currentAccount = UserConfig.selectedAccount;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    public GroupCreateUserCell(Context context, boolean needCheck, int padding) {
        super(context);
        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : (13 + padding), 6, LocaleController.isRTL ? (13 + padding) : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 28 : 72) + padding, 10, (LocaleController.isRTL ? 72 : 28) + padding, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 28 : 72) + padding, 32, (LocaleController.isRTL ? 72 : 28) + padding, 0));

        if (needCheck) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 33, LocaleController.isRTL ? 39 : 0, 0));
        }
    }

    public void setObject(TLObject object, CharSequence name, CharSequence status) {
        currentObject = object;
        currentStatus = status;
        currentName = name;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public void setCheckBoxEnabled(boolean enabled) {
        checkBox.setEnabled(enabled);
    }

    public TLObject getObject() {
        return currentObject;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentObject == null) {
            return;
        }
        TLRPC.FileLocation photo = null;
        String newName = null;


        if (currentObject instanceof TLRPC.User) {
            TLRPC.User currentUser = (TLRPC.User) currentObject;
            if (currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }
            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (currentUser != null && currentStatus == null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    int newStatus = 0;
                    if (currentUser.status != null) {
                        newStatus = currentUser.status.expires;
                    }
                    if (newStatus != lastStatus) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    newName = UserObject.getUserName(currentUser);
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }
            avatarDrawable.setInfo(currentUser);
            lastStatus = currentUser.status != null ? currentUser.status.expires : 0;

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName, true);
            } else {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
                nameTextView.setText(lastName);
            }

            if (currentStatus == null) {
                if (currentUser.bot) {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                    statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    statusTextView.setText(LocaleController.getString("Bot", R.string.Bot));
                } else {
                    if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
                        statusTextView.setTag(Theme.key_windowBackgroundWhiteBlueText);
                        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        statusTextView.setText(LocaleController.getString("Online", R.string.Online));
                    } else {
                        statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                        statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser));
                    }
                }
            }

            avatarImageView.setImage(ImageLocation.getForUser(currentUser, false), "50_50", avatarDrawable, currentUser);
        } else {
            TLRPC.Chat currentChat = (TLRPC.Chat) currentObject;
            if (currentChat.photo != null) {
                photo = currentChat.photo.photo_small;
            }
            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                    newName = currentChat.title;
                    if (!newName.equals(lastName)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate) {
                    return;
                }
            }

            avatarDrawable.setInfo(currentChat);

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName, true);
            } else {
                lastName = newName == null ? currentChat.title : newName;
                nameTextView.setText(lastName);
            }

            if (currentStatus == null) {
                statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                if (currentChat.participants_count != 0) {
                    statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participants_count));
                } else if (currentChat.has_geo) {
                    statusTextView.setText(LocaleController.getString("MegaLocation", R.string.MegaLocation));
                } else if (TextUtils.isEmpty(currentChat.username)) {
                    statusTextView.setText(LocaleController.getString("MegaPrivate", R.string.MegaPrivate));
                } else {
                    statusTextView.setText(LocaleController.getString("MegaPublic", R.string.MegaPublic));
                }
            }

            avatarImageView.setImage(ImageLocation.getForChat(currentChat, false), "50_50", avatarDrawable, currentChat);
        }

        if (currentStatus != null) {
            statusTextView.setText(currentStatus, true);
            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
