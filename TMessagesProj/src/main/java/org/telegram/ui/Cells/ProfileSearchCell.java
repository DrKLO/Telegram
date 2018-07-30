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
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.Theme;

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

    private boolean drawCount;
    private int lastUnreadCount;
    private int countTop = AndroidUtilities.dp(25);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;
    private int paddingRight;

    private boolean drawCheck;

    private int onlineLeft;
    private StaticLayout onlineLayout;

    private RectF rect = new RectF();

    public ProfileSearchCell(Context context) {
        super(context);

        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(26));
        avatarDrawable = new AvatarDrawable();
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

    public void setPaddingRight(int padding) {
        paddingRight = padding;
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null && chat == null && encryptedChat == null) {
            return;
        }
        if (changed) {
            buildLayout();
        }
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
            dialog_id = ((long) encryptedChat.id) << 32;
            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(16.5f);
        } else {
            if (chat != null) {
                if (chat.id < 0) {
                    dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
                    drawNameBroadcast = true;
                    nameLockTop = AndroidUtilities.dp(28.5f);
                } else {
                    dialog_id = -chat.id;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        drawNameBroadcast = true;
                        nameLockTop = AndroidUtilities.dp(28.5f);
                    } else {
                        drawNameGroup = true;
                        nameLockTop = AndroidUtilities.dp(30);
                    }
                }
                drawCheck = chat.verified;
                if (!LocaleController.isRTL) {
                    nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                } else {
                    nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                    nameLeft = AndroidUtilities.dp(11);
                }
            } else if (user != null) {
                dialog_id = user.id;
                if (!LocaleController.isRTL) {
                    nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                } else {
                    nameLeft = AndroidUtilities.dp(11);
                }
                if (user.bot) {
                    drawNameBot = true;
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                        nameLeft = AndroidUtilities.dp(11);
                    }
                    nameLockTop = AndroidUtilities.dp(16.5f);
                } else {
                    nameLockTop = AndroidUtilities.dp(17);
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
            currentNamePaint = Theme.dialogs_nameEncryptedPaint;
        } else {
            currentNamePaint = Theme.dialogs_namePaint;
        }

        int onlineWidth;
        if (!LocaleController.isRTL) {
            onlineWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14);
        } else {
            onlineWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline);
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

        nameWidth -= paddingRight;
        onlineWidth -= paddingRight;

        if (drawCount) {
            TLRPC.TL_dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
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

        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        if (chat == null || subLabel != null) {
            if (!LocaleController.isRTL) {
                onlineLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                onlineLeft = AndroidUtilities.dp(11);
            }

            CharSequence onlineString = "";
            TextPaint currentOnlinePaint = Theme.dialogs_offlinePaint;

            if (subLabel != null) {
                onlineString = subLabel;
            } else if (user != null) {
                if (user.bot) {
                    onlineString = LocaleController.getString("Bot", R.string.Bot);
                } else if (user.id == 333000 || user.id == 777000) {
                    onlineString = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else {
                    onlineString = LocaleController.formatUserStatus(currentAccount, user);
                    if (user != null && (user.id == UserConfig.getInstance(currentAccount).getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime())) {
                        currentOnlinePaint = Theme.dialogs_onlinePaint;
                        onlineString = LocaleController.getString("Online", R.string.Online);
                    }
                }
            }

            if (savedMessages) {
                onlineLayout = null;
                nameTop = AndroidUtilities.dp(25);
            } else {
                CharSequence onlineStringFinal = TextUtils.ellipsize(onlineString, currentOnlinePaint, onlineWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
                onlineLayout = new StaticLayout(onlineStringFinal, currentOnlinePaint, onlineWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                nameTop = AndroidUtilities.dp(13);
                if (subLabel != null && chat != null) {
                    nameLockTop -= AndroidUtilities.dp(12);
                }
            }
        } else {
            onlineLayout = null;
            nameTop = AndroidUtilities.dp(25);
        }

        int avatarLeft;
        if (!LocaleController.isRTL) {
            avatarLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 13 : 9);
        } else {
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 65 : 61);
        }

        avatarImage.setImageCoords(avatarLeft, AndroidUtilities.dp(10), AndroidUtilities.dp(52), AndroidUtilities.dp(52));

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
            if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                left = onlineLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                    if (widthpx < onlineWidth) {
                        onlineLeft += (onlineWidth - widthpx);
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
            if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                left = onlineLayout.getLineRight(0);
                if (left == onlineWidth) {
                    widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                    if (widthpx < onlineWidth) {
                        onlineLeft -= (onlineWidth - widthpx);
                    }
                }
            }
        }

        if (LocaleController.isRTL) {
            nameLeft += paddingRight;
            onlineLeft += paddingRight;
        }
    }

    public void update(int mask) {
        TLRPC.FileLocation photo = null;
        if (user != null) {
            avatarDrawable.setInfo(user);
            if (savedMessages) {
                avatarDrawable.setSavedMessages(1);
            } else if (user.photo != null) {
                photo = user.photo.photo_small;
            }
        } else if (chat != null) {
            if (chat.photo != null) {
                photo = chat.photo.photo_small;
            }
            avatarDrawable.setInfo(chat);
        } else {
            avatarDrawable.setInfo(0, null, null, false);
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && user != null || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 && chat != null) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null && lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
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
                TLRPC.TL_dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
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
        avatarImage.setImage(photo, "50_50", avatarDrawable, null, 0);

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
                setDrawableBounds(Theme.dialogs_verifiedDrawable, x, nameLockTop);
                setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, x, nameLockTop);
                Theme.dialogs_verifiedDrawable.draw(canvas);
                Theme.dialogs_verifiedCheckDrawable.draw(canvas);
            }
        }

        if (onlineLayout != null) {
            canvas.save();
            canvas.translate(onlineLeft, AndroidUtilities.dp(40));
            onlineLayout.draw(canvas);
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
}
