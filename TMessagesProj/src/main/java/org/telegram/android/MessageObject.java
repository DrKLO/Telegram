/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.util.Linkify;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class MessageObject {

    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public int type;
    public int contentType;
    public ArrayList<PhotoObject> photoThumbs;
    public Bitmap imagePreview;
    public String dateKey;
    public boolean deleted = false;
    public float audioProgress;
    public int audioProgressSec;

    private static TextPaint textPaint;
    public int lastLineWidth;
    public int textWidth;
    public int textHeight;
    public int blockHeight = Integer.MAX_VALUE;

    public static class TextLayoutBlock {
        public StaticLayout textLayout;
        public float textXOffset = 0;
        public float textYOffset = 0;
        public int charactersOffset = 0;
    }

    private static final int LINES_PER_BLOCK = 10;

    public ArrayList<TextLayoutBlock> textLayoutBlocks;

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users) {
        this(message, users, 1);
    }

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, int preview) {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xff000000);
            textPaint.linkColor = 0xff316f9f;
        }

        textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));

        messageOwner = message;

        if (message instanceof TLRPC.TL_messageService) {
            if (message.action != null) {
                TLRPC.User fromUser = null;
                if (users != null) {
                    fromUser = users.get(message.from_id);
                }
                if (fromUser == null) {
                    fromUser = MessagesController.getInstance().getUser(message.from_id);
                }
                if (message.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (isFromMe()) {
                        messageText = LocaleController.getString("ActionYouCreateGroup", R.string.ActionYouCreateGroup);
                    } else {
                        if (fromUser != null) {
                            messageText = LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (isFromMe()) {
                            messageText = LocaleController.getString("ActionYouLeftUser", R.string.ActionYouLeftUser);
                        } else {
                            if (fromUser != null) {
                                messageText = LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser).replace("un1", "");
                            }
                        }
                    } else {
                        TLRPC.User who = null;
                        if (users != null) {
                            who = users.get(message.action.user_id);
                        }
                        if (who == null) {
                            who = MessagesController.getInstance().getUser(message.action.user_id);
                        }
                        if (who != null && fromUser != null) {
                            if (isFromMe()) {
                                messageText = LocaleController.getString("ActionYouKickUser", R.string.ActionYouKickUser).replace("un2", ContactsController.formatName(who.first_name, who.last_name));
                            } else if (message.action.user_id == UserConfig.getClientUserId()) {
                                messageText = LocaleController.getString("ActionKickUserYou", R.string.ActionKickUserYou).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = LocaleController.getString("ActionKickUser", R.string.ActionKickUser).replace("un2", ContactsController.formatName(who.first_name, who.last_name)).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                            }
                        } else {
                            messageText = LocaleController.getString("ActionKickUser", R.string.ActionKickUser).replace("un2", "").replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    TLRPC.User whoUser = null;
                    if (users != null) {
                        whoUser = users.get(message.action.user_id);
                    }
                    if (whoUser == null) {
                        whoUser = MessagesController.getInstance().getUser(message.action.user_id);
                    }
                    if (whoUser != null && fromUser != null) {
                        if (isFromMe()) {
                            messageText = LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser).replace("un2", ContactsController.formatName(whoUser.first_name, whoUser.last_name));
                        } else if (message.action.user_id == UserConfig.getClientUserId()) {
                            messageText = LocaleController.getString("ActionAddUserYou", R.string.ActionAddUserYou).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = LocaleController.getString("ActionAddUser", R.string.ActionAddUser).replace("un2", ContactsController.formatName(whoUser.first_name, whoUser.last_name)).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                        }
                    } else {
                        messageText = LocaleController.getString("ActionAddUser", R.string.ActionAddUser).replace("un2", "").replace("un1", "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    if (isFromMe()) {
                        messageText = LocaleController.getString("ActionYouChangedPhoto", R.string.ActionYouChangedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    if (isFromMe()) {
                        messageText = LocaleController.getString("ActionYouChangedTitle", R.string.ActionYouChangedTitle).replace("un2", message.action.title);
                    } else {
                        if (fromUser != null) {
                            messageText = LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name)).replace("un2", message.action.title);
                        } else {
                            messageText = LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un1", "").replace("un2", message.action.title);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    if (isFromMe()) {
                        messageText = LocaleController.getString("ActionYouRemovedPhoto", R.string.ActionYouRemovedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto).replace("un1", ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (message.action.ttl != 0) {
                        String timeString;
                        if (message.action.ttl == 2) {
                            timeString = LocaleController.getString("MessageLifetime2s", R.string.MessageLifetime2s);
                        } else if (message.action.ttl == 5) {
                            timeString = LocaleController.getString("MessageLifetime5s", R.string.MessageLifetime5s);
                        } else if (message.action.ttl == 60) {
                            timeString = LocaleController.getString("MessageLifetime1m", R.string.MessageLifetime1m);
                        } else if (message.action.ttl == 60 * 60) {
                            timeString = LocaleController.getString("MessageLifetime1h", R.string.MessageLifetime1h);
                        } else if (message.action.ttl == 60 * 60 * 24) {
                            timeString = LocaleController.getString("MessageLifetime1d", R.string.MessageLifetime1d);
                        } else if (message.action.ttl == 60 * 60 * 24 * 7) {
                            timeString = LocaleController.getString("MessageLifetime1w", R.string.MessageLifetime1w);
                        } else {
                            timeString = String.format("%d", message.action.ttl);
                        }
                        if (isFromMe()) {
                            messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, timeString);
                        } else {
                            if (fromUser != null) {
                                messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, fromUser.first_name, timeString);
                            } else {
                                messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, "", timeString);
                            }
                        }
                    } else {
                        if (isFromMe()) {
                            messageText = LocaleController.getString("MessageLifetimeYouRemoved", R.string.MessageLifetimeYouRemoved);
                        } else {
                            if (fromUser != null) {
                                messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, fromUser.first_name);
                            } else {
                                messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, "");
                            }
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                    String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.formatterYear.format(((long)message.date) * 1000), LocaleController.formatterDay.format(((long)message.date) * 1000));
                    TLRPC.User to_user = UserConfig.getCurrentUser();
                    if (to_user == null) {
                        if (users != null) {
                            to_user = users.get(messageOwner.to_id.user_id);
                        }
                        if (to_user == null) {
                            to_user = MessagesController.getInstance().getUser(messageOwner.to_id.user_id);
                        }
                    }
                    String name = "";
                    if (to_user != null) {
                        name = to_user.first_name;
                    }
                    messageText = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, name, date, message.action.title, message.action.address);
                } else if (message.action instanceof TLRPC.TL_messageActionUserJoined) {
                    if (fromUser != null) {
                        messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    if (fromUser != null) {
                        messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, ContactsController.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageEcryptedAction) {
                    if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        if (isFromMe()) {
                            messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                        } else {
                            if (fromUser != null) {
                                messageText = LocaleController.formatString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot).replace("un1", fromUser.first_name);
                            } else {
                                messageText = LocaleController.formatString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot).replace("un1", "");
                            }
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionCreatedBroadcastList) {
                    messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList);
                }
            }
        } else if (message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                messageText = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
            } else if (message.media instanceof TLRPC.TL_messageMediaVideo) {
                messageText = LocaleController.getString("AttachVideo", R.string.AttachVideo);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo) {
                messageText = LocaleController.getString("AttachLocation", R.string.AttachLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = LocaleController.getString("AttachContact", R.string.AttachContact);
            } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                messageText = LocaleController.getString("UnsuppotedMedia", R.string.UnsuppotedMedia);
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                messageText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                messageText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
            }
        } else {
            messageText = message.message;
        }
        messageText = Emoji.replaceEmoji(messageText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20));

        if (message instanceof TLRPC.TL_message || (message instanceof TLRPC.TL_messageForwarded && (message.media == null || !(message.media instanceof TLRPC.TL_messageMediaEmpty)))) {
            if (message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty) {
                contentType = type = 0;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaPhoto) {
                contentType = type = 1;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaGeo) {
                contentType = 1;
                type = 4;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaVideo) {
                contentType = 1;
                type = 3;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaContact) {
                if (isFromMe()) {
                    contentType = 3;
                    type = 12;
                } else {
                    contentType = 4;
                    type = 13;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                contentType = type = 0;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaDocument) {
                contentType = 1;
                if (message.media.document.thumb != null && !(message.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty) && message.media.document.mime_type != null && message.media.document.mime_type.equals("image/gif")) {
                    type = 8;
                } else {
                    type = 9;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaAudio) {
                contentType = type = 2;
            }
        } else if (message instanceof TLRPC.TL_messageService) {
            if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                contentType = type = 0;
            } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                contentType = 8;
                type = 11;
            } else {
                contentType = 7;
                type = 10;
            }
        } else if (message instanceof TLRPC.TL_messageForwarded) {
            contentType = type = 0;
        }

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long)(messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);

        generateLayout();
        generateThumbs(false, preview);
    }

    public void generateThumbs(boolean update, int preview) {
        if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<PhotoObject>();
                    for (TLRPC.PhotoSize size : messageOwner.action.photo.sizes) {
                        photoThumbs.add(new PhotoObject(size, preview));
                    }
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (PhotoObject photoObject : photoThumbs) {
                        for (TLRPC.PhotoSize size : messageOwner.action.photo.sizes) {
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.photoOwner.type)) {
                                photoObject.photoOwner.location = size.location;
                                break;
                            }
                        }
                    }
                }
            }
        } else if (messageOwner.media != null && !(messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<PhotoObject>();
                    for (TLRPC.PhotoSize size : messageOwner.media.photo.sizes) {
                        PhotoObject obj = new PhotoObject(size, preview);
                        photoThumbs.add(obj);
                        if (imagePreview == null && obj.image != null) {
                            imagePreview = obj.image;
                        }
                    }
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (PhotoObject photoObject : photoThumbs) {
                        for (TLRPC.PhotoSize size : messageOwner.media.photo.sizes) {
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.photoOwner.type)) {
                                photoObject.photoOwner.location = size.location;
                                break;
                            }
                        }
                    }
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                if (!update) {
                    photoThumbs = new ArrayList<PhotoObject>();
                    PhotoObject obj = new PhotoObject(messageOwner.media.video.thumb, preview);
                    photoThumbs.add(obj);
                    if (imagePreview == null && obj.image != null) {
                        imagePreview = obj.image;
                    }
                } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.video.thumb != null) {
                    PhotoObject photoObject = photoThumbs.get(0);
                    photoObject.photoOwner.location = messageOwner.media.video.thumb.location;
                }
            } if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (!(messageOwner.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                    if (!update) {
                        photoThumbs = new ArrayList<PhotoObject>();
                        PhotoObject obj = new PhotoObject(messageOwner.media.document.thumb, preview);
                        photoThumbs.add(obj);
                    } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.document.thumb != null) {
                        PhotoObject photoObject = photoThumbs.get(0);
                        photoObject.photoOwner.location = messageOwner.media.document.thumb.location;
                    }
                }
            }
        }
    }

    public String getFileName() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
            return FileLoader.getAttachFileName(messageOwner.media.video);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.getAttachFileName(messageOwner.media.document);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaAudio) {
            return FileLoader.getAttachFileName(messageOwner.media.audio);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            ArrayList<TLRPC.PhotoSize> sizes = messageOwner.media.photo.sizes;
            if (sizes.size() > 0) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    return FileLoader.getAttachFileName(sizeFull);
                }
            }
        }
        return "";
    }

    private void generateLayout() {
        if (type != 0 || messageOwner.to_id == null || messageText == null || messageText.length() == 0) {
            return;
        }

        textLayoutBlocks = new ArrayList<TextLayoutBlock>();

        if (messageText instanceof Spannable) {
            if (messageOwner.message != null && messageOwner.message.contains(".") && (messageOwner.message.contains(".com") || messageOwner.message.contains("http") || messageOwner.message.contains(".ru") || messageOwner.message.contains(".org") || messageOwner.message.contains(".net"))) {
                Linkify.addLinks((Spannable)messageText, Linkify.WEB_URLS);
            } else if (messageText.length() < 100) {
                Linkify.addLinks((Spannable)messageText, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
            }
        }

        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            if (messageOwner.to_id.chat_id != 0) {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
            } else {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
            }
        } else {
            if (messageOwner.to_id.chat_id != 0) {
                maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
            } else {
                maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
            }
        }

        StaticLayout textLayout = null;

        try {
            textLayout = new StaticLayout(messageText, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return;
        }

        textHeight = textLayout.getHeight();
        int linesCount = textLayout.getLineCount();

        int blocksCount = (int)Math.ceil((float)linesCount / LINES_PER_BLOCK);
        int linesOffset = 0;
        float prevOffset = 0;

        for (int a = 0; a < blocksCount; a++) {
            int currentBlockLinesCount = Math.min(LINES_PER_BLOCK, linesCount - linesOffset);
            TextLayoutBlock block = new TextLayoutBlock();

            if (blocksCount == 1) {
                block.textLayout = textLayout;
                block.textYOffset = 0;
                block.charactersOffset = 0;
                blockHeight = textHeight;
            } else {
                int startCharacter = textLayout.getLineStart(linesOffset);
                int endCharacter = textLayout.getLineEnd(linesOffset + currentBlockLinesCount - 1);
                if (endCharacter < startCharacter) {
                    continue;
                }
                block.charactersOffset = startCharacter;
                try {
                    CharSequence str = messageText.subSequence(startCharacter, endCharacter);
                    block.textLayout = new StaticLayout(str, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    block.textYOffset = textLayout.getLineTop(linesOffset);
                    if (a != 0) {
                        blockHeight = Math.min(blockHeight, (int)(block.textYOffset - prevOffset));
                    }
                    prevOffset = block.textYOffset;
                    /*if (a != blocksCount - 1) {
                        int height = block.textLayout.getHeight();
                        blockHeight = Math.min(blockHeight, block.textLayout.getHeight());
                        prevOffset = block.textYOffset;
                    } else {
                        blockHeight = Math.min(blockHeight, (int)(block.textYOffset - prevOffset));
                    }*/
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    continue;
                }
            }

            textLayoutBlocks.add(block);

            float lastLeft = block.textXOffset = 0;
            try {
                lastLeft = block.textXOffset = block.textLayout.getLineLeft(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            float lastLine = 0;
            try {
                lastLine = block.textLayout.getLineWidth(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            int linesMaxWidth = (int)Math.ceil(lastLine);
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;
            boolean hasNonRTL = false;

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int)Math.ceil(lastLine + lastLeft);
            if (lastLeft == 0) {
                hasNonRTL = true;
            }

            if (currentBlockLinesCount > 1) {
                float textRealMaxWidth = 0, textRealMaxWidthWithLeft = 0, lineWidth, lineLeft;
                for (int n = 0; n < currentBlockLinesCount; ++n) {
                    try {
                        lineWidth = block.textLayout.getLineWidth(n);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        lineWidth = 0;
                    }

                    if (lineWidth > maxWidth + 100) {
                        int start = block.textLayout.getLineStart(n);
                        int end = block.textLayout.getLineEnd(n);
                        CharSequence text = block.textLayout.getText().subSequence(start, end);
                        continue;
                    }

                    try {
                        lineLeft = block.textLayout.getLineLeft(n);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        lineLeft = 0;
                    }

                    block.textXOffset = Math.min(block.textXOffset, lineLeft);

                    if (lineLeft == 0) {
                        hasNonRTL = true;
                    }
                    textRealMaxWidth = Math.max(textRealMaxWidth, lineWidth);
                    textRealMaxWidthWithLeft = Math.max(textRealMaxWidthWithLeft, lineWidth + lineLeft);
                    linesMaxWidth = Math.max(linesMaxWidth, (int)Math.ceil(lineWidth));
                    linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int)Math.ceil(lineWidth + lineLeft));
                }
                if (hasNonRTL) {
                    textRealMaxWidth = textRealMaxWidthWithLeft;
                    if (a == blocksCount - 1) {
                        lastLineWidth = lastLineWidthWithLeft;
                    }
                    linesMaxWidth = linesMaxWidthWithLeft;
                } else if (a == blocksCount - 1) {
                    lastLineWidth = linesMaxWidth;
                }
                textWidth = Math.max(textWidth, (int)Math.ceil(textRealMaxWidth));
            } else {
                textWidth = Math.max(textWidth, Math.min(maxWidth, linesMaxWidth));
            }

            if (hasNonRTL) {
                block.textXOffset = 0;
            }

            linesOffset += currentBlockLinesCount;
        }
        if (blockHeight == 0) {
            blockHeight = 1;
        }
    }

    public boolean isOut() {
        return messageOwner.out;
    }

    public boolean isFromMe() {
        return messageOwner.from_id == UserConfig.getClientUserId();
    }

    public boolean isUnread () {
        return messageOwner.unread;
    }

    public long getDialogId() {
        if (messageOwner.dialog_id != 0) {
            return messageOwner.dialog_id;
        } else {
            if (messageOwner.to_id.chat_id != 0) {
                return -messageOwner.to_id.chat_id;
            } else if (isFromMe()) {
                return messageOwner.to_id.user_id;
            } else {
                return messageOwner.from_id;
            }
        }
    }

    public boolean isSending() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENDING;
    }

    public boolean isSendError() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SEND_ERROR;
    }

    public boolean isSent() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENT;
    }
}
