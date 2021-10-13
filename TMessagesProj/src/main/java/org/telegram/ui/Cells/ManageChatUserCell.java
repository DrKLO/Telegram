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
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
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
import org.telegram.ui.Components.LayoutHelper;

public class ManageChatUserCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private ImageView optionsButton;
    private ImageView customImageView;

    private AvatarDrawable avatarDrawable;
    private Object currentObject;

    private CharSequence currentName;
    private CharSequence currrntStatus;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;
    private boolean isAdmin;

    private boolean needDivider;

    private int statusColor;
    private int statusOnlineColor;

    private int namePadding;

    private int currentAccount = UserConfig.selectedAccount;

    private String dividerColor;

    private ManageChatUserCellDelegate delegate;

    public interface ManageChatUserCellDelegate {
        boolean onOptionsButtonCheck(ManageChatUserCell cell, boolean click);
    }

    public ManageChatUserCell(Context context, int avatarPadding, int nPadding, boolean needOption) {
        super(context);

        statusColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        statusOnlineColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);

        namePadding = nPadding;

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(23));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 7 + avatarPadding, 8, LocaleController.isRTL ? 7 + avatarPadding : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(17);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 + 18 : (68 + namePadding), 11.5f, LocaleController.isRTL ? (68 + namePadding) : 28 + 18, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : (68 + namePadding), 34.5f, LocaleController.isRTL ? (68 + namePadding) : 28, 0));

        if (needOption) {
            optionsButton = new ImageView(context);
            optionsButton.setFocusable(false);
            optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            optionsButton.setImageResource(R.drawable.ic_ab_other);
            optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            optionsButton.setScaleType(ImageView.ScaleType.CENTER);
            addView(optionsButton, LayoutHelper.createFrame(60, 64, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));
            optionsButton.setOnClickListener(v -> delegate.onOptionsButtonCheck(ManageChatUserCell.this, true));
            optionsButton.setContentDescription(LocaleController.getString("AccDescrUserOptions", R.string.AccDescrUserOptions));
        }
    }

    public void setCustomRightImage(int resId) {
        customImageView = new ImageView(getContext());
        customImageView.setImageResource(resId);
        customImageView.setScaleType(ImageView.ScaleType.CENTER);
        customImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_mutedIconUnscrolled), PorterDuff.Mode.MULTIPLY));
        addView(customImageView, LayoutHelper.createFrame(52, 64, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));
    }

    public void setCustomImageVisible(boolean visible) {
        if (customImageView == null) {
            return;
        }
        customImageView.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setData(Object object, CharSequence name, CharSequence status, boolean divider) {
        if (object == null) {
            currrntStatus = null;
            currentName = null;
            currentObject = null;
            nameTextView.setText("");
            statusTextView.setText("");
            avatarImageView.setImageDrawable(null);
            return;
        }
        currrntStatus = status;
        currentName = name;
        currentObject = object;
        if (optionsButton != null) {
            boolean visible = delegate.onOptionsButtonCheck(ManageChatUserCell.this, false);
            optionsButton.setVisibility(visible ? VISIBLE : INVISIBLE);
            nameTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (visible ? 46 : 28) : (68 + namePadding), status == null || status.length() > 0 ? 11.5f : 20.5f, LocaleController.isRTL ? (68 + namePadding) : (visible ? 46 : 28), 0));
            statusTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (visible ? 46 : 28) : (68 + namePadding), 34.5f, LocaleController.isRTL ? (68 + namePadding) : (visible ? 46 : 28), 0));
        } else if (customImageView != null) {
            boolean visible = customImageView.getVisibility() == VISIBLE;
            nameTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (visible ? 54 : 28) : (68 + namePadding), status == null || status.length() > 0 ? 11.5f : 20.5f, LocaleController.isRTL ? (68 + namePadding) : (visible ? 54 : 28), 0));
            statusTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (visible ? 54 : 28) : (68 + namePadding), 34.5f, LocaleController.isRTL ? (68 + namePadding) : (visible ? 54 : 28), 0));
        }
        needDivider = divider;
        setWillNotDraw(!needDivider);
        update(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public long getUserId() {
        if (currentObject instanceof TLRPC.User) {
            return ((TLRPC.User) currentObject).id;
        }
        return 0;
    }

    public void setStatusColors(int color, int onlineColor) {
        statusColor = color;
        statusOnlineColor = onlineColor;
    }

    public void setIsAdmin(boolean value) {
        isAdmin = value;
    }

    public boolean hasAvatarSet() {
        return avatarImageView.getImageReceiver().hasNotThumb();
    }

    public void setNameColor(int color) {
        nameTextView.setTextColor(color);
    }

    public void setDividerColor(String key) {
        dividerColor = key;
    }

    public void update(int mask) {
        if (currentObject == null) {
            return;
        }
        if (currentObject instanceof TLRPC.User) {
            TLRPC.User currentUser = (TLRPC.User) currentObject;

            TLRPC.FileLocation photo = null;
            String newName = null;
            if (currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                        continueUpdate = true;
                    }
                }
                if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
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
            if (currentUser.status != null) {
                lastStatus = currentUser.status.expires;
            } else {
                lastStatus = 0;
            }

            if (currentName != null) {
                lastName = null;
                nameTextView.setText(currentName);
            } else {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
                nameTextView.setText(lastName);
            }
            if (currrntStatus != null) {
                statusTextView.setTextColor(statusColor);
                statusTextView.setText(currrntStatus);
            } else {
                if (currentUser.bot) {
                    statusTextView.setTextColor(statusColor);
                    if (currentUser.bot_chat_history || isAdmin) {
                        statusTextView.setText(LocaleController.getString("BotStatusRead", R.string.BotStatusRead));
                    } else {
                        statusTextView.setText(LocaleController.getString("BotStatusCantRead", R.string.BotStatusCantRead));
                    }
                } else {
                    if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
                        statusTextView.setTextColor(statusOnlineColor);
                        statusTextView.setText(LocaleController.getString("Online", R.string.Online));
                    } else {
                        statusTextView.setTextColor(statusColor);
                        statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser));
                    }
                }
            }
            lastAvatar = photo;
            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentObject instanceof TLRPC.Chat) {
            TLRPC.Chat currentChat = (TLRPC.Chat) currentObject;

            TLRPC.FileLocation photo = null;
            String newName = null;
            if (currentChat.photo != null) {
                photo = currentChat.photo.photo_small;
            }

            if (mask != 0) {
                boolean continueUpdate = false;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                    if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
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
                nameTextView.setText(currentName);
            } else {
                lastName = newName == null ? currentChat.title : newName;
                nameTextView.setText(lastName);
            }
            if (currrntStatus != null) {
                statusTextView.setTextColor(statusColor);
                statusTextView.setText(currrntStatus);
            } else {
                statusTextView.setTextColor(statusColor);
                if (currentChat.participants_count != 0) {
                    if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                        statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentChat.participants_count));
                    } else {
                        statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participants_count));
                    }
                } else if (currentChat.has_geo) {
                    statusTextView.setText(LocaleController.getString("MegaLocation", R.string.MegaLocation));
                } else if (TextUtils.isEmpty(currentChat.username)) {
                    statusTextView.setText(LocaleController.getString("MegaPrivate", R.string.MegaPrivate));
                } else {
                    statusTextView.setText(LocaleController.getString("MegaPublic", R.string.MegaPublic));
                }
            }
            lastAvatar = photo;
            avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
        } else if (currentObject instanceof Integer) {
            nameTextView.setText(currentName);
            statusTextView.setTextColor(statusColor);
            statusTextView.setText(currrntStatus);
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SHARES);
            avatarImageView.setImage(null, "50_50", avatarDrawable);
        }
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void setDelegate(ManageChatUserCellDelegate manageChatUserCellDelegate) {
        delegate = manageChatUserCellDelegate;
    }

    public Object getCurrentObject() {
        return currentObject;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            if (dividerColor != null) {
                Theme.dividerExtraPaint.setColor(Theme.getColor(dividerColor));
            }
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, dividerColor != null ? Theme.dividerExtraPaint : Theme.dividerPaint);
        }
    }
}
