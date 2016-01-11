/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.TypefaceSpan;

public class ChatBaseCell extends BaseCell implements MediaController.FileDownloadProgressListener {

    public interface ChatBaseCellDelegate {
        void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user);
        void didPressedViaBot(ChatBaseCell cell, TLRPC.User user);
        void didPressedChannelAvatar(ChatBaseCell cell, TLRPC.Chat chat);
        void didPressedCancelSendButton(ChatBaseCell cell);
        void didLongPressed(ChatBaseCell cell);
        void didPressReplyMessage(ChatBaseCell cell, int id);
        void didPressUrl(MessageObject messageObject, ClickableSpan url, boolean longPress);
        void needOpenWebView(String url, String title, String originalUrl, int w, int h);
        void didClickedImage(ChatBaseCell cell);
        void didPressShare(ChatBaseCell cell);
        boolean canPerformActions();
    }

    protected ClickableSpan pressedLink;
    protected boolean linkPreviewPressed;
    protected LinkPath urlPath = new LinkPath();
    protected static Paint urlPaint;
    private int TAG;

    public boolean isChat;
    protected boolean isPressed;
    protected boolean forwardName;
    protected boolean isHighlighted;
    protected boolean media;
    protected boolean isCheckPressed = true;
    private boolean wasLayout;
    protected boolean isAvatarVisible;
    protected boolean drawBackground = true;
    protected boolean allowAssistant;
    protected MessageObject currentMessageObject;
    private int viaWidth;
    private int viaNameWidth;

    private static TextPaint timePaint;
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

    private boolean drawShareButton;
    private boolean sharePressed;
    private int shareStartX;
    private int shareStartY;

    private StaticLayout nameLayout;
    protected int nameWidth;
    private float nameOffsetX;
    private float nameX;
    protected boolean drawName;

    private StaticLayout forwardedNameLayout;
    protected int forwardedNameWidth;
    protected boolean drawForwardedName;
    private int forwardNameX;
    private int forwardNameY;
    private float forwardNameOffsetX;

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
            namePaint.setTextSize(AndroidUtilities.dp(15));

            forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            forwardNamePaint.setTextSize(AndroidUtilities.dp(14));

            replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            replyNamePaint.setTextSize(AndroidUtilities.dp(14));

            replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            replyTextPaint.setTextSize(AndroidUtilities.dp(14));
            replyTextPaint.linkColor = 0xff316f9f;

            replyLinePaint = new Paint();

            urlPaint = new Paint();
            urlPaint.setColor(0x33316f9f);
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

    protected void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
        }
        linkPreviewPressed = false;
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
        if (currentMessageObject.messageOwner.from_id > 0) {
            newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        } else if (currentMessageObject.messageOwner.from_id < 0) {
            newChat = MessagesController.getInstance().getChat(-currentMessageObject.messageOwner.from_id);
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
        currentTimeString = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        timeTextWidth = timeWidth = (int) Math.ceil(timePaint.measureText(currentTimeString));
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            currentViewsString = String.format("%s", LocaleController.formatShortNumber(Math.max(1, messageObject.messageOwner.views), null));
            timeWidth += (int) Math.ceil(timePaint.measureText(currentViewsString)) + ResourceLoader.viewsCountDrawable.getIntrinsicWidth() + AndroidUtilities.dp(10);
        }
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
        drawShareButton = false;
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

        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            if (currentMessageObject.isContentUnread() && !currentMessageObject.isOut()) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, false);
                currentMessageObject.setContentIsRead();
            } else if (!currentMessageObject.viewsReloaded) {
                MessagesController.getInstance().addToViewsQueue(currentMessageObject.messageOwner, true);
                currentMessageObject.viewsReloaded = true;
            }
        }

        if (messageObject.messageOwner.from_id > 0) {
            currentUser = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        } else if (messageObject.messageOwner.from_id < 0) {
            currentChat = MessagesController.getInstance().getChat(-messageObject.messageOwner.from_id);
            if (messageObject.messageOwner.to_id.channel_id != 0 && (messageObject.messageOwner.reply_to_msg_id == 0 || messageObject.type != 13)) {
                drawShareButton = true;
            }
        }
        if (isChat && !messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0) {
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

        currentTimeString = LocaleController.getInstance().formatterDay.format((long) (messageObject.messageOwner.date) * 1000);
        timeTextWidth = timeWidth = (int)Math.ceil(timePaint.measureText(currentTimeString));
        if ((messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
            currentViewsString = String.format("%s", LocaleController.formatShortNumber(Math.max(1, messageObject.messageOwner.views), null));
            viewsTextWidth = (int) Math.ceil(timePaint.measureText(currentViewsString));
            timeWidth += viewsTextWidth + ResourceLoader.viewsCountDrawable.getIntrinsicWidth() + AndroidUtilities.dp(10);
        }

        namesOffset = 0;

        String viaUsername = null;
        String viaString = null;
        if (messageObject.messageOwner.via_bot_id != 0) {
            TLRPC.User botUser = MessagesController.getInstance().getUser(messageObject.messageOwner.via_bot_id);
            if (botUser != null && botUser.username != null && botUser.username.length() > 0) {
                viaUsername = "@" + botUser.username;
                viaString = " via " + viaUsername;
                viaWidth = (int) Math.ceil(forwardNamePaint.measureText(viaString));
                currentViaBotUser = botUser;
            }
        }

        boolean authorName = drawName && isChat && !currentMessageObject.isOutOwner();
        boolean viaBot = messageObject.messageOwner.fwd_from_id == null && currentViaBotUser != null;
        if (authorName || viaBot) {
            drawName = true;
            nameWidth = getMaxNameWidth();
            if (nameWidth < 0) {
                nameWidth = AndroidUtilities.dp(100);
            }

            if (authorName || !currentMessageObject.isOutOwner()) {
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

            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace("\n", " "), namePaint, nameWidth - AndroidUtilities.dp(12) - (viaBot ? viaWidth : 0), TextUtils.TruncateAt.END);
            if (viaBot) {
                viaNameWidth = (int) Math.ceil(namePaint.measureText(nameStringFinal, 0, nameStringFinal.length()));
                if (viaNameWidth != 0) {
                    viaNameWidth += AndroidUtilities.dp(4);
                }
                if (currentNameString.length() > 0) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("%s via @%s", nameStringFinal, currentViaBotUser.username));
                    stringBuilder.setSpan(new TypefaceSpan(null, 0, currentMessageObject.isOutOwner() ? 0xff4a923c : 0xff006fc8), nameStringFinal.length() + 1, nameStringFinal.length() + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, currentMessageObject.isOutOwner() ? 0xff4a923c : 0xff006fc8), nameStringFinal.length() + 5, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                } else {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format("via @%s", currentViaBotUser.username));
                    stringBuilder.setSpan(new TypefaceSpan(null, 0, currentMessageObject.isOutOwner() ? 0xff4a923c : 0xff006fc8), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), 0, currentMessageObject.isOutOwner() ? 0xff4a923c : 0xff006fc8), 4, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameStringFinal = stringBuilder;
                }
            }
            try {
                nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (nameLayout != null && nameLayout.getLineCount() > 0) {
                    nameWidth = (int)Math.ceil(nameLayout.getLineWidth(0));
                    namesOffset += AndroidUtilities.dp(19);
                    nameOffsetX = nameLayout.getLineLeft(0);
                } else {
                    nameWidth = 0;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            currentNameString = null;
            nameLayout = null;
            nameWidth = 0;
        }

        if (drawForwardedName && messageObject.isForwarded()) {
            if (messageObject.messageOwner.fwd_from_id instanceof TLRPC.TL_peerChannel) {
                currentForwardChannel = MessagesController.getInstance().getChat(messageObject.messageOwner.fwd_from_id.channel_id);
                currentForwardUser = null;
            } else if (messageObject.messageOwner.fwd_from_id instanceof TLRPC.TL_peerUser) {
                currentForwardChannel = null;
                currentForwardUser = MessagesController.getInstance().getUser(messageObject.messageOwner.fwd_from_id.user_id);
            }

            if (currentForwardUser != null || currentForwardChannel != null) {
                if (currentForwardUser != null) {
                    currentForwardNameString = UserObject.getUserName(currentForwardUser);
                } else {
                    currentForwardNameString = currentForwardChannel.title;
                }

                forwardedNameWidth = getMaxNameWidth();

                CharSequence str = TextUtils.ellipsize(currentForwardNameString.replace("\n", " "), forwardNamePaint, forwardedNameWidth - AndroidUtilities.dp(40) - viaWidth, TextUtils.TruncateAt.END);
                if (viaString != null) {
                    viaNameWidth = (int) Math.ceil(forwardNamePaint.measureText(LocaleController.getString("From", R.string.From) + " " + str));
                    str = AndroidUtilities.replaceTags(String.format("%s\n%s <b>%s</b> via <b>%s</b>", LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage), LocaleController.getString("From", R.string.From), str, viaUsername));
                } else {
                    str = AndroidUtilities.replaceTags(String.format("%s\n%s <b>%s</b>", LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage), LocaleController.getString("From", R.string.From), str));
                }
                forwardedNameLayout = StaticLayoutEx.createStaticLayout(str, forwardNamePaint, forwardedNameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, forwardedNameWidth, 2);
                if (forwardedNameLayout.getLineCount() > 1) {
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout.getLineWidth(0)), (int) Math.ceil(forwardedNameLayout.getLineWidth(1)));
                    namesOffset += AndroidUtilities.dp(36);
                    forwardNameOffsetX = Math.min(forwardedNameLayout.getLineLeft(0), forwardedNameLayout.getLineLeft(1));
                } else {
                    forwardedNameWidth = 0;
                }
            } else {
                currentForwardNameString = null;
                forwardedNameLayout = null;
                forwardedNameWidth = 0;
            }
        } else {
            currentForwardNameString = null;
            forwardedNameLayout = null;
            forwardedNameWidth = 0;
        }

        if (messageObject.isReply()) {
            namesOffset += AndroidUtilities.dp(42);
            if (messageObject.contentType == 2 || messageObject.contentType == 3) {
                namesOffset += AndroidUtilities.dp(4);
            } else if (messageObject.contentType == 1) {
                if (messageObject.type == 13) {
                    namesOffset -= AndroidUtilities.dp(42);
                } else {
                    namesOffset += AndroidUtilities.dp(5);
                }
            }

            int maxWidth;
            if (messageObject.type == 13) {
                int width;
                if (AndroidUtilities.isTablet()) {
                    if (AndroidUtilities.isSmallTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        width = AndroidUtilities.displaySize.x;
                    } else {
                        int leftWidth = AndroidUtilities.displaySize.x / 100 * 35;
                        if (leftWidth < AndroidUtilities.dp(320)) {
                            leftWidth = AndroidUtilities.dp(320);
                        }
                        width = AndroidUtilities.displaySize.x - leftWidth;
                    }
                } else {
                    width = AndroidUtilities.displaySize.x;
                }

                if (messageObject.isOutOwner()) {
                    maxWidth = width - backgroundWidth - AndroidUtilities.dp(60);
                } else {
                    maxWidth = width - backgroundWidth - AndroidUtilities.dp(56 + (isChat && messageObject.messageOwner.from_id > 0 ? 61 : 0));
                }
            } else {
                maxWidth = getMaxNameWidth() - AndroidUtilities.dp(22);
            }
            if (!media && messageObject.contentType != 0) {
                maxWidth -= AndroidUtilities.dp(8);
            }

            CharSequence stringFinalName = null;
            CharSequence stringFinalText = null;
            if (messageObject.replyMessageObject != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject.photoThumbs, 80);
                if (photoSize == null || messageObject.replyMessageObject.type == 13 || messageObject.type == 13 && !AndroidUtilities.isTablet()) {
                    replyImageReceiver.setImageBitmap((Drawable) null);
                    needReplyImage = false;
                } else {
                    currentReplyPhoto = photoSize.location;
                    replyImageReceiver.setImage(photoSize.location, "50_50", null, null, true);
                    needReplyImage = true;
                    maxWidth -= AndroidUtilities.dp(44);
                }

                String name = null;
                if (messageObject.replyMessageObject.messageOwner.from_id > 0) {
                    TLRPC.User user = MessagesController.getInstance().getUser(messageObject.replyMessageObject.messageOwner.from_id);
                    if (user != null) {
                        name = UserObject.getUserName(user);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(-messageObject.replyMessageObject.messageOwner.from_id);
                    if (chat != null) {
                        name = chat.title;
                    }
                }

                if (name != null) {
                    stringFinalName = TextUtils.ellipsize(name.replace("\n", " "), replyNamePaint, maxWidth - AndroidUtilities.dp(8), TextUtils.TruncateAt.END);
                }
                if (messageObject.replyMessageObject.messageText != null && messageObject.replyMessageObject.messageText.length() > 0) {
                    String mess = messageObject.replyMessageObject.messageText.toString();
                    if (mess.length() > 150) {
                        mess = mess.substring(0, 150);
                    }
                    mess = mess.replace("\n", " ");
                    stringFinalText = Emoji.replaceEmoji(mess, replyTextPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    stringFinalText = TextUtils.ellipsize(stringFinalText, replyTextPaint, maxWidth - AndroidUtilities.dp(8), TextUtils.TruncateAt.END);
                }
            }
            if (stringFinalName == null) {
                stringFinalName = LocaleController.getString("Loading", R.string.Loading);
            }
            try {
                replyNameLayout = new StaticLayout(stringFinalName, replyNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (replyNameLayout.getLineCount() > 0) {
                    replyNameWidth = (int)Math.ceil(replyNameLayout.getLineWidth(0)) + AndroidUtilities.dp(12 + (needReplyImage ? 44 : 0));
                    replyNameOffset = replyNameLayout.getLineLeft(0);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                if (stringFinalText != null) {
                    replyTextLayout = new StaticLayout(stringFinalText, replyTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
        return backgroundWidth - AndroidUtilities.dp(8);
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
                } else if (drawForwardedName && forwardedNameLayout != null && x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32)) {
                    if (viaWidth != 0 && x >= forwardNameX + viaNameWidth + AndroidUtilities.dp(4)) {
                        forwardBotPressed = true;
                    } else {
                        forwardNamePressed = true;
                    }
                    result = true;
                } else if (drawName && nameLayout != null && viaWidth != 0 && x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= AndroidUtilities.dp(6) && y <= AndroidUtilities.dp(30)) {
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
                            delegate.didPressedChannelAvatar(this, currentChat);
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
                        if (currentForwardUser != null) {
                            delegate.didPressedUserAvatar(this, currentForwardUser);
                        } else {
                            delegate.didPressedChannelAvatar(this, currentForwardChannel);
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
                        delegate.didPressedViaBot(this, currentViaBotUser);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    forwardBotPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (drawForwardedName && forwardedNameLayout != null) {
                        if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                            forwardBotPressed = false;
                        }
                    } else {
                        if (!(x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= AndroidUtilities.dp(6) && y <= AndroidUtilities.dp(30))) {
                            forwardBotPressed = false;
                        }
                    }
                }
            } else if (replyPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    replyPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressReplyMessage(this, currentMessageObject.messageOwner.reply_to_msg_id);
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
                        delegate.didPressShare(this);
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
            layoutHeight = getMeasuredHeight();

            timeLayout = new StaticLayout(currentTimeString, timePaint, timeTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!media) {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth + (isChat && currentMessageObject.messageOwner.from_id > 0 ? AndroidUtilities.dp(52) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
                }
            } else {
                if (!currentMessageObject.isOutOwner()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(4) - timeWidth + (isChat && currentMessageObject.messageOwner.from_id > 0 ? AndroidUtilities.dp(52) : 0);
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
                avatarImage.setImageCoords(AndroidUtilities.dp(6), layoutHeight - AndroidUtilities.dp(45), AndroidUtilities.dp(42), AndroidUtilities.dp(42));
            }

            wasLayout = true;
        }
    }

    protected void onAfterBackgroundDraw(Canvas canvas) {

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

        if (media) {
            timePaint.setColor(0xffffffff);
        } else {
            if (currentMessageObject.isOutOwner()) {
                timePaint.setColor(0xff70b15c);
            } else {
                timePaint.setColor(isDrawSelectedBackground() ? 0xff89b4c1 : 0xffa1aab3);
            }
        }

        Drawable currentBackgroundDrawable;
        if (currentMessageObject.isOutOwner()) {
            if (isDrawSelectedBackground()) {
                if (!media) {
                    currentBackgroundDrawable = ResourceLoader.backgroundDrawableOutSelected;
                } else {
                    currentBackgroundDrawable = ResourceLoader.backgroundMediaDrawableOutSelected;
                }
            } else {
                if (!media) {
                    currentBackgroundDrawable = ResourceLoader.backgroundDrawableOut;
                } else {
                    currentBackgroundDrawable = ResourceLoader.backgroundMediaDrawableOut;
                }
            }
            setDrawableBounds(currentBackgroundDrawable, layoutWidth - backgroundWidth - (!media ? 0 : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
        } else {
            if (isDrawSelectedBackground()) {
                if (!media) {
                    currentBackgroundDrawable = ResourceLoader.backgroundDrawableInSelected;
                } else {
                    currentBackgroundDrawable = ResourceLoader.backgroundMediaDrawableInSelected;
                }
            } else {
                if (!media) {
                    currentBackgroundDrawable = ResourceLoader.backgroundDrawableIn;
                } else {
                    currentBackgroundDrawable = ResourceLoader.backgroundMediaDrawableIn;
                }
            }
            if (isChat && currentMessageObject.messageOwner.from_id > 0) {
                setDrawableBounds(currentBackgroundDrawable, AndroidUtilities.dp(52 + (!media ? 0 : 9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
            } else {
                setDrawableBounds(currentBackgroundDrawable, (!media ? 0 : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
            }
        }
        if (drawBackground && currentBackgroundDrawable != null) {
            currentBackgroundDrawable.draw(canvas);
        }

        onAfterBackgroundDraw(canvas);

        if (drawShareButton) {
            ResourceLoader.shareDrawable[ApplicationLoader.isCustomTheme() ? 1 : 0][sharePressed ? 1 : 0].setBounds(shareStartX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(8), shareStartY = layoutHeight - AndroidUtilities.dp(41), currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(40), layoutHeight - AndroidUtilities.dp(9));
            ResourceLoader.shareDrawable[ApplicationLoader.isCustomTheme() ? 1 : 0][sharePressed ? 1 : 0].draw(canvas);
        }

        if (drawName && nameLayout != null) {
            canvas.save();
            if (media || currentMessageObject.isOutOwner()) {
                canvas.translate(nameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(10) - nameOffsetX, AndroidUtilities.dp(10));
            } else {
                canvas.translate(nameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(19) - nameOffsetX, AndroidUtilities.dp(10));
            }
            if (currentUser != null) {
                namePaint.setColor(AvatarDrawable.getNameColorForId(currentUser.id));
            } else if (currentChat != null) {
                namePaint.setColor(AvatarDrawable.getNameColorForId(currentChat.id));
            } else {
                namePaint.setColor(AvatarDrawable.getNameColorForId(0));
            }
            nameLayout.draw(canvas);
            canvas.restore();

            /*if (forwardedNameLayout == null && viaWidth != 0) {
                canvas.drawRect(nameX + viaNameWidth, AndroidUtilities.dp(6), nameX + viaNameWidth + viaWidth, AndroidUtilities.dp(30), namePaint);
            }*/
        }

        if (drawForwardedName && forwardedNameLayout != null) {
            forwardNameY = AndroidUtilities.dp(10 + (drawName ? 19 : 0));
            if (currentMessageObject.isOutOwner()) {
                forwardNamePaint.setColor(0xff4a923c);
                forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(10);
            } else {
                forwardNamePaint.setColor(0xff006fc8);
                if (media) {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(10);
                } else {
                    forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(19);
                }
            }
            canvas.save();
            canvas.translate(forwardNameX - forwardNameOffsetX, forwardNameY);
            forwardedNameLayout.draw(canvas);
            canvas.restore();

            /*if (viaWidth != 0) {
                canvas.drawRect(forwardNameX + viaNameWidth, forwardNameY, forwardNameX + viaNameWidth + viaWidth, forwardNameY + AndroidUtilities.dp(32), namePaint);
            }*/
        }

        if (currentMessageObject.isReply()) {
            if (currentMessageObject.type == 13) {
                replyLinePaint.setColor(0xffffffff);
                replyNamePaint.setColor(0xffffffff);
                replyTextPaint.setColor(0xffffffff);
                int backWidth;
                if (currentMessageObject.isOutOwner()) {
                    backWidth = currentBackgroundDrawable.getBounds().left - AndroidUtilities.dp(32);
                    replyStartX = currentBackgroundDrawable.getBounds().left - AndroidUtilities.dp(9) - backWidth;
                } else {
                    backWidth = getWidth() - currentBackgroundDrawable.getBounds().right - AndroidUtilities.dp(32);
                    replyStartX = currentBackgroundDrawable.getBounds().right + AndroidUtilities.dp(23);
                }
                Drawable back;
                if (ApplicationLoader.isCustomTheme()) {
                    back = ResourceLoader.backgroundBlack;
                } else {
                    back = ResourceLoader.backgroundBlue;
                }
                replyStartY = layoutHeight - AndroidUtilities.dp(58);
                back.setBounds(replyStartX - AndroidUtilities.dp(7), replyStartY - AndroidUtilities.dp(6), replyStartX - AndroidUtilities.dp(7) + backWidth, replyStartY + AndroidUtilities.dp(41));
                back.draw(canvas);
            } else {
                if (currentMessageObject.isOutOwner()) {
                    replyLinePaint.setColor(0xff8dc97a);
                    replyNamePaint.setColor(0xff61a349);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0) {
                        replyTextPaint.setColor(0xff000000);
                    } else {
                        replyTextPaint.setColor(0xff70b15c);
                    }
                    replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
                } else {
                    replyLinePaint.setColor(0xff6c9fd2);
                    replyNamePaint.setColor(0xff377aae);
                    if (currentMessageObject.replyMessageObject != null && currentMessageObject.replyMessageObject.type == 0) {
                        replyTextPaint.setColor(0xff000000);
                    } else {
                        replyTextPaint.setColor(0xff999999);
                    }
                    if (currentMessageObject.contentType == 1 && media) {
                        replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(11);
                    } else {
                        replyStartX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(20);
                    }
                }
                replyStartY = AndroidUtilities.dp(12 + (drawForwardedName && forwardedNameLayout != null ? 36 : 0) + (drawName && nameLayout != null ? 20 : 0));
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

        if (drawTime || !media) {
            if (media) {
                setDrawableBounds(ResourceLoader.mediaBackgroundDrawable, timeX - AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(27.5f), timeWidth + AndroidUtilities.dp(6 + (currentMessageObject.isOutOwner() ? 20 : 0)), AndroidUtilities.dp(16.5f));
                ResourceLoader.mediaBackgroundDrawable.draw(canvas);

                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(ResourceLoader.clockMediaDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.clockMediaDrawable.getIntrinsicHeight());
                            ResourceLoader.clockMediaDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(ResourceLoader.errorDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(12.5f) - ResourceLoader.errorDrawable.getIntrinsicHeight());
                            ResourceLoader.errorDrawable.draw(canvas);
                        }
                    } else {
                        Drawable countDrawable = ResourceLoader.viewsMediaCountDrawable[isDrawSelectedBackground() ? 1 : 0];
                        setDrawableBounds(countDrawable, timeX, layoutHeight - AndroidUtilities.dp(10) - timeLayout.getHeight());
                        countDrawable.draw(canvas);

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + countDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(12.0f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(12.0f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
            } else {
                int additionalX = 0;
                if ((currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    additionalX = (int) (timeWidth - timeLayout.getLineWidth(0));

                    if (currentMessageObject.isSending()) {
                        if (!currentMessageObject.isOutOwner()) {
                            Drawable clockDrawable = ResourceLoader.clockChannelDrawable[isDrawSelectedBackground() ? 1 : 0];
                            setDrawableBounds(clockDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(8.5f) - clockDrawable.getIntrinsicHeight());
                            clockDrawable.draw(canvas);
                        }
                    } else if (currentMessageObject.isSendError()) {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(ResourceLoader.errorDrawable, timeX + AndroidUtilities.dp(11), layoutHeight - AndroidUtilities.dp(6.5f) - ResourceLoader.errorDrawable.getIntrinsicHeight());
                            ResourceLoader.errorDrawable.draw(canvas);
                        }
                    } else {
                        if (!currentMessageObject.isOutOwner()) {
                            setDrawableBounds(ResourceLoader.viewsCountDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                            ResourceLoader.viewsCountDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(ResourceLoader.viewsOutCountDrawable, timeX, layoutHeight - AndroidUtilities.dp(4.5f) - timeLayout.getHeight());
                            ResourceLoader.viewsOutCountDrawable.draw(canvas);
                        }

                        if (viewsLayout != null) {
                            canvas.save();
                            canvas.translate(timeX + ResourceLoader.viewsOutCountDrawable.getIntrinsicWidth() + AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                            viewsLayout.draw(canvas);
                            canvas.restore();
                        }
                    }
                }

                canvas.save();
                canvas.translate(timeX + additionalX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
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
                    if (!media) {
                        setDrawableBounds(ResourceLoader.clockDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - ResourceLoader.clockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - ResourceLoader.clockDrawable.getIntrinsicHeight());
                        ResourceLoader.clockDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(ResourceLoader.clockMediaDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - ResourceLoader.clockMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.clockMediaDrawable.getIntrinsicHeight());
                        ResourceLoader.clockMediaDrawable.draw(canvas);
                    }
                }
                if (isBroadcast) {
                    if (drawCheck1 || drawCheck2) {
                        if (!media) {
                            setDrawableBounds(ResourceLoader.broadcastDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - ResourceLoader.broadcastDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - ResourceLoader.broadcastDrawable.getIntrinsicHeight());
                            ResourceLoader.broadcastDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(ResourceLoader.broadcastMediaDrawable, layoutWidth - AndroidUtilities.dp(24.0f) - ResourceLoader.broadcastMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.broadcastMediaDrawable.getIntrinsicHeight());
                            ResourceLoader.broadcastMediaDrawable.draw(canvas);
                        }
                    }
                } else {
                    if (drawCheck2) {
                        if (!media) {
                            if (drawCheck1) {
                                setDrawableBounds(ResourceLoader.checkDrawable, layoutWidth - AndroidUtilities.dp(22.5f) - ResourceLoader.checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - ResourceLoader.checkDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(ResourceLoader.checkDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - ResourceLoader.checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - ResourceLoader.checkDrawable.getIntrinsicHeight());
                            }
                            ResourceLoader.checkDrawable.draw(canvas);
                        } else {
                            if (drawCheck1) {
                                setDrawableBounds(ResourceLoader.checkMediaDrawable, layoutWidth - AndroidUtilities.dp(26.0f) - ResourceLoader.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.checkMediaDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(ResourceLoader.checkMediaDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - ResourceLoader.checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.checkMediaDrawable.getIntrinsicHeight());
                            }
                            ResourceLoader.checkMediaDrawable.draw(canvas);
                        }
                    }
                    if (drawCheck1) {
                        if (!media) {
                            setDrawableBounds(ResourceLoader.halfCheckDrawable, layoutWidth - AndroidUtilities.dp(18) - ResourceLoader.halfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - ResourceLoader.halfCheckDrawable.getIntrinsicHeight());
                            ResourceLoader.halfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(ResourceLoader.halfCheckMediaDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - ResourceLoader.halfCheckMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - ResourceLoader.halfCheckMediaDrawable.getIntrinsicHeight());
                            ResourceLoader.halfCheckMediaDrawable.draw(canvas);
                        }
                    }
                }
                if (drawError) {
                    if (!media) {
                        setDrawableBounds(ResourceLoader.errorDrawable, layoutWidth - AndroidUtilities.dp(18) - ResourceLoader.errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(6.5f) - ResourceLoader.errorDrawable.getIntrinsicHeight());
                        ResourceLoader.errorDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(ResourceLoader.errorDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - ResourceLoader.errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - ResourceLoader.errorDrawable.getIntrinsicHeight());
                        ResourceLoader.errorDrawable.draw(canvas);
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
