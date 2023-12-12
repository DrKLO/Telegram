/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
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
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;

public class UserCell2 extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;
    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private ImageView imageView;
    private CheckBox checkBox;
    private CheckBoxSquare checkBoxBig;

    private AvatarDrawable avatarDrawable;
    private TLObject currentObject;

    private CharSequence currentName;
    private CharSequence currentStatus;
    private int currentId;
    private int currentDrawable;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    private int currentAccount = UserConfig.selectedAccount;

    private int statusColor;
    private int statusOnlineColor;

    public UserCell2(Context context, int padding, int checkbox) {
        this(context, padding, checkbox, null);
    }

    public UserCell2(Context context, int padding, int checkbox, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        statusColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        statusOnlineColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 7 + padding, 11, LocaleController.isRTL ? 7 + padding : 0, 0));

        nameTextView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), AndroidUtilities.dp(15), false);
                return super.setText(value);
            }
        };
        NotificationCenter.listenEmojiLoading(nameTextView);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTextSize(17);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 + (checkbox == 2 ? 18 : 0) : (68 + padding), 14.5f, LocaleController.isRTL ? (68 + padding) : 28 + (checkbox == 2 ? 18 : 0), 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : (68 + padding), 37.5f, LocaleController.isRTL ? (68 + padding) : 28, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 0 : 16, 0, LocaleController.isRTL ? 16 : 0, 0));

        if (checkbox == 2) {
            checkBoxBig = new CheckBoxSquare(context, false, resourcesProvider);
            addView(checkBoxBig, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 19 : 0, 0, LocaleController.isRTL ? 0 : 19, 0));
        } else if (checkbox == 1) {
            checkBox = new CheckBox(context, R.drawable.round_check2);
            checkBox.setVisibility(INVISIBLE);
            checkBox.setColor(Theme.getColor(Theme.key_checkbox, resourcesProvider), Theme.getColor(Theme.key_checkboxCheck, resourcesProvider));
            addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 37 + padding, 41, LocaleController.isRTL ? 37 + padding : 0, 0));
        }
    }

    public void setData(TLObject object, CharSequence name, CharSequence status, int resId) {
        if (object == null && name == null && status == null) {
            currentStatus = null;
            currentName = null;
            currentObject = null;
            nameTextView.setText("");
            statusTextView.setText("");
            avatarImageView.setImageDrawable(null);
            return;
        }
        currentStatus = status;
        currentName = name;
        currentObject = object;
        currentDrawable = resId;
        update(0);
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
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(70), MeasureSpec.EXACTLY));
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
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
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
        lastAvatar = photo;

        if (currentUser != null) {
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

        if (currentName != null) {
            lastName = null;
            nameTextView.setText(currentName);
        } else {
            if (currentUser != null) {
                lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
            } else {
                lastName = newName == null ? currentChat.title : newName;
            }
            nameTextView.setText(lastName);
        }

        if (currentStatus != null) {
            statusTextView.setTextColor(statusColor);
            statusTextView.setText(currentStatus);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            }
        } else if (currentUser != null) {
            if (currentUser.bot) {
                statusTextView.setTextColor(statusColor);
                if (currentUser.bot_chat_history) {
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
            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentChat != null) {
            statusTextView.setTextColor(statusColor);
            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                if (currentChat.participants_count != 0) {
                    statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentChat.participants_count));
                } else if (!ChatObject.isPublic(currentChat)) {
                    statusTextView.setText(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate));
                } else {
                    statusTextView.setText(LocaleController.getString("ChannelPublic", R.string.ChannelPublic));
                }
            } else {
                if (currentChat.participants_count != 0) {
                    statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participants_count));
                } else if (currentChat.has_geo) {
                    statusTextView.setText(LocaleController.getString("MegaLocation", R.string.MegaLocation));
                } else if (!ChatObject.isPublic(currentChat)) {
                    statusTextView.setText(LocaleController.getString("MegaPrivate", R.string.MegaPrivate));
                } else {
                    statusTextView.setText(LocaleController.getString("MegaPublic", R.string.MegaPublic));
                }
            }
            avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
        } else {
            avatarImageView.setImageDrawable(avatarDrawable);
        }

        avatarImageView.setRoundRadius(currentChat != null && currentChat.forum ? AndroidUtilities.dp(14) : AndroidUtilities.dp(24));

        if (imageView.getVisibility() == VISIBLE && currentDrawable == 0 || imageView.getVisibility() == GONE && currentDrawable != 0) {
            imageView.setVisibility(currentDrawable == 0 ? GONE : VISIBLE);
            imageView.setImageResource(currentDrawable);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
