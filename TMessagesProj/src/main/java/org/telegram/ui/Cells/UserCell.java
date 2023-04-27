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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.NotificationsSettingsActivity;

public class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private ImageView imageView;
    private CheckBox checkBox;
    private CheckBoxSquare checkBoxBig;
    private TextView adminTextView;
    private TextView addButton;
    private Drawable premiumDrawable;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatus;
    private Theme.ResourcesProvider resourcesProvider;

    private AvatarDrawable avatarDrawable;
    private Object currentObject;
    private TLRPC.EncryptedChat encryptedChat;

    private CharSequence currentName;
    private CharSequence currentStatus;
    private int currentId;
    private int currentDrawable;

    private boolean selfAsSavedMessages;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    private int currentAccount = UserConfig.selectedAccount;

    private int statusColor;
    private int statusOnlineColor;

    private boolean needDivider;

    public UserCell(Context context, int padding, int checkbox, boolean admin) {
        this(context, padding, checkbox, admin, false, null);
    }

    public UserCell(Context context, int padding, int checkbox, boolean admin, Theme.ResourcesProvider resourcesProvider) {
        this(context, padding, checkbox, admin, false, resourcesProvider);
    }

    public UserCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton) {
        this(context, padding, checkbox, admin, needAddButton, null);
    }

    public UserCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        int additionalPadding;
        if (needAddButton) {
            addButton = new TextView(context);
            addButton.setGravity(Gravity.CENTER);
            addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            addButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addButton.setBackgroundDrawable(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
            addButton.setText(LocaleController.getString("Add", R.string.Add));
            addButton.setPadding(AndroidUtilities.dp(17), 0, AndroidUtilities.dp(17), 0);
            addView(addButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 14 : 0, 15, LocaleController.isRTL ? 0 : 14, 0));
            additionalPadding = (int) Math.ceil((addButton.getPaint().measureText(addButton.getText().toString()) + AndroidUtilities.dp(34 + 14)) / AndroidUtilities.density);
        } else {
            additionalPadding = 0;
        }

        statusColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        statusOnlineColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 7 + padding, 6, LocaleController.isRTL ? 7 + padding : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 + (checkbox == 2 ? 18 : 0) + additionalPadding : (64 + padding), 10, LocaleController.isRTL ? (64 + padding) : 28 + (checkbox == 2 ? 18 : 0) + additionalPadding, 0));

        emojiStatus = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView, AndroidUtilities.dp(20));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 + additionalPadding : (64 + padding), 32, LocaleController.isRTL ? (64 + padding) : 28 + additionalPadding, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 0 : 16, 0, LocaleController.isRTL ? 16 : 0, 0));

        if (checkbox == 2) {
            checkBoxBig = new CheckBoxSquare(context, false);
            addView(checkBoxBig, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 19 : 0, 0, LocaleController.isRTL ? 0 : 19, 0));
        } else if (checkbox == 1) {
            checkBox = new CheckBox(context, R.drawable.round_check2);
            checkBox.setVisibility(INVISIBLE);
            checkBox.setColor(Theme.getColor(Theme.key_checkbox, resourcesProvider), Theme.getColor(Theme.key_checkboxCheck, resourcesProvider));
            addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 37 + padding, 40, LocaleController.isRTL ? 37 + padding : 0, 0));
        }

        if (admin) {
            adminTextView = new TextView(context);
            adminTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            adminTextView.setTextColor(Theme.getColor(Theme.key_profile_creatorIcon, resourcesProvider));
            addView(adminTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 23 : 0, 10, LocaleController.isRTL ? 0 : 23, 0));
        }

        setFocusable(true);
    }

    public void setAvatarPadding(int padding) {
        LayoutParams layoutParams = (LayoutParams) avatarImageView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 7 + padding);
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 7 + padding : 0);
        avatarImageView.setLayoutParams(layoutParams);

        layoutParams = (LayoutParams) nameTextView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 28 + (checkBoxBig != null ? 18 : 0) : (64 + padding));
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? (64 + padding) : 28 + (checkBoxBig != null ? 18 : 0));

        layoutParams = (FrameLayout.LayoutParams) statusTextView.getLayoutParams();
        layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 28 : (64 + padding));
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? (64 + padding) : 28);

        if (checkBox != null) {
            layoutParams = (FrameLayout.LayoutParams) checkBox.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 37 + padding);
            layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 37 + padding : 0);
        }
    }

    public void setAddButtonVisible(boolean value) {
        if (addButton == null) {
            return;
        }
        addButton.setVisibility(value ? VISIBLE : GONE);
    }

    public void setAdminRole(String role) {
        if (adminTextView == null) {
            return;
        }
        adminTextView.setVisibility(role != null ? VISIBLE : GONE);
        adminTextView.setText(role);
        if (role != null) {
            CharSequence text = adminTextView.getText();
            int size = (int) Math.ceil(adminTextView.getPaint().measureText(text, 0, text.length()));
            nameTextView.setPadding(LocaleController.isRTL ? size + AndroidUtilities.dp(6) : 0, 0, !LocaleController.isRTL ? size + AndroidUtilities.dp(6) : 0, 0);
        } else {
            nameTextView.setPadding(0, 0, 0, 0);
        }
    }

    public CharSequence getName() {
        return nameTextView.getText();
    }

    public void setData(Object object, CharSequence name, CharSequence status, int resId) {
        setData(object, null, name, status, resId, false);
    }

    public void setData(Object object, CharSequence name, CharSequence status, int resId, boolean divider) {
        setData(object, null, name, status, resId, divider);
    }

    public void setData(Object object, TLRPC.EncryptedChat ec, CharSequence name, CharSequence status, int resId, boolean divider) {
        if (object == null && name == null && status == null) {
            currentStatus = null;
            currentName = null;
            currentObject = null;
            nameTextView.setText("");
            statusTextView.setText("");
            avatarImageView.setImageDrawable(null);
            return;
        }
        encryptedChat = ec;
        currentStatus = status;
        try {
            if (name != null && nameTextView != null) {
                name = Emoji.replaceEmoji(name, nameTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);
            }
        } catch (Exception ignore) {}
        currentName = name;
        currentObject = object;
        currentDrawable = resId;
        needDivider = divider;
        setWillNotDraw(!needDivider);
        update(0);
    }

    public Object getCurrentObject() {
        return currentObject;
    }

    public void setException(NotificationsSettingsActivity.NotificationException exception, CharSequence name, boolean divider) {
        String text;
        boolean enabled;
        boolean custom = exception.hasCustom;
        int value = exception.notify;
        int delta = exception.muteUntil;
        if (value == 3 && delta != Integer.MAX_VALUE) {
            delta -= ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (delta <= 0) {
                if (custom) {
                    text = LocaleController.getString("NotificationsCustom", R.string.NotificationsCustom);
                } else {
                    text = LocaleController.getString("NotificationsUnmuted", R.string.NotificationsUnmuted);
                }
            } else if (delta < 60 * 60) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
            } else if (delta < 60 * 60 * 24) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
            } else if (delta < 60 * 60 * 24 * 365) {
                text = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
            } else {
                text = null;
            }
        } else {
            if (value == 0) {
                enabled = true;
            } else if (value == 1) {
                enabled = true;
            } else if (value == 2) {
                enabled = false;
            } else {
                enabled = false;
            }
            if (enabled && custom) {
                text = LocaleController.getString("NotificationsCustom", R.string.NotificationsCustom);
            } else {
                text = enabled ? LocaleController.getString("NotificationsUnmuted", R.string.NotificationsUnmuted) : LocaleController.getString("NotificationsMuted", R.string.NotificationsMuted);
            }
        }
        if (text == null) {
            text = LocaleController.getString("NotificationsOff", R.string.NotificationsOff);
        }

        if (DialogObject.isEncryptedDialog(exception.did)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(exception.did));
            if (encryptedChat != null) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                if (user != null) {
                    setData(user, encryptedChat, name, text, 0, false);
                }
            }
        } else if (DialogObject.isUserDialog(exception.did)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(exception.did);
            if (user != null) {
                setData(user, null, name, text, 0, divider);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-exception.did);
            if (chat != null) {
                setData(chat, null, name, text, 0, divider);
            }
        }
    }

    public void setNameTypeface(Typeface typeface) {
        nameTextView.setTypeface(typeface);
    }

    public void setCurrentId(int id) {
        currentId = id;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            if (checkBox.getVisibility() != VISIBLE) {
                checkBox.setVisibility(VISIBLE);
            }
            checkBox.setChecked(checked, animated);
        } else if (checkBoxBig != null) {
            if (checkBoxBig.getVisibility() != VISIBLE) {
                checkBoxBig.setVisibility(VISIBLE);
            }
            checkBoxBig.setChecked(checked, animated);
        }
    }

    public void setCheckDisabled(boolean disabled) {
        if (checkBoxBig != null) {
            checkBoxBig.setDisabled(disabled);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setStatusColors(int color, int onlineColor) {
        statusColor = color;
        statusOnlineColor = onlineColor;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (checkBoxBig != null) {
            checkBoxBig.invalidate();
        }
    }

    public void update(int mask) {
        TLRPC.FileLocation photo = null;
        String newName = null;
        TLRPC.User currentUser = null;
        TLRPC.Chat currentChat = null;
        if (currentObject instanceof TLRPC.User) {
            currentUser = (TLRPC.User) currentObject;
            if (currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }
        } else if (currentObject instanceof TLRPC.Chat) {
            currentChat = (TLRPC.Chat) currentObject;
            if (currentChat.photo != null) {
                photo = currentChat.photo.photo_small;
            }
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                    continueUpdate = true;
                }
            }
            if (currentUser != null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int newStatus = 0;
                if (currentUser.status != null) {
                    newStatus = currentUser.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                if (currentUser != null) {
                    newName = UserObject.getUserName(currentUser);
                } else {
                    newName = currentChat.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate) {
                return;
            }
        }

        if (currentObject instanceof String) {
            ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
            String str = (String) currentObject;
            switch (str) {
                case "contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                    break;
                case "non_contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS);
                    break;
                case "groups":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                    break;
                case "channels":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                    break;
                case "bots":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_BOTS);
                    break;
                case "muted":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_MUTED);
                    break;
                case "read":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_READ);
                    break;
                case "archived":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED);
                    break;
            }
            avatarImageView.setImage(null, "50_50", avatarDrawable);
            currentStatus = "";
        } else {
            ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
            if (currentUser != null) {
                if (selfAsSavedMessages && UserObject.isUserSelf(currentUser)) {
                    nameTextView.setText(LocaleController.getString("SavedMessages", R.string.SavedMessages), true);
                    statusTextView.setText(null);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser);
                    ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
                    return;
                }
                avatarDrawable.setInfo(currentUser);
                if (currentUser.status != null) {
                    lastStatus = currentUser.status.expires;
                } else {
                    lastStatus = 0;
                }
            } else if (currentChat != null) {
                avatarDrawable.setInfo(currentChat);
            } else if (currentName != null) {
                avatarDrawable.setInfo(currentId, currentName.toString(), null);
            } else {
                avatarDrawable.setInfo(currentId, "#", null);
            }
        }

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName);
        } else {
            if (currentUser != null) {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
            } else if (currentChat != null) {
                lastName = newName == null ? currentChat.title : newName;
            } else {
                lastName = "";
            }
            CharSequence name = lastName;
            if (name != null) {
                try {
                    name = Emoji.replaceEmoji(lastName, nameTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);
                } catch (Exception ignore) {}
            }
            nameTextView.setText(name);
        }
        if (currentUser != null && MessagesController.getInstance(currentAccount).isPremiumUser(currentUser)) {
            if (currentUser.emoji_status instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) currentUser.emoji_status).until > (int) (System.currentTimeMillis() / 1000)) {
                emojiStatus.set(((TLRPC.TL_emojiStatusUntil) currentUser.emoji_status).document_id, false);
                emojiStatus.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
                nameTextView.setRightDrawable(emojiStatus);
            } else if (currentUser.emoji_status instanceof TLRPC.TL_emojiStatus) {
                emojiStatus.set(((TLRPC.TL_emojiStatus) currentUser.emoji_status).document_id, false);
                emojiStatus.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
                nameTextView.setRightDrawable(emojiStatus);
            } else {
                if (premiumDrawable == null) {
                    premiumDrawable = getContext().getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
                    premiumDrawable = new AnimatedEmojiDrawable.WrapSizeDrawable(premiumDrawable, AndroidUtilities.dp(14), AndroidUtilities.dp(14)) {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            canvas.save();
                            canvas.translate(0, AndroidUtilities.dp(1));
                            super.draw(canvas);
                            canvas.restore();
                        }
                    };
                    premiumDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                }
                nameTextView.setRightDrawable(premiumDrawable);
            }
            nameTextView.setRightDrawableTopPadding(-AndroidUtilities.dp(0.5f));
        } else {
            nameTextView.setRightDrawable(null);
            nameTextView.setRightDrawableTopPadding(0);
        }
        if (currentStatus != null) {
            statusTextView.setTextColor(statusColor);
            statusTextView.setText(currentStatus);
        } else if (currentUser != null) {
            if (currentUser.bot) {
                statusTextView.setTextColor(statusColor);
                if (currentUser.bot_chat_history || adminTextView != null && adminTextView.getVisibility() == VISIBLE) {
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

        if (imageView.getVisibility() == VISIBLE && currentDrawable == 0 || imageView.getVisibility() == GONE && currentDrawable != 0) {
            imageView.setVisibility(currentDrawable == 0 ? GONE : VISIBLE);
            imageView.setImageResource(currentDrawable);
        }

        lastAvatar = photo;
        if (currentUser != null) {
            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentChat != null) {
            avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
        } else {
            avatarImageView.setImageDrawable(avatarDrawable);
        }

        avatarImageView.setRoundRadius(currentChat != null && currentChat.forum ? AndroidUtilities.dp(14) : AndroidUtilities.dp(24));

        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        if (adminTextView != null) {
            adminTextView.setTextColor(Theme.getColor(Theme.key_profile_creatorIcon, resourcesProvider));
        }
    }

    public void setSelfAsSavedMessages(boolean value) {
        selfAsSavedMessages = value;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (checkBoxBig != null && checkBoxBig.getVisibility() == VISIBLE) {
            info.setCheckable(true);
            info.setChecked(checkBoxBig.isChecked());
            info.setClassName("android.widget.CheckBox");
        } else if (checkBox != null && checkBox.getVisibility() == VISIBLE) {
            info.setCheckable(true);
            info.setChecked(checkBox.isChecked());
            info.setClassName("android.widget.CheckBox");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            nameTextView.invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        emojiStatus.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        emojiStatus.detach();
    }
}
