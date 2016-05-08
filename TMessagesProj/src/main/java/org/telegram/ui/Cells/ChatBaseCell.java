/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;

public class ChatBaseCell extends BaseCell implements MediaController.FileDownloadProgressListener {

    public interface ChatBaseCellDelegate {
        void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user);
        void didPressedViaBot(ChatBaseCell cell, String username);
        void didPressedChannelAvatar(ChatBaseCell cell, TLRPC.Chat chat, int postId);
        void didPressedCancelSendButton(ChatBaseCell cell);
        void didLongPressed(ChatBaseCell cell);
        void didPressedReplyMessage(ChatBaseCell cell, int id);
        void didPressedUrl(MessageObject messageObject, ClickableSpan url, boolean longPress);
        void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h);
        void didPressedImage(ChatBaseCell cell);
        void didPressedShare(ChatBaseCell cell);
        void didPressedOther(ChatBaseCell cell);
        void didPressedBotButton(ChatBaseCell cell, TLRPC.KeyboardButton button);
        boolean needPlayAudio(MessageObject messageObject);
        boolean canPerformActions();
    }

    private int TAG;

    public boolean isChat;
    protected boolean isPressed;
    protected boolean forwardName;
    protected boolean isHighlighted;
    protected boolean mediaBackground;
    protected boolean isCheckPressed = true;
    private boolean wasLayout;
    protected boolean isAvatarVisible;
    protected boolean drawBackground = true;
    protected int substractBackgroundHeight;
    protected boolean allowAssistant;
    protected Drawable currentBackgroundDrawable;
    protected MessageObject currentMessageObject;
    private int viaWidth;
    private int viaNameWidth;
    protected int availableTimeWidth;

    protected static TextPaint timePaint;
    private static TextPaint namePaint;
    private static TextPaint forwardNamePaint;
    protected static TextPaint replyNamePaint;
    protected static TextPaint replyTextPaint;
    protected static Paint replyLinePaint;

    protected int backgroundWidth = 100;

    protected int layoutWidth;
    protected int layoutHeight;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean avatarPressed;
    private boolean forwardNamePressed;
    private boolean forwardBotPressed;

    private StaticLayout replyNameLayout;
    private StaticLayout replyTextLayout;
    private ImageReceiver replyImageReceiver;
    private int replyStartX;
    private int replyStartY;
    protected int replyNameWidth;
    private float replyNameOffset;
    protected int replyTextWidth;
    private float replyTextOffset;
    private boolean needReplyImage;
    private boolean replyPressed;
    private TLRPC.FileLocation currentReplyPhoto;

    protected boolean drawShareButton;
    private boolean sharePressed;
    private int shareStartX;
    private int shareStartY;

    private StaticLayout nameLayout;
    protected int nameWidth;
    private float nameOffsetX;
    private float nameX;
    private float nameY;
    protected boolean drawName;
    protected boolean drawNameLayout;

    private StaticLayout[] forwardedNameLayout = new StaticLayout[2];
    protected int forwardedNameWidth;
    protected boolean drawForwardedName;
    private int forwardNameX;
    private int forwardNameY;
    private float forwardNameOffsetX[] = new float[2];

    private StaticLayout timeLayout;
    protected int timeWidth;
    private int timeTextWidth;
    private int timeX;
    private String currentTimeString;
    protected boolean drawTime = true;

    private StaticLayout viewsLayout;
    private int viewsTextWidth;
    private String currentViewsString;

    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.FileLocation currentPhoto;
    private String currentNameString;

    private TLRPC.User currentForwardUser;
    private TLRPC.User currentViaBotUser;
    private TLRPC.Chat currentForwardChannel;
    private String currentForwardNameString;

    protected ChatBaseCellDelegate delegate;

    protected int namesOffset;

    private int lastSendState;
    private int lastDeleteDate;
    private int lastViewsCount;

    public ChatBaseCell(Context context) {
        super(context);
        if (timePaint == null) {
            timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaint.setTextSize(AndroidUtilities.dp(12));

            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            namePaint.setTextSize(AndroidUtilities.dp(14));

            forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            forwardNamePaint.setTextSize(AndroidUtilities.dp(14));

            replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            replyNamePaint.setTextSize(AndroidUtilities.dp(14));

            replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyTextPaint.setTextSize(AndroidUtilities.dp(14));
            replyTextPaint.linkColor = Theme.MSG_LINK_TEXT_COLOR;

            replyLinePaint = new Paint();
        }
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarDrawable = new AvatarDrawable();
        replyImageReceiver = new ImageReceiver(this);
        TAG = MediaController.getInstance().generateObserverTag();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
        replyImageReceiver.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        replyImageReceiver.onAttachedToWindow();
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    public void setDelegate(ChatBaseCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setHighlighted(boolean value) {
        if (isHighlighted == value) {
            return;
        }
        isHighlighted = value;
        invalidate();
    }

    public void setCheckPressed(boolean value, boolean pressed) {
        isCheckPressed = value;
        isPressed = pressed;
        invalidate();
    }

    public void setAllowAssistant(boolean value) {
        allowAssistant = value;
    }

    protected boolean isUserDataChanged() {
        if (currentMessageObject == null || currentUser == null && currentChat == null) {
            return false;
        }
        if (lastSendState != currentMessageObject.messageOwner.send_state) {
            return true;
        }
        if (lastDeleteDate != currentMessageObject.messageOwner.destroyTime) {
            return true;
        }
        if (lastViewsCount != currentMessageObject.messageOwner.views) {
            return true;
        }

        TLRPC.User newUser = null;
        TLRPC.Chat newChat = null;
        if (currentMessageObject.isFromUser()) {
            newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            newChat = MessagesController.getInstance().getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            newChat = MessagesController.getInstance().getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible) {
            if (newUser != null && newUser.photo != null){
                newPhoto = newUser.photo.photo_small;
            } else if (newChat != null && newChat.photo != null) {
                newPhoto = newChat.photo.photo_small;
            }
        }

        if (replyTextLayout == null && currentMessageObject.replyMessageObject != null) {
            return true;
        }

        if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id)) {
            return true;
        }

        TLRPC.FileLocation newReplyPhoto = null;

        if (currentMessageObject.replyMessageObject != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.replyMessageObject.photoThumbs, 80);
            if (photoSize != null && currentMessageObject.replyMessageObject.type != 13) {
                newReplyPhoto = photoSize.location;
            }
        }

        if (currentReplyPhoto == null && newReplyPhoto != null) {
            return true;
        }

        String newNameString = null;
        if (drawName && isChat && !currentMessageObject.isOutOwner()) {
            if (newUser != null) {
                newNameString = UserObject.getUserName(newUser);
            } else if (newChat != null) {
                newNameString = newChat.title;
            }
        }

        if (currentNameString == null && newNameString != null || currentNameString != null && newNameString == null || currentNameString != null && newNameString != null && !currentNameString.equals(newNameString)) {
            return true;
        }

        if (drawForwardedName) {
            newNameString = currentMessageObject.getForwardedName();
            return currentForwardNameString == null && newNameString != null || currentForwardNameString != null && newNameString == null || currentForwardNameString != null && newNameString != null && !currentForwardNameString.equals(newNameString);
        }
        return false;
    }

    protected void measureTime(MessageObject messageObject) {
        boolean hasSign = !messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0 && messageObject.messageOwner.post;
        TLRPC.User signUser = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        if (hasSign && signUser == null) {
            hasSign = false;
        }
        if (hasSign) {
            currentTimeString = ", " + LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        } else {
            currentTimeString = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        }
        timeTextWidth = timeWidth = (int) Math.ceil(timePaint.measureText(currentTimeString));
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            currentViewsString = String.format("%s", LocaleController.formatShortNumber(Math.max(1, messageObject.messageOwner.views), null));
            viewsTextWidth = (int) Math.ceil(timePaint.measureText(currentViewsString));
            timeWidth += viewsTextWidth + Theme.viewsCountDrawable[0].getIntrinsicWidth() + AndroidUtilities.dp(10);
        }
        if (hasSign) {
            if (availableTimeWidth == 0) {
                availableTimeWidth = AndroidUtilities.dp(1000);
            }
            CharSequence name = ContactsController.formatName(signUser.first_name, signUser.last_name).replace('\n', ' ');
            int widthForSign = availableTimeWidth - timeWidth;
            int width = (int) Math.ceil(timePaint.measureText(name, 0, name.length()));
            if (width > widthForSign) {
                name = TextUtils.ellipsize(name, timePaint, widthForSign, TextUtils.TruncateAt.END);
                width = widthForSign;
            }
            currentTimeString = name + currentTimeString;
            timeTextWidth += width;
            timeWidth += width;
        }
    }

    protected boolean checkNeedDrawShareButton(MessageObject messageObject) {
        if (messageObject.isFromUser()) {
            TLRPC.User user = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
            if (user != null && user.bot && messageObject.type != 13 && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty || messageObject.messageOwner.media == null
                    || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && !(messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage))) {
                return true;
            }
        } else if (messageObject.messageOwner.from_id < 0 || messageObject.messageOwner.post) {
            if (messageObject.messageOwner.to_id.channel_id != 0 && (messageObject.messageOwner.via_bot_id == 0 && messageObject.messageOwner.reply_to_msg_id == 0 || messageObject.type != 13)) {
                return true;
            }
        }
        return false;
    }

    public void setMessageObject(MessageObject messageObject) {
        currentMessageObject = messageObject;
        lastSendState = messageObject.messageOwner.send_state;
        lastDeleteDate = messageObject.messageOwner.destroyTime;
        lastViewsCount = messageObject.messageOwner.views;
        isPressed = false;
        isCheckPressed = true;
        isAvatarVisible = false;
        wasLayout = false;
        drawShareButton = checkNeedDrawShareButton(messageObject);
        replyNameLayout = null;
        replyTextLayout = null;
        replyNameWidth = 0;
        replyTextWidth = 0;
        viaWidth = 0;
        viaNameWidth = 0;
        currentReplyPhoto = null;
        currentUser = null;
        currentChat = null;
        currentViaBotUser = null;
        drawNameLayout = false;

        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            if (currentMessageObject.isContentUnread() && !currentMessageObject.isOut()) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, false);
                currentMessageObject.setContentIsRead();
            } else if (!currentMessageObject.viewsReloaded) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, true);
                currentMessageObject.viewsReloaded = true;
            }
        }

        if (currentMessageObject.isFromUser()) {
            currentUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            currentChat = MessagesController.getInstance().getChat(-currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.post) {
            currentChat = MessagesController.getInstance().getChat(currentMessageObject.messageOwner.to_id.channel_id);
        }

        if (isChat && !messageObject.isOutOwner() && messageObject.isFromUser()) {
            isAvatarVisible = true;
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    currentPhoto = currentUser.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentUser);
            } else if (currentChat != null) {
                if (currentChat.photo != null) {
                    currentPhoto = currentChat.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentChat);
            } else {
                currentPhoto = null;
                avatarDrawable.setInfo(messageObject.messageOwner.from_id, null, null, false);
            }
            avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, null, false);
        }


        measureTime(messageObject);

        namesOffset = 0;

        String viaUsername = null;
        CharSequence viaString = null;
        if (messageObject.messageOwner.via_bot_id != 0) {
            TLRPC.User botUser = MessagesController.getInstance().getUser(messageObject.messageOwner.via_bot_id);
            if (botUser != null && botUser.username != null && botUser.username.length() > 0) {
                viaUsername = "@" + botUser.username;
                viaString = AndroidUtilities.replaceTags(String.format(" via <b>%s</b>", viaUsername));
                viaWidth = (int) Math.ceil(replyNamePaint.measureText(viaString, 0, viaString.length()));
                currentViaBotUser = botUser;
            }
        } else if (messageObject.messageOwner.via_bot_name != null && messageObject.messageOwner.via_bot_name.length() > 0) {
            viaUsername = "@" + messageObject.messageOwner.via_bot_name;
            viaString = AndroidUtilities.replaceTags(String.format(" via <b>%s</b>", viaUsername));
            viaWidth = (int) Math.ceil(replyNamePaint.measureText(viaString, 0, viaString.length()));
        }

        boolean authorName = drawName && isChat && !currentMessageObject.isOutOwner();
        boolean viaBot = (messageObject.messageOwner.fwd_from == null || messageObject.type == 14) && viaUsername != null;
        if (authorName || viaBot) {
            drawNameLayout = true;
            nameWidth = getMaxNameWidth();
            if (nameWidth < 0) {
                nameWidth = AndroidUtilities.dp(100);
            }

            if (authorName) {
                if (currentUser != null) {
                    currentNameString = UserObject.getUserName(currentUser);
                } else if (currentChat != null) {
                    currentNameString = currentChat.title;
                } else {
                    currentNameString = "DELETED";
                }
            } else {
                currentNameString = "";
            }
            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace('\n', ' '), namePaint, nameWidth - (viaBot ? viaWidth : 0), TextUtils.TruncateAt.END);
            if (viaBot) {
                viaNameWidth = (int) Math.ceil(namePaint.measureText(nameStringFinal, 0, nameStringFinal.length()));
                if (viaNameWidth != 0) {
                    viaNameWidth += AndroidUtilities.dp(4);
                }
                int color;
                if (currentMessageObject.type == 13) {
                    color = Theme.MSG_STICKER_VIA_BOT_NAME_TEXT_COLOR;
                } else {
                    color = currentMessageObject.isOutOwner() ? Theme.MSG_OUT_VIA_BOT_NAME_TEXT_COLOR : Theme.MSG_IN_VIA_BOT_NAME_TEXT_COLOR;
                }
                if (currentNameString.length() > 0) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s via %s", nameStringFinal, viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), nameStringFinal.length() + 1, nameStringFinal.length() + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), nameStringFinal.length() + 5, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                } else {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("via %s", viaUsername));
                    stringBuilder.setSpan(new TypefaceSpan(Typeface.DEFAULT, 0, color), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, color), 4, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                }
                nameStringFinal = TextUtils.ellipsize(nameStringFinal, namePaint, nameWidth, TextUtils.TruncateAt.END);
            }
            try {
                nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (nameLayout != null && nameLayout.getLineCount() > 0) {
                    nameWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
                    if (messageObject.type != 13) {
                        namesOffset += AndroidUtilities.dp(19);
                    }
                    nameOffsetX = nameLayout.getLineLeft(0);
                } else {
                    nameWidth = 0;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (currentNameString.length() == 0) {
                currentNameString = null;
            }
        } else {
            currentNameString = null;
            nameLayout = null;
            nameWidth = 0;
        }

        currentForwardUser = null;
        currentForwardNameString = null;
        forwardedNameLayout[0] = null;
        forwardedNameLayout[1] = null;
        forwardedNameWidth = 0;
        if (drawForwardedName && messageObject.isForwarded()) {
            currentForwardChannel = null;
            if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                currentForwardChannel = MessagesController.getInstance().getChat(messageObject.messageOwner.fwd_from.channel_id);
            }
            if (messageObject.messageOwner.fwd_from.from_id != 0) {
                currentForwardUser = MessagesController.getInstance().getUser(messageObject.messageOwner.fwd_from.from_id);
            }

            if (currentForwardUser != null || currentForwardChannel != null) {
                if (currentForwardChannel != null) {
                    if (currentForwardUser != null) {
                        currentForwardNameString = String.format("%s (%s)", currentForwardChannel.title, UserObject.getUserName(currentForwardUser));
                    } else {
                        currentForwardNameString = currentForwardChannel.title;
                    }
                } else if (currentForwardUser != null) {
                    currentForwardNameString = UserObject.getUserName(currentForwardUser);
                }

                forwardedNameWidth = getMaxNameWidth();
                int fromWidth = (int) Math.ceil(forwardNamePaint.measureText(LocaleController.getString("From", R.string.From) + " "));
                CharSequence name = TextUtils.ellipsize(currentForwardNameString.replace('\n', ' '), replyNamePaint, forwardedNameWidth - fromWidth - viaWidth, TextUtils.TruncateAt.END);
                CharSequence lastLine;
                if (viaString != null) {
                    viaNameWidth = (int) Math.ceil(forwardNamePaint.measureText(LocaleController.getString("From", R.string.From) + " " + name));
                    lastLine = AndroidUtilities.replaceTags(String.format("%s <b>%s</b> via <b>%s</b>", LocaleController.getString("From", R.string.From), name, viaUsername));
                } else {
                    lastLine = AndroidUtilities.replaceTags(String.format("%s <b>%s</b>", LocaleController.getString("From", R.string.From), name));
                }
                lastLine = TextUtils.ellipsize(lastLine, forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                try {
                    forwardedNameLayout[1] = new StaticLayout(lastLine, forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    lastLine = TextUtils.ellipsize(AndroidUtilities.replaceTags(LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage)), forwardNamePaint, forwardedNameWidth, TextUtils.TruncateAt.END);
                    forwardedNameLayout[0] = new StaticLayout(lastLine, forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout[0].getLineWidth(0)), (int) Math.ceil(forwardedNameLayout[1].getLineWidth(0)));
                    forwardNameOffsetX[0] = forwardedNameLayout[0].getLineLeft(0);
                    forwardNameOffsetX[1] = forwardedNameLayout[1].getLineLeft(0);
                    namesOffset += AndroidUtilities.dp(36);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        if (messageObject.isReply()) {
            namesOffset += AndroidUtilities.dp(42);
            if (messageObject.type != 0) {
                if (messageObject.type == 13) {
                    namesOffset -= AndroidUtilities.dp(42);
                } else {
                    namesOffset += AndroidUtilities.dp(5);
                }
            }

            int maxWidth = getMaxNameWidth();
            if (messageObject.type != 13) {
                maxWidth -= AndroidUtilities.dp(10);
            }

            CharSequence stringFinalName = null;
            CharSequence stringFinalText = null;
            if (messageObject.replyMessageObject != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 80);
                if (photoSize == null || messageObject.replyMessageObject.type == 13 || messageObject.type == 13 && !AndroidUtilities.isTablet() || messageObject.replyMessageObject.isSecretMedia()) {
                    replyImageReceiver.setImageBitmap((Drawable) null);
                    needReplyImage = false;
                } else {
                    currentReplyPhoto = photoSize.location;
                    replyImageReceiver.setImage(photoSize.location, "50_50", null, null, true);
                    needReplyImage = true;
                    maxWidth -= AndroidUtilities.dp(44);
                }

                String name = null;
                if (messageObject.replyMessageObject.isFromUser()) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObject.replyMessageObject.messageOwner.from_id);
                    if (user != null) {
                        name = UserObject.getUserName(user);
                    }
                } else if (messageObject.replyMessageObject.messageOwner.from_id < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(-messageObject.replyMessageObject.messageOwner.from_id);
                    if (chat != null) {
                        name = chat.title;
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(messageObject.replyMessageObject.messageOwner.to_id.channel_id);
                    if (chat != null) {
                        name = chat.title;
                    }
                }

                if (name != null) {
                    stringFinalName = TextUtils.ellipsize(name.replace('\n', ' '), replyNamePaint, maxWidth, TextUtils.TruncateAt.END);
                }
                if (messageObject.replyMessageObject.messageText != null && messageObject.replyMessageObject.messageText.length() > 0) {
                    String mess = messageObject.replyMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace('\n', ' ');
                    stringFinalText = Emoji.replaceEmoji(mess, replyTextPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, replyTextPaint, maxWidth, TextUtils.TruncateAt.END);
                }
            }
            if (stringFinalName == null) {
                stringFinalName = LocaleController.getString("Loading", R.string.Loading);
            }
            try {
                replyNameLayout = new StaticLayout(stringFinalName, replyNamePaint, maxWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (replyNameLayout.getLineCount() > 0) {
                    replyNameWidth = (int)Math.ceil(replyNameLayout.getLineWidth(0)) + AndroidUtilities.dp(12 + (needReplyImage ? 44 : 0));
                    replyNameOffset = replyNameLayout.getLineLeft(0);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                if (stringFinalText != null) {
                    replyTextLayout = new StaticLayout(stringFinalText, replyTextPaint, maxWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    if (replyTextLayout.getLineCount() > 0) {
                        replyTextWidth = (int) Math.ceil(replyTextLayout.getLineWidth(0)) + AndroidUtilities.dp(12 + (needReplyImage ? 44 : 0));
                        replyTextOffset = replyTextLayout.getLineLeft(0);
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        requestLayout();
    }

    public final MessageObject getMessageObject() {
        return currentMessageObject;
    }

    protected int getMaxNameWidth() {
        return backgroundWidth - AndroidUtilities.dp(mediaBackground ? 22 : 31);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate == null || delegate.canPerformActions()) {
                if (isAvatarVisible && avatarImage.isInsideImage(x, y)) {
                    avatarPressed = true;
                    result = true;
                } else if (drawForwardedName && forwardedNameLayout[0] != null && x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32)) {
                    if (viaWidth != 0 && x >= forwardNameX + viaNameWidth + AndroidUtilities.dp(4)) {
                        forwardBotPressed = true;
                    } else {
                        forwardNamePressed = true;
                    }
                    result = true;
                } else if (drawNameLayout && nameLayout != null && viaWidth != 0 && x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4) && y <= nameY + AndroidUtilities.dp(20)) {
                    forwardBotPressed = true;
                    result = true;
                } else if (currentMessageObject.isReply() && x >= replyStartX && x <= replyStartX + Math.max(replyNameWidth, replyTextWidth) && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35)) {
                    replyPressed = true;
                    result = true;
                } else if (drawShareButton && x >= shareStartX && x <= shareStartX + AndroidUtilities.dp(40) && y >= shareStartY && y <= shareStartY + AndroidUtilities.dp(32)) {
                    sharePressed = true;
                    result = true;
                    invalidate();
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (avatarPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    avatarPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        if (currentUser != null) {
                            delegate.didPressedUserAvatar(this, currentUser);
                        } else if (currentChat != null) {
                            delegate.didPressedChannelAvatar(this, currentChat, 0);
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    avatarPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (isAvatarVisible && !avatarImage.isInsideImage(x, y)) {
                        avatarPressed = false;
                    }
                }
            } else if (forwardNamePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    forwardNamePressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        if (currentForwardChannel != null) {
                            delegate.didPressedChannelAvatar(this, currentForwardChannel, currentMessageObject.messageOwner.fwd_from.channel_post);
                        } else if (currentForwardUser != null) {
                            delegate.didPressedUserAvatar(this, currentForwardUser);
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    forwardNamePressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                        forwardNamePressed = false;
                    }
                }
            } else if (forwardBotPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    forwardBotPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedViaBot(this, currentViaBotUser != null ? currentViaBotUser.username : currentMessageObject.messageOwner.via_bot_name);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    forwardBotPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (drawForwardedName && forwardedNameLayout[0] != null) {
                        if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                            forwardBotPressed = false;
                        }
                    } else {
                        if (!(x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4) && y <= nameY + AndroidUtilities.dp(20))) {
                            forwardBotPressed = false;
                        }
                    }
                }
            } else if (replyPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    replyPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedReplyMessage(this, currentMessageObject.messageOwner.reply_to_msg_id);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    replyPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= replyStartX && x <= replyStartX + Math.max(replyNameWidth, replyTextWidth) && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35))) {
                        replyPressed = false;
                    }
                }
            } else if (sharePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    sharePressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedShare(this);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    sharePressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= shareStartX && x <= shareStartX + AndroidUtilities.dp(40) && y >= shareStartY && y <= shareStartY + AndroidUtilities.dp(32))) {
                        sharePressed = false;
                    }
                }
                invalidate();
            }
        }
        return result;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        if (changed || !wasLayout) {
            layoutWidth = getMeasuredWidth();
            layoutHeight = getMeasuredHeight() - substractBackgroundHeight;
            if (timeTextWidth < 0) {
                timeTextWidth = AndroidUtilities.dp(10);
            }
            timeLayout = new StaticLayout(currentTimeString, timePaint, timeTextWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!mediaBackground) {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth + (isChat && currentMessageObject.isFromUser() ? AndroidUtilities.dp(48) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
                }
            } else {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(4) - timeWidth + (isChat && currentMessageObject.isFromUser() ? AndroidUtilities.dp(48) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(42.0f);
                }
            }

            if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                viewsLayout = new StaticLayout(currentViewsString, timePaint, viewsTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } else {
                viewsLayout = null;
            }

            if (isAvatarVisible) {
                avatarImage.setImageCoords(AndroidUtilities.dp(6), layoutHeight - AndroidUtilities.dp(44), AndroidUtilities.dp(42), AndroidUtilities.dp(42));
            }

            wasLayout = true;
        }
    }

    protected void drawContent(Canvas canvas) {

    }

    public ImageReceiver getPhotoImage() {
        return null;
    }

    @Override
    protected void onLongPress() {
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    protected boolean isDrawSelectedBackground() {
        return isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }

        if (!wasLayout) {
            requestLayout();
            return;
        }

        if (isAvatarVisible) {
            avatarImage.draw(canvas);
        }

        if (mediaBackground) {
            timePaint.setColor(Theme.MSG_MEDIA_TIME_TEXT_COLOR);
        } else {
            if (currentMessageObject.isOutOwner()) {
                timePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_TIME_SELECTED_TEXT_COLOR : Theme.MSG_OUT_TIME_TEXT_COLOR);
            } else {
                timePaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_TIME_SELECTED_TEXT_COLOR : Theme.MSG_IN_TIME_TEXT_COLOR);
            }
        }

        if (currentMessageObject.isOutOwner()) {
            if (isDrawSelectedBackground()) {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableOutSelected;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableOutSelected;
                }
            } else {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableOut;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableOut;
                }
            }
            setDrawableBounds(currentBackgroundDrawable, layoutWidth - backgroundWidth - (!mediaBackground ? 0 : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth - (mediaBackground ? 0 : AndroidUtilities.dp(3)), layoutHeight - AndroidUtilities.dp(2));
        } else {
            if (isDrawSelectedBackground()) {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableInSelected;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableInSelected;
                }
            } else {
                if (!mediaBackground) {
                    currentBackgroundDrawable = Theme.backgroundDrawableIn;
                } else {
                    currentBackgroundDrawable = Theme.backgroundMediaDrawableIn;
                }
            }
            if (isChat && currentMessageObject.isFromUser()) {
                setDrawableBounds(currentBackgroundDrawable, AndroidUtilities.dp(48 + (!mediaBackground ? 3 : 9)), AndroidUtilities.dp(1), backgroundWidth - (mediaBackground ? 0 : AndroidUtilities.dp(3)), layoutHeight - AndroidUtilities.dp(2));
            } else {
                setDrawableBounds(currentBackgroundDrawable, (!mediaBackground ? AndroidUtilities.dp(3) : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth - (mediaBackground ? 0 : AndroidUtilities.dp(3)), layoutHeight - AndroidUtilities.dp(2));
            }
        }
        if (drawBackground && currentBackgroundDrawable != null) {
            currentBackgroundDrawable.draw(canvas);
        }

        drawContent(canvas);

        if (drawShareButton) {
            Theme.shareDrawable.setColorFilter(sharePressed ? Theme.colorPressedFilter : Theme.colorFilter);
            setDrawableBounds(Theme.shareDrawable, shareStartX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(8), shareStartY = layoutHeight - AndroidUtilities.dp(41));
            Theme.shareDrawable.draw(canvas);
            setDrawableBounds(Theme.shareIconDrawable, shareStartX + AndroidUtilities.dp(9), shareStartY + AndroidUtilities.dp(9));
            Theme.shareIconDrawable.draw(canvas);
        }

        if (drawNameLayout && nameLayout != null) {
            canvas.save();

            if (currentMessageObject.type == 13) {
                namePaint.setColor(Theme.MSG_STICKER_NAME_TEXT_COLOR);
                int backWidth;
                if (currentMessageObject.isOutOwner()) {
                    nameX = AndroidUtilities.dp(28);
                } else {
                    nameX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(22);
                }
                nameY = layoutHeight - AndroidUtilities.dp(38);
                Theme.systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.systemDrawable.setBounds((int) nameX - AndroidUtilities.dp(12), (int) nameY - AndroidUtilities.dp(5), (int) nameX + AndroidUtilities.dp(12) + nameWidth, (int) nameY + AndroidUtilities.dp(22));
                Theme.systemDrawable.draw(canvas);
            } else {
                if (mediaBackground || currentMessageObject.isOutOwner()) {
                    nameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11) - nameOffsetX;
                } else {
                    nameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(17) - nameOffsetX;
                }
                if (currentUser != null) {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(currentUser.id));
                } else if (currentChat != null) {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(currentChat.id));
                } else {
                    namePaint.setColor(AvatarDrawable.getNameColorForId(0));
                }
                nameY = AndroidUtilities.dp(10);
            }
            canvas.translate(nameX, nameY);
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null) {
            forwardNameY = AndroidUtilities.dp(10 + (drawNameLayout ? 19 : 0));
            if (currentMessageObject.isOutOwner()) {
                forwardNamePaint.setColor(Theme.MSG_OUT_FORDWARDED_NAME_TEXT_COLOR);
                forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
            } else {
                forwardNamePaint.setColor(Theme.MSG_IN_FORDWARDED_NAME_TEXT_COLOR);
                if (mediaBackground) {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
                } else {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(17);
                }
            }
            for (int a = 0; a < 2; a++) {
                canvas.save();
                canvas.translate(forwardNameX - forwardNameOffsetX[a], forwardNameY + AndroidUtilities.dp(16) * a);
                forwardedNameLayout[a].draw(canvas);
                canvas.restore();
            }
        }

        if (currentMessageObject.isReply()) {
            if (currentMessageObject.type == 13) {
                replyLinePaint.setColor(Theme.MSG_STICKER_REPLY_LINE_COLOR);
                replyNamePaint.setColor(Theme.MSG_STICKER_REPLY_NAME_TEXT_COLOR);
                replyTextPaint.setColor(Theme.MSG_STICKER_REPLY_MESSAGE_TEXT_COLOR);
                if (currentMessageObject.isOutOwner()) {
                    replyStartX = AndroidUtilities.dp(23);
                } else {
                    replyStartX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(17);
                }
                replyStartY = layoutHeight - AndroidUtilities.dp(58);
                if (nameLayout != null) {
                    replyStartY -= AndroidUtilities.dp(25 + 6);
                }
                int backWidth = Math.max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(14 + (needReplyImage ? 44 : 0));
                Theme.systemDrawable.setColorFilter(Theme.colorFilter);
                Theme.systemDrawable.setBounds(replyStartX - AndroidUtilities.dp(7), replyStartY - AndroidUtilities.dp(6), replyStartX - AndroidUtilities.dp(7) + backWidth, replyStartY + AndroidUtilities.dp(41));
                Theme.systemDrawable.draw(canvas);
            } else {
                if (currentMessageObject.isOutOwner()) {
                    replyLinePaint.setColor(Theme.MSG_OUT_REPLY_LINE_COLOR);
                    replyNamePaint.setColor(Theme.MSG_OUT_REPLY_NAME_TEXT_COLOR);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0) {
                        replyTextPaint.setColor(Theme.MSG_OUT_REPLY_MESSAGE_TEXT_COLOR);
                    } else {
                        replyTextPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_OUT_REPLY_MEDIA_MESSAGE_SELETED_TEXT_COLOR : Theme.MSG_OUT_REPLY_MEDIA_MESSAGE_TEXT_COLOR);
                    }
                    replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(12);
                } else {
                    replyLinePaint.setColor(Theme.MSG_IN_REPLY_LINE_COLOR);
                    replyNamePaint.setColor(Theme.MSG_IN_REPLY_NAME_TEXT_COLOR);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0) {
                        replyTextPaint.setColor(Theme.MSG_IN_REPLY_MESSAGE_TEXT_COLOR);
                    } else {
                        replyTextPaint.setColor(isDrawSelectedBackground() ? Theme.MSG_IN_REPLY_MEDIA_MESSAGE_SELETED_TEXT_COLOR : Theme.MSG_IN_REPLY_MEDIA_MESSAGE_TEXT_COLOR);
                    }
                    if (mediaBackground) {
                        replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(12);
                    } else {
                        replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(18);
                    }
                }
                replyStartY = AndroidUtilities.dp(12 + (drawForwardedName && forwardedNameLayout[0] != null ? 36 : 0) + (drawNameLayout && nameLayout != null ? 20 : 0));
            }
            canvas.drawRect(replyStartX, replyStartY, replyStartX + AndroidUtilities.dp(2), replyStartY + AndroidUtilities.dp(35), replyLinePaint);
            if (needReplyImage) {
                replyImageReceiver.setImageCoords(replyStartX + AndroidUtilities.dp(10), replyStartY, AndroidUtilities.dp(35), AndroidUtilities.dp(35));
                replyImageReceiver.draw(canvas);
            }

            if (replyNameLayout != null) {
                canvas.save();
                canvas.translate(replyStartX - replyNameOffset + AndroidUtilities.dp(10 + (needReplyImage ? 44 : 0)), replyStartY);
                replyNameLayout.draw(canvas);
                canvas.restore();
            }
            if (replyTextLayout != null) {
                canvas.save();
                canvas.translate(replyStartX - replyTextOffset + AndroidUtilities.dp(10 + (needReplyImage ? 44 : 0)), replyStartY + AndroidUtilities.dp(19));
                replyTextLayout.draw(canvas);
                canvas.restore();
            }
        }

        if (drawTime || !mediaBackground) {
            if (mediaBackground) {
                Drawable drawable;
                if (currentMessageObject.type == 13) {
                    drawable = Theme.timeStickerBackgroundDrawable;
                } else {
                    drawable = Theme.timeBackgroundDrawable;
                }
                setDrawableBounds(drawable, timeX - AndroidUtilities.dp(4), layoutHeight - AndroidUtilities.dp(27), timeWidth + AndroidUtilities.dp(8 + (currentMessageObject.isOutOwner() ? 20 : 0)), AndroidUtilities.dp(17));
                drawable.draw(canvas);

                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.clockMediaDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(13.0f) - Theme.clockMediaDrawable.getIntrinsicHeight());
                            Theme.clockMediaDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.errorDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(12.5f) - Theme.errorDrawable.getIntrinsicHeight());
                            Theme.errorDrawable.draw(canvas);
                        }
                    } else {
                        Drawable countDrawable = Theme.viewsMediaCountDrawable;
                        setDrawableBounds(countDrawable, timeX, layoutHeight - AndroidUtilities.dp(9.5f) - timeLayout.getHeight());
                        countDrawable.draw(canvas);

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + countDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(11.3f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(11.3f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
            } else {
                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            Drawable clockDrawable = Theme.clockChannelDrawable[isDrawSelectedBackground() ? 1 : 0];
                            setDrawableBounds(clockDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(8.5f) - clockDrawable.getIntrinsicHeight());
                            clockDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.errorDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(6.5f) - Theme.errorDrawable.getIntrinsicHeight());
                            Theme.errorDrawable.draw(canvas);
                        }
                    } else {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(Theme.viewsCountDrawable[isDrawSelectedBackground() ? 1 : 0], timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                            Theme.viewsCountDrawable[isDrawSelectedBackground() ? 1 : 0].draw(canvas);
                        } else {
                            setDrawableBounds(Theme.viewsOutCountDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                            Theme.viewsOutCountDrawable.draw(canvas);
                        }

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + Theme.viewsOutCountDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
                //canvas.drawRect(timeX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight(), timeX + availableTimeWidth, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight(), timePaint);
            }

            if (currentMessageObject.isOutOwner()) {
                boolean drawCheck1 = false;
                boolean drawCheck2 = false;
                boolean drawClock = false;
                boolean drawError = false;
                boolean isBroadcast = (int)(currentMessageObject.getDialogId() >> 32) == 1;

                if (currentMessageObject.isSending()) {
                    drawCheck1 = false;
                    drawCheck2 = false;
                    drawClock = true;
                    drawError = false;
                } else if (currentMessageObject.isSendError()) {
                    drawCheck1 = false;
                    drawCheck2 = false;
                    drawClock = false;
                    drawError = true;
                } else if (currentMessageObject.isSent()) {
                    if (!currentMessageObject.isUnread()) {
                        drawCheck1 = true;
                        drawCheck2 = true;
                    } else {
                        drawCheck1 = false;
                        drawCheck2 = true;
                    }
                    drawClock = false;
                    drawError = false;
                }

                if (drawClock) {
                    if (!mediaBackground) {
                        setDrawableBounds(Theme.clockDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - Theme.clockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - Theme.clockDrawable.getIntrinsicHeight());
                        Theme.clockDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(Theme.clockMediaDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - Theme.clockMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - Theme.clockMediaDrawable.getIntrinsicHeight());
                        Theme.clockMediaDrawable.draw(canvas);
                    }
                }
                if (isBroadcast) {
                    if (drawCheck1 || drawCheck2) {
                        if (!mediaBackground) {
                            setDrawableBounds(Theme.broadcastDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - Theme.broadcastDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - Theme.broadcastDrawable.getIntrinsicHeight());
                            Theme.broadcastDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.broadcastMediaDrawable, layoutWidth - AndroidUtilities.dp(24.0f) - Theme.broadcastMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - Theme.broadcastMediaDrawable.getIntrinsicHeight());
                            Theme.broadcastMediaDrawable.draw(canvas);
                        }
                    }
                } else {
                    if (drawCheck2) {
                        if (!mediaBackground) {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.checkDrawable, layoutWidth - AndroidUtilities.dp(22.5f) - Theme.checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - Theme.checkDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.checkDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - Theme.checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - Theme.checkDrawable.getIntrinsicHeight());
                            }
                            Theme.checkDrawable.draw(canvas);
                        } else {
                            if (drawCheck1) {
                                setDrawableBounds(Theme.checkMediaDrawable, layoutWidth - AndroidUtilities.dp(26.3f) - Theme.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - Theme.checkMediaDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(Theme.checkMediaDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - Theme.checkMediaDrawable.getIntrinsicHeight());
                            }
                            Theme.checkMediaDrawable.draw(canvas);
                        }
                    }
                    if (drawCheck1) {
                        if (!mediaBackground) {
                            setDrawableBounds(Theme.halfCheckDrawable, layoutWidth - AndroidUtilities.dp(18) - Theme.halfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - Theme.halfCheckDrawable.getIntrinsicHeight());
                            Theme.halfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(Theme.halfCheckMediaDrawable, layoutWidth - AndroidUtilities.dp(21.5f) - Theme.halfCheckMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - Theme.halfCheckMediaDrawable.getIntrinsicHeight());
                            Theme.halfCheckMediaDrawable.draw(canvas);
                        }
                    }
                }
                if (drawError) {
                    if (!mediaBackground) {
                        setDrawableBounds(Theme.errorDrawable, layoutWidth - AndroidUtilities.dp(18) - Theme.errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(7) - Theme.errorDrawable.getIntrinsicHeight());
                        Theme.errorDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(Theme.errorDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - Theme.errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(11.5f) - Theme.errorDrawable.getIntrinsicHeight());
                        Theme.errorDrawable.draw(canvas);
                    }
                }
            }
        }
    }

    @Override
    public void onFailedDownload(String fileName) {

    }

    @Override
    public void onSuccessDownload(String fileName) {

    }

    @Override
    public void onProgressDownload(String fileName, float progress) {

    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
