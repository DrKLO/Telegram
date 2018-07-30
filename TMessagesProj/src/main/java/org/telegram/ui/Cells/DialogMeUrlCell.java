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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

public class DialogMeUrlCell extends BaseCell {

    private TLRPC.RecentMeUrl recentMeUrl;

    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    public boolean useSeparator;

    private int nameLeft;
    private StaticLayout nameLayout;
    private boolean drawNameLock;
    private boolean drawNameGroup;
    private boolean drawNameBroadcast;
    private boolean drawNameBot;
    private int nameMuteLeft;
    private int nameLockLeft;
    private int nameLockTop;

    private int messageTop = AndroidUtilities.dp(40);
    private int messageLeft;
    private StaticLayout messageLayout;

    private boolean drawVerified;

    private int avatarTop = AndroidUtilities.dp(10);

    private boolean isSelected;

    private int currentAccount = UserConfig.selectedAccount;

    public DialogMeUrlCell(Context context) {
        super(context);

        Theme.createDialogsResources(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(26));
    }

    public void setRecentMeUrl(TLRPC.RecentMeUrl url) {
        recentMeUrl = url;
        requestLayout();
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            buildLayout();
        }
    }

    public void buildLayout() {
        String nameString = "";
        CharSequence messageString;
        TextPaint currentNamePaint = Theme.dialogs_namePaint;
        TextPaint currentMessagePaint = Theme.dialogs_messagePaint;

        drawNameGroup = false;
        drawNameBroadcast = false;
        drawNameLock = false;
        drawNameBot = false;
        drawVerified = false;

        TLObject image;

        if (recentMeUrl instanceof TLRPC.TL_recentMeUrlChat) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(recentMeUrl.chat_id);
            if (chat.id < 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
                drawNameBroadcast = true;
                nameLockTop = AndroidUtilities.dp(16.5f);
            } else {
                drawNameGroup = true;
                nameLockTop = AndroidUtilities.dp(17.5f);
            }
            drawVerified = chat.verified;

            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                nameLeft = AndroidUtilities.dp(14);
            }
            nameString = chat.title;
            if (chat.photo != null) {
                image = chat.photo.photo_small;
            } else {
                image = null;
            }
            avatarDrawable.setInfo(chat);
        } else if (recentMeUrl instanceof TLRPC.TL_recentMeUrlUser) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(recentMeUrl.user_id);
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(14);
            }
            if (user != null) {
                if (user.bot) {
                    drawNameBot = true;
                    nameLockTop = AndroidUtilities.dp(16.5f);
                    if (!LocaleController.isRTL) {
                        nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                        nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
                    } else {
                        nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - Theme.dialogs_botDrawable.getIntrinsicWidth();
                        nameLeft = AndroidUtilities.dp(14);
                    }
                }
                drawVerified = user.verified;
            }
            nameString = UserObject.getUserName(user);
            if (user.photo != null) {
                image = user.photo.photo_small;
            } else {
                image = null;
            }
            avatarDrawable.setInfo(user);
        } else if (recentMeUrl instanceof TLRPC.TL_recentMeUrlStickerSet) {
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(14);
            }
            nameString = recentMeUrl.set.set.title;
            image = recentMeUrl.set.cover;
            avatarDrawable.setInfo(5, recentMeUrl.set.set.title, null, false);
        } else if (recentMeUrl instanceof TLRPC.TL_recentMeUrlChatInvite) {
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(14);
            }
            if (recentMeUrl.chat_invite.chat != null) {
                avatarDrawable.setInfo(recentMeUrl.chat_invite.chat);
                nameString = recentMeUrl.chat_invite.chat.title;
                if (recentMeUrl.chat_invite.chat.photo != null) {
                    image = recentMeUrl.chat_invite.chat.photo.photo_small;
                } else {
                    image = null;
                }
                if (recentMeUrl.chat_invite.chat.id < 0 || ChatObject.isChannel(recentMeUrl.chat_invite.chat) && !recentMeUrl.chat_invite.chat.megagroup) {
                    drawNameBroadcast = true;
                    nameLockTop = AndroidUtilities.dp(16.5f);
                } else {
                    drawNameGroup = true;
                    nameLockTop = AndroidUtilities.dp(17.5f);
                }
                drawVerified = recentMeUrl.chat_invite.chat.verified;
            } else {
                nameString = recentMeUrl.chat_invite.title;
                image = recentMeUrl.chat_invite.photo.photo_small;
                avatarDrawable.setInfo(5, recentMeUrl.chat_invite.title, null, false);
                if (recentMeUrl.chat_invite.broadcast || recentMeUrl.chat_invite.channel) {
                    drawNameBroadcast = true;
                    nameLockTop = AndroidUtilities.dp(16.5f);
                } else {
                    drawNameGroup = true;
                    nameLockTop = AndroidUtilities.dp(17.5f);
                }
            }
            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - (drawNameGroup ? Theme.dialogs_groupDrawable.getIntrinsicWidth() : Theme.dialogs_broadcastDrawable.getIntrinsicWidth());
                nameLeft = AndroidUtilities.dp(14);
            }
        } else if (recentMeUrl instanceof TLRPC.TL_recentMeUrlUnknown) {
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(14);
            }
            nameString = "Url";
            image = null;
        } else {
            image = null;
        }
        messageString = MessagesController.getInstance(currentAccount).linkPrefix + "/" + recentMeUrl.url;
        avatarImage.setImage(image, "50_50", avatarDrawable, null, 0);

        if (TextUtils.isEmpty(nameString)) {
            nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
        }

        int nameWidth;

        if (!LocaleController.isRTL) {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14);
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        } else if (drawNameGroup) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_groupDrawable.getIntrinsicWidth();
        } else if (drawNameBroadcast) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_broadcastDrawable.getIntrinsicWidth();
        } else if (drawNameBot) {
            nameWidth -= AndroidUtilities.dp(4) + Theme.dialogs_botDrawable.getIntrinsicWidth();
        }

        if (drawVerified) {
            int w = AndroidUtilities.dp(6) + Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }

        nameWidth = Math.max(AndroidUtilities.dp(12), nameWidth);
        try {
            CharSequence nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        int messageWidth = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 16);
        int avatarLeft;
        if (!LocaleController.isRTL) {
            messageLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            avatarLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 13 : 9);
        } else {
            messageLeft = AndroidUtilities.dp(16);
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 65 : 61);
        }
        avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(52), AndroidUtilities.dp(52));

        messageWidth = Math.max(AndroidUtilities.dp(12), messageWidth);
        CharSequence messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, messageWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        try {
            messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                }
                if (left == 0) {
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (messageLayout != null && messageLayout.getLineCount() > 0) {
                left = messageLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(messageLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageLeft += (messageWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
                if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + left + AndroidUtilities.dp(6));
                }
            }
            if (messageLayout != null && messageLayout.getLineCount() > 0) {
                left = messageLayout.getLineRight(0);
                if (left == messageWidth) {
                    widthpx = Math.ceil(messageLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageLeft -= (messageWidth - widthpx);
                    }
                }
            }
        }
    }

    public void setDialogSelected(boolean value) {
        if (isSelected != value) {
            invalidate();
        }
        isSelected = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isSelected) {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_tabletSeletedPaint);
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
            canvas.translate(nameLeft, AndroidUtilities.dp(13));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (messageLayout != null) {
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            try {
                messageLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (drawVerified) {
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f));
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        }

        if (useSeparator) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        avatarImage.draw(canvas);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
