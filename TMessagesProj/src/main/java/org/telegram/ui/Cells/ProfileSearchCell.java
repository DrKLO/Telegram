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
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.NotificationsSettingsActivity;

public class ProfileSearchCell extends BaseCell {

    private CharSequence currentName;
    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private CharSequence subLabel;

    private TLRPC.User user;
    private TLRPC.Chat chat;
    private TLRPC.EncryptedChat encryptedChat;
    private long dialog_id;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    private boolean savedMessages;

    public boolean useSeparator;

    private int currentAccount = UserConfig.selectedAccount;

    private int nameLeft;
    private int nameTop;
    private StaticLayout nameLayout;
    private boolean drawNameLock;
    private boolean drawNameBroadcast;
    private boolean drawNameGroup;
    private boolean drawNameBot;
    private int nameLockLeft;
    private int nameLockTop;
    private int nameWidth;

    private int sublabelOffsetX;
    private int sublabelOffsetY;

    private boolean drawCount;
    private int lastUnreadCount;
    private int countTop = AndroidUtilities.dp(19);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean drawCheck;

    private int statusLeft;
    private StaticLayout statusLayout;

    private RectF rect = new RectF();

    CheckBox2 checkBox;

    public ProfileSearchCell(Context context) {
        super(context);

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(23));
        avatarDrawable = new AvatarDrawable();

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox);
    }

    public void setData(TLObject object, TLRPC.EncryptedChat ec, CharSequence n, CharSequence s, boolean needCount, boolean saved) {
        currentName = n;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            chat = null;
        } else if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            user = null;
        }
        encryptedChat = ec;
        subLabel = s;
        drawCount = needCount;
        savedMessages = saved;
        update(0);
    }

    public void setException(NotificationsSettingsActivity.NotificationException exception, CharSequence name) {
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
                    setData(user, encryptedChat, name, text, false, false);
                }
            }
        } else if (DialogObject.isUserDialog(exception.did)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(exception.did);
            if (user != null) {
                setData(user, null, name, text, false, false);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-exception.did);
            if (chat != null) {
                setData(chat, null, name, text, false, false);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(60) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null && chat == null && encryptedChat == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(42) : AndroidUtilities.dp(42);
            int y = AndroidUtilities.dp(36);
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        if (changed) {
            buildLayout();
        }
    }

    public TLRPC.User getUser() {
        return user;
    }

    public TLRPC.Chat getChat() {
        return chat;
    }

    public void setSublabelOffset(int x, int y) {
        sublabelOffsetX = x;
        sublabelOffsetY = y;
    }

    public void buildLayout() {
        CharSequence nameString;
        TextPaint currentNamePaint;

        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameGroup = false;
        drawCheck = false;
        drawNameBot = false;

        if (encryptedChat != null) {
            drawNameLock = true;
            dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(22.0f);
        } else {
            if (chat != null) {
                dialog_id = -chat.id;
                if (SharedConfig.drawDialogIcons) {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        drawNameBroadcast = true;
                        nameLockTop = AndroidUtilities.dp(22.5f);
                    } else {
                        drawNameGroup = true;
                        nameLockTop = AndroidUtilities.dp(24);
                    }
                }
                drawCheck = chat.verified;
                if (SharedConfig.drawDialogIcons) {
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                        nameLeft = AndroidUtilities.dp(11);
                    }
                } else {
                    if (!LocaleController.isRTL) {
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    } else {
                        nameLeft = AndroidUtilities.dp(11);
                    }
                }
            } else if (user != null) {
                dialog_id = user.id;
                if (!LocaleController.isRTL) {
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                } else {
                    nameLeft = AndroidUtilities.dp(11);
                }
                if (SharedConfig.drawDialogIcons && user.bot && !MessagesController.isSupportUser(user)) {
                    drawNameBot = true;
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                        nameLeft = AndroidUtilities.dp(11);
                    }
                    nameLockTop = AndroidUtilities.dp(20.5f);
                } else {
                    nameLockTop = AndroidUtilities.dp(21);
                }
                drawCheck = user.verified;
            }
        }

        if (currentName != null) {
            nameString = currentName;
        } else {
            String nameString2 = "";
            if (chat != null) {
                nameString2 = chat.title;
            } else if (user != null) {
                nameString2 = UserObject.getUserName(user);
            }
            nameString = nameString2.replace('\n', ' ');
        }
        if (nameString.length() == 0) {
            if (user != null && user.phone != null && user.phone.length() != 0) {
                nameString = PhoneFormat.getInstance().format("+" + user.phone);
            } else {
                nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
            }
        }
        if (encryptedChat != null) {
            currentNamePaint = Theme.dialogs_searchNameEncryptedPaint;
        } else {
            currentNamePaint = Theme.dialogs_searchNamePaint;
        }

        int statusWidth;
        if (!LocaleController.isRTL) {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14);
        } else {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        } else if (drawNameBroadcast) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_broadcastDrawable.getIntrinsicWidth();
        } else if (drawNameGroup) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_groupDrawable.getIntrinsicWidth();
        } else if (drawNameBot) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_botDrawable.getIntrinsicWidth();
        }

        nameWidth -= getPaddingLeft() + getPaddingRight();
        statusWidth -= getPaddingLeft() + getPaddingRight();

        if (drawCount) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
            if (dialog != null && dialog.unread_count != 0) {
                lastUnreadCount = dialog.unread_count;
                String countString = String.format("%d", dialog.unread_count);
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + AndroidUtilities.dp(18);
                nameWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(19);
                } else {
                    countLeft = AndroidUtilities.dp(19);
                    nameLeft += w;
                }
            } else {
                lastUnreadCount = 0;
                countLayout = null;
            }
        } else {
            lastUnreadCount = 0;
            countLayout = null;
        }

        if (nameWidth < 0) {
            nameWidth = 0;
        }
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        CharSequence statusString = null;
        TextPaint currentStatusPaint = Theme.dialogs_offlinePaint;
        if (!LocaleController.isRTL) {
            statusLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        } else {
            statusLeft = AndroidUtilities.dp(11);
        }

        if (chat == null || subLabel != null) {
            if (subLabel != null) {
                statusString = subLabel;
            } else if (user != null) {
                if (MessagesController.isSupportUser(user)) {
                    statusString = LocaleController.getString("SupportStatus", R.string.SupportStatus);
                } else if (user.bot) {
                    statusString = LocaleController.getString("Bot", R.string.Bot);
                } else if (user.id == 333000 || user.id == 777000) {
                    statusString = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else {
                    statusString = LocaleController.formatUserStatus(currentAccount, user);
                    if (user != null && (user.id == UserConfig.getInstance(currentAccount).getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime())) {
                        currentStatusPaint = Theme.dialogs_onlinePaint;
                        statusString = LocaleController.getString("Online", R.string.Online);
                    }
                }
            }
            if (savedMessages || UserObject.isReplyUser(user)) {
                statusString = null;
                nameTop = AndroidUtilities.dp(20);
            }
        } else {
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralString("Subscribers", chat.participants_count);
                } else {
                    if (TextUtils.isEmpty(chat.username)) {
                        statusString = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                    } else {
                        statusString = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                    }
                }
            } else {
                if (chat.participants_count != 0) {
                    statusString = LocaleController.formatPluralString("Members", chat.participants_count);
                } else {
                    if (chat.has_geo) {
                        statusString = LocaleController.getString("MegaLocation", R.string.MegaLocation);
                    } else if (TextUtils.isEmpty(chat.username)) {
                        statusString = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                    } else {
                        statusString = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                    }
                }
            }
            nameTop = AndroidUtilities.dp(19);
        }

        if (!TextUtils.isEmpty(statusString)) {
            CharSequence statusStringFinal = TextUtils.ellipsize(statusString, currentStatusPaint, statusWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            statusLayout = new StaticLayout(statusStringFinal, currentStatusPaint, statusWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            nameTop = AndroidUtilities.dp(9);
            nameLockTop -= AndroidUtilities.dp(10);
        } else {
            nameTop = AndroidUtilities.dp(20);
            statusLayout = null;
        }

        int avatarLeft;
        if (LocaleController.isRTL) {
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(57) - getPaddingRight();
        } else {
            avatarLeft = AndroidUtilities.dp(11) + getPaddingLeft();
        }

        avatarImage.setImageCoords(avatarLeft, AndroidUtilities.dp(7), AndroidUtilities.dp(46), AndroidUtilities.dp(46));

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft += (statusWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineRight(0);
                if (left == statusWidth) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft -= (statusWidth - widthpx);
                    }
                }
            }
        }

        nameLeft += getPaddingLeft();
        statusLeft += getPaddingLeft();
        nameLockLeft += getPaddingLeft();
    }

    public void update(int mask) {
        TLRPC.FileLocation photo = null;
        if (user != null) {
            avatarDrawable.setInfo(user);
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
            } else if (savedMessages) {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
            } else {
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                avatarImage.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50", avatarDrawable, user, 0);
            }
        } else if (chat != null) {
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
            }
            avatarDrawable.setInfo(chat);
            avatarImage.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", avatarDrawable, chat, 0);
        } else {
            avatarDrawable.setInfo(0, null, null);
            avatarImage.setImage(null, null, avatarDrawable, null, null, 0);
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && user != null || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 && chat != null) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0 && user != null) {
                int newStatus = 0;
                if (user.status != null) {
                    newStatus = user.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && ((mask & MessagesController.UPDATE_MASK_NAME) != 0 && user != null) || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 && chat != null) {
                String newName;
                if (user != null) {
                    newName = user.first_name + user.last_name;
                } else {
                    newName = chat.title;
                }
                if (!newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && drawCount && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
                if (dialog != null && dialog.unread_count != lastUnreadCount) {
                    continueUpdate = true;
                }
            }

            if (!continueUpdate) {
                return;
            }
        }

        if (user != null) {
            if (user.status != null) {
                lastStatus = user.status.expires;
            } else {
                lastStatus = 0;
            }
            lastName = user.first_name + user.last_name;
        } else if (chat != null) {
            lastName = chat.title;
        }

        lastAvatar = photo;

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (user == null && chat == null && encryptedChat == null) {
            return;
        }

        if (useSeparator) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        } else if (drawNameGroup) {
            setDrawableBounds(Theme.dialogs_groupDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_groupDrawable.draw(canvas);
        } else if (drawNameBroadcast) {
            setDrawableBounds(Theme.dialogs_broadcastDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_broadcastDrawable.draw(canvas);
        } else if (drawNameBot) {
            setDrawableBounds(Theme.dialogs_botDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_botDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();
            if (drawCheck) {
                int x;
                if (LocaleController.isRTL) {
                    if (nameLayout.getLineLeft(0) == 0) {
                        x = nameLeft - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
                    } else {
                        float w = nameLayout.getLineWidth(0);
                        x = (int) (nameLeft + nameWidth - Math.ceil(w) - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                    }
                } else {
                    x = (int) (nameLeft + nameLayout.getLineRight(0) + AndroidUtilities.dp(6));
                }
                setDrawableBounds(Theme.dialogs_verifiedDrawable, x, nameTop + AndroidUtilities.dp(3));
                setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, x, nameTop + AndroidUtilities.dp(3));
                Theme.dialogs_verifiedDrawable.draw(canvas);
                Theme.dialogs_verifiedCheckDrawable.draw(canvas);
            }
        }

        if (statusLayout != null) {
            canvas.save();
            canvas.translate(statusLeft + sublabelOffsetX, AndroidUtilities.dp(33) + sublabelOffsetY);
            statusLayout.draw(canvas);
            canvas.restore();
        }

        if (countLayout != null) {
            int x = countLeft - AndroidUtilities.dp(5.5f);
            rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, MessagesController.getInstance(currentAccount).isDialogMuted(dialog_id) ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint);
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }

        avatarImage.draw(canvas);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder builder = new StringBuilder();
        if (nameLayout != null) {
            builder.append(nameLayout.getText());
        }
        if (statusLayout != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(statusLayout.getText());
        }
        info.setText(builder.toString());
    }

    public long getDialogId() {
        return dialog_id;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(checked, animated);
    }
}
