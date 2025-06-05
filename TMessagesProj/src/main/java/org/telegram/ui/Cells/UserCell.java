/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.StoriesUtilities;

public class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public BackupImageView avatarImageView;
    protected SimpleTextView nameTextView;
    protected SimpleTextView statusTextView;
    private ImageView imageView;
    private CheckBox2 checkBox;
    private CheckBoxSquare checkBoxBig;
    private ImageView checkBox3;
    private TextView adminTextView;
    private TextView addButton;
    private Drawable premiumDrawable;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerification;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatus;
    private ImageView closeView;
    protected Theme.ResourcesProvider resourcesProvider;

    protected AvatarDrawable avatarDrawable;
    private boolean storiable;
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

    public boolean needDivider;
    public StoriesUtilities.AvatarStoryParams storyParams = new StoriesUtilities.AvatarStoryParams(false) {
        @Override
        public void openStory(long dialogId, Runnable onDone) {
            UserCell.this.openStory(dialogId, onDone);
        }
    };

    public void openStory(long dialogId, Runnable runnable) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment != null) {
            fragment.getOrCreateStoryViewer().doOnAnimationReady(runnable);
            fragment.getOrCreateStoryViewer().open(getContext(), dialogId, StoriesListPlaceProvider.of((RecyclerListView) getParent()));
        }
    }

    protected long dialogId;

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
            addButton.setTypeface(AndroidUtilities.bold());
            addButton.setBackgroundDrawable(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
            addButton.setText(getString(R.string.Add));
            addButton.setPadding(dp(17), 0, dp(17), 0);
            addView(addButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 14 : 0, 15, LocaleController.isRTL ? 0 : 14, 0));
            additionalPadding = (int) Math.ceil((addButton.getPaint().measureText(addButton.getText().toString()) + dp(34 + 14)) / AndroidUtilities.density);
        } else {
            additionalPadding = 0;
        }

        statusColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        statusOnlineColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (storiable) {
                    storyParams.originalAvatarRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    StoriesUtilities.drawAvatarWithStory(dialogId, canvas, imageReceiver, storyParams);
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (storyParams.checkOnTouchEvent(event, this)) {
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };
        avatarImageView.setRoundRadius(dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 7 + padding, 6, LocaleController.isRTL ? 7 + padding : 0, 0));
        setClipChildren(false);

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 + (checkbox == 2 ? 18 : 0) + additionalPadding : (64 + padding), 10, LocaleController.isRTL ? (64 + padding) : 28 + (checkbox == 2 ? 18 : 0) + additionalPadding, 0));

        botVerification = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView, dp(20));
        emojiStatus = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView, dp(20));

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
            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 37 + padding, 36, LocaleController.isRTL ? 37 + padding : 0, 0));
        } else if (checkbox == 3) {
            checkBox3 = new ImageView(context);
            checkBox3.setScaleType(ImageView.ScaleType.CENTER);
            checkBox3.setImageResource(R.drawable.account_check);
            checkBox3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            checkBox3.setVisibility(View.GONE);
            addView(checkBox3, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 10 + padding : 0, 0, LocaleController.isRTL ? 0 : 10 + padding, 0));
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
        layoutParams.leftMargin = dp(LocaleController.isRTL ? 0 : 7 + padding);
        layoutParams.rightMargin = dp(LocaleController.isRTL ? 7 + padding : 0);
        avatarImageView.setLayoutParams(layoutParams);

        layoutParams = (LayoutParams) nameTextView.getLayoutParams();
        layoutParams.leftMargin = dp(LocaleController.isRTL ? 28 + (checkBoxBig != null ? 18 : 0) : (64 + padding));
        layoutParams.rightMargin = dp(LocaleController.isRTL ? (64 + padding) : 28 + (checkBoxBig != null ? 18 : 0));

        layoutParams = (FrameLayout.LayoutParams) statusTextView.getLayoutParams();
        layoutParams.leftMargin = dp(LocaleController.isRTL ? 28 : (64 + padding));
        layoutParams.rightMargin = dp(LocaleController.isRTL ? (64 + padding) : 28);

        if (checkBox != null) {
            layoutParams = (FrameLayout.LayoutParams) checkBox.getLayoutParams();
            layoutParams.leftMargin = dp(LocaleController.isRTL ? 0 : 37 + padding);
            layoutParams.rightMargin = dp(LocaleController.isRTL ? 37 + padding : 0);
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
            setRightPadding(size, true, false);
        } else {
            setRightPadding(0, true, false);
        }
    }

    public void setRightPadding(int pad, boolean top, boolean bottom) {
        if (pad > 0) pad += dp(6);
        if (top) nameTextView.setPadding(LocaleController.isRTL ? pad : 0, 0, !LocaleController.isRTL ? pad : 0, 0);
        if (bottom) statusTextView.setPadding(LocaleController.isRTL ? pad : 0, 0, !LocaleController.isRTL ? pad : 0, 0);
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
            storiable = false;
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
                name = Emoji.replaceEmoji(name, nameTextView.getPaint().getFontMetricsInt(), false);
            }
        } catch (Exception ignore) {}
        currentName = name;
        storiable = !(object instanceof String);
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
        if (exception.story) {
            if (exception.notify <= 0 && exception.auto) {
                text = getString(R.string.NotificationEnabledAutomatically);
            } else if (exception.notify <= 0) {
                text = getString(R.string.NotificationEnabled);
            } else {
                text = getString(R.string.NotificationDisabled);
            }
        } else {
            boolean enabled;
            boolean custom = exception.hasCustom;
            int value = exception.notify;
            int delta = exception.muteUntil;
            if (value == 3 && delta != Integer.MAX_VALUE) {
                delta -= ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                if (delta <= 0) {
                    if (custom) {
                        text = getString(R.string.NotificationsCustom);
                    } else {
                        text = getString(R.string.NotificationsUnmuted);
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
                    text = getString(R.string.NotificationsCustom);
                } else {
                    text = getString(enabled ? R.string.NotificationsUnmuted : R.string.NotificationsMuted);
                }
            }
            if (text == null) {
                text = getString(R.string.NotificationsOff);
            }
            if (exception.auto) {
                text += ", Auto";
            }
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
        } else if (checkBox3 != null) {
            checkBox3.setVisibility(checked ? View.VISIBLE : View.GONE);
        }
    }

    public void setCheckDisabled(boolean disabled) {
        if (checkBoxBig != null) {
            checkBoxBig.setDisabled(disabled);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
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
        dialogId = 0;
        if (currentObject instanceof TLRPC.User) {
            currentUser = (TLRPC.User) currentObject;
            if (currentUser.photo != null) {
                photo = currentUser.photo.photo_small;
            }
            dialogId = currentUser.id;
        } else if (currentObject instanceof TLRPC.Chat) {
            currentChat = (TLRPC.Chat) currentObject;
            if (currentChat.photo != null) {
                photo = currentChat.photo.photo_small;
            }
            dialogId = currentChat.id;
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
                    newName = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(UserObject.getUserName(currentUser)));
                } else {
                    newName = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(currentChat == null ? "" : currentChat.title));
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
            ((LayoutParams) nameTextView.getLayoutParams()).topMargin = dp(19);
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
                case "new_chats":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_NEW_CHATS);
                    break;
                case "existing_chats":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_EXISTING_CHATS);
                    break;
            }
            avatarImageView.setImage(null, "50_50", avatarDrawable);
            currentStatus = "";
        } else {
            ((LayoutParams) nameTextView.getLayoutParams()).topMargin = dp(10);
            if (currentUser != null) {
                if (selfAsSavedMessages && UserObject.isUserSelf(currentUser)) {
                    nameTextView.setText(getString(R.string.SavedMessages), true);
                    statusTextView.setText(null);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser);
                    ((LayoutParams) nameTextView.getLayoutParams()).topMargin = dp(19);
                    return;
                }
                avatarDrawable.setInfo(currentAccount, currentUser);
                if (currentUser.status != null) {
                    lastStatus = currentUser.status.expires;
                } else {
                    lastStatus = 0;
                }
            } else if (currentChat != null) {
                avatarDrawable.setInfo(currentAccount, currentChat);
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
                lastName = newName == null ? UserObject.getUserName(currentUser) : AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(newName));
            } else if (currentChat != null) {
                lastName = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(newName == null ? currentChat.title : newName));
            } else {
                lastName = "";
            }
            CharSequence name = lastName;
            if (name != null) {
                try {
                    name = Emoji.replaceEmoji(lastName, nameTextView.getPaint().getFontMetricsInt(), false);
                } catch (Exception ignore) {}
            }
            nameTextView.setText(name);
        }
        long botVerificationIcon = 0;
        if (currentUser != null) {
            botVerificationIcon = DialogObject.getBotVerificationIcon(currentUser);
        } else if (currentChat != null) {
            botVerificationIcon = DialogObject.getBotVerificationIcon(currentChat);
        }
        if (botVerificationIcon == 0) {
            botVerification.set((Drawable) null, false);
            nameTextView.setLeftDrawable(null);
        } else {
            botVerification.set(botVerificationIcon, false);
            botVerification.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
            nameTextView.setLeftDrawable(botVerification);
        }
        if (currentUser != null && MessagesController.getInstance(currentAccount).isPremiumUser(currentUser) && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            if (DialogObject.getEmojiStatusDocumentId(currentUser.emoji_status) != 0) {
                emojiStatus.set(DialogObject.getEmojiStatusDocumentId(currentUser.emoji_status), false);
                emojiStatus.setColor(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider));
                nameTextView.setRightDrawable(emojiStatus);
            } else {
                if (premiumDrawable == null) {
                    premiumDrawable = getContext().getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
                    premiumDrawable = new AnimatedEmojiDrawable.WrapSizeDrawable(premiumDrawable, dp(14), dp(14)) {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            canvas.save();
                            canvas.translate(0, dp(1));
                            super.draw(canvas);
                            canvas.restore();
                        }
                    };
                    premiumDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                }
                nameTextView.setRightDrawable(premiumDrawable);
            }
            nameTextView.setRightDrawableTopPadding(-dp(0.5f));
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
                    statusTextView.setText(getString(R.string.BotStatusRead));
                } else {
                    statusTextView.setText(getString(R.string.BotStatusCantRead));
                }
            } else {
                if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
                    statusTextView.setTextColor(statusOnlineColor);
                    statusTextView.setText(getString(R.string.Online));
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

        avatarImageView.setRoundRadius(currentChat != null && currentChat.forum ? dp(14) : dp(24));

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
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(68) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
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
        botVerification.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        emojiStatus.detach();
        botVerification.detach();
        storyParams.onDetachFromWindow();
    }

    public long getDialogId() {
        return dialogId;
    }

    public void setFromUItem(int currentAccount, UItem item, boolean divider) {
        if (item.chatType != null) {
            setData(item.chatType, item.text, null, 0, divider);
            return;
        }
        long id = item.dialogId;
        if (id > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(id);
            if (user != null) {
                String status;
                if (user.bot) {
                    status = getString(R.string.Bot);
                } else if (user.contact) {
                    status = getString(R.string.FilterContact);
                } else {
                    status = getString(R.string.FilterNonContact);
                }
                setData(user, null, status, 0, divider);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-id);
            if (chat != null) {
                String status;
                if (chat.participants_count != 0) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        status = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                    } else {
                        status = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                    }
                } else if (!ChatObject.isPublic(chat)) {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        status = getString(R.string.ChannelPrivate);
                    } else {
                        status = getString(R.string.MegaPrivate);
                    }
                } else {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        status = getString(R.string.ChannelPublic);
                    } else {
                        status = getString(R.string.MegaPublic);
                    }
                }
                setData(chat, null, status, 0, divider);
            }
        }
    }

    public void setCloseIcon(View.OnClickListener onClick) {
        if (onClick == null) {
            if (closeView != null) {
                removeView(closeView);
                closeView = null;
            }
        } else {
            if (closeView == null) {
                closeView = new ImageView(getContext());
                closeView.setScaleType(ImageView.ScaleType.CENTER);
                ScaleStateListAnimator.apply(closeView);
                closeView.setImageResource(R.drawable.ic_close_white);
                closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider), PorterDuff.Mode.SRC_IN));
                closeView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_AUTO));
                addView(closeView, LayoutHelper.createFrame(30, 30, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 0));
            }
            closeView.setOnClickListener(onClick);
        }
    }
}
