/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.util.Linkify;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageObject {

    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public CharSequence linkDescription;
    public MessageObject replyMessageObject;
    public int type;
    public int contentType;
    public String dateKey;
    public String monthKey;
    public boolean deleted = false;
    public float audioProgress;
    public int audioProgressSec;
    public ArrayList<TLRPC.PhotoSize> photoThumbs;

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

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, boolean generateLayout) {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xff000000);
            textPaint.linkColor = 0xff316f9f;
        }

        textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));

        messageOwner = message;

        if (message.replyMessage != null) {
            replyMessageObject = new MessageObject(message.replyMessage, users, false);
        }

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
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouCreateGroup", R.string.ActionYouCreateGroup);
                    } else {
                        if (fromUser != null) {
                            messageText = replaceWithLink(LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup), "un1", fromUser);
                        } else {
                            messageText = LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouLeftUser", R.string.ActionYouLeftUser);
                        } else {
                            if (fromUser != null) {
                                messageText = replaceWithLink(LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser), "un1", fromUser);
                            } else {
                                messageText = LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser).replace("un1", "");
                            }
                        }
                    } else {
                        TLRPC.User whoUser = null;
                        if (users != null) {
                            whoUser = users.get(message.action.user_id);
                        }
                        if (whoUser == null) {
                            whoUser = MessagesController.getInstance().getUser(message.action.user_id);
                        }
                        if (whoUser != null && fromUser != null) {
                            if (isOut()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionYouKickUser", R.string.ActionYouKickUser), "un2", whoUser);
                            } else if (message.action.user_id == UserConfig.getClientUserId()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionKickUserYou", R.string.ActionKickUserYou), "un1", fromUser);
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionKickUser", R.string.ActionKickUser), "un2", whoUser);
                                messageText = replaceWithLink(messageText, "un1", fromUser);
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
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", whoUser);
                        } else if (message.action.user_id == UserConfig.getClientUserId()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionAddUserYou", R.string.ActionAddUserYou), "un1", fromUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", whoUser);
                            messageText = replaceWithLink(messageText, "un1", fromUser);
                        }
                    } else {
                        messageText = LocaleController.getString("ActionAddUser", R.string.ActionAddUser).replace("un2", "").replace("un1", "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouChangedPhoto", R.string.ActionYouChangedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto), "un1", fromUser);
                        } else {
                            messageText = LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouChangedTitle", R.string.ActionYouChangedTitle).replace("un2", message.action.title);
                    } else {
                        if (fromUser != null) {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un2", message.action.title), "un1", fromUser);
                        } else {
                            messageText = LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un1", "").replace("un2", message.action.title);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouRemovedPhoto", R.string.ActionYouRemovedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = replaceWithLink(LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto), "un1", fromUser);
                        } else {
                            messageText = LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (message.action.ttl != 0) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, AndroidUtilities.formatTTLString(message.action.ttl));
                        } else {
                            if (fromUser != null) {
                                messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, fromUser.first_name, AndroidUtilities.formatTTLString(message.action.ttl));
                            } else {
                                messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, "", AndroidUtilities.formatTTLString(message.action.ttl));
                            }
                        }
                    } else {
                        if (isOut()) {
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
                    String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.formatterYear.format(((long) message.date) * 1000), LocaleController.formatterDay.format(((long) message.date) * 1000));
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
                } else if (message.action instanceof TLRPC.TL_messageEncryptedAction) {
                    if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                        } else {
                            if (fromUser != null) {
                                messageText = replaceWithLink(LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot), "un1", fromUser);
                            } else {
                                messageText = LocaleController.formatString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot).replace("un1", "");
                            }
                        }
                    } else if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL) message.action.encryptedAction;
                        if (action.ttl_seconds != 0) {
                            if (isOut()) {
                                messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, AndroidUtilities.formatTTLString(action.ttl_seconds));
                            } else {
                                if (fromUser != null) {
                                    messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, fromUser.first_name, AndroidUtilities.formatTTLString(action.ttl_seconds));
                                } else {
                                    messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, "", AndroidUtilities.formatTTLString(action.ttl_seconds));
                                }
                            }
                        } else {
                            if (isOut()) {
                                messageText = LocaleController.getString("MessageLifetimeYouRemoved", R.string.MessageLifetimeYouRemoved);
                            } else {
                                if (fromUser != null) {
                                    messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, fromUser.first_name);
                                } else {
                                    messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, "");
                                }
                            }
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionCreatedBroadcastList) {
                    messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList);
                }
            }
        } else if (!isMediaEmpty()) {
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
                if (isSticker()) {
                    String sch = getStrickerChar();
                    if (sch != null && sch.length() > 0) {
                        messageText = String.format("%s %s", sch, LocaleController.getString("AttachSticker", R.string.AttachSticker));
                    } else {
                        messageText = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                    }
                } else {
                    String name = FileLoader.getDocumentFileName(message.media.document);
                    if (name != null && name.length() > 0) {
                        messageText = name;
                    } else {
                        messageText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                messageText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
            }
        } else {
            messageText = message.message;
        }
        messageText = Emoji.replaceEmoji(messageText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20));

        if (message instanceof TLRPC.TL_message || message instanceof TLRPC.TL_messageForwarded_old2) {
            if (isMediaEmpty()) {
                contentType = type = 0;
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                contentType = type = 1;
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo) {
                contentType = 1;
                type = 4;
            } else if (message.media instanceof TLRPC.TL_messageMediaVideo) {
                contentType = 1;
                type = 3;
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                contentType = 3;
                type = 12;
            } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                contentType = type = 0;
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                contentType = 1;
                if (message.media.document.mime_type != null) {
                    if (message.media.document.mime_type.equals("image/gif") && message.media.document.thumb != null && !(message.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                        type = 8;
                    } else if (message.media.document.mime_type.equals("image/webp") && isSticker()) {
                        type = 13;
                        if (messageOwner.media.document.thumb != null && messageOwner.media.document.thumb.location != null) {
                            messageOwner.media.document.thumb.location.ext = "webp";
                        }
                    } else {
                        type = 9;
                    }
                } else {
                    type = 9;
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                contentType = type = 2;
            }
        } else if (message instanceof TLRPC.TL_messageService) {
            if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                contentType = type = 0;
            } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                contentType = 4;
                type = 11;
            } else if (message.action instanceof TLRPC.TL_messageEncryptedAction) {
                if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                    contentType = 4;
                    type = 10;
                } else {
                    contentType = -1;
                    type = -1;
                }
            } else {
                contentType = 4;
                type = 10;
            }
        }

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
        if (contentType == 1 || contentType == 2) {
            monthKey = String.format("%d_%02d", dateYear, dateMonth);
        }

        if (generateLayout) {
            generateLayout();
        }
        generateThumbs(false);
    }

    public void generateThumbs(boolean update) {
        if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<>(messageOwner.action.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (TLRPC.PhotoSize photoObject : photoThumbs) {
                        for (TLRPC.PhotoSize size : messageOwner.action.photo.sizes) {
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            }
                        }
                    }
                }
            }
        } else if (messageOwner.media != null && !(messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<>(messageOwner.media.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (TLRPC.PhotoSize photoObject : photoThumbs) {
                        for (TLRPC.PhotoSize size : messageOwner.media.photo.sizes) {
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            }
                        }
                    }
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                if (!update) {
                    photoThumbs = new ArrayList<>();
                    photoThumbs.add(messageOwner.media.video.thumb);
                } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.video.thumb != null) {
                    TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                    photoObject.location = messageOwner.media.video.thumb.location;
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (!(messageOwner.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                    if (!update) {
                        photoThumbs = new ArrayList<>();
                        photoThumbs.add(messageOwner.media.document.thumb);
                    } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.document.thumb != null) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                        photoObject.location = messageOwner.media.document.thumb.location;
                    }
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (messageOwner.media.webpage.photo != null) {
                    if (!update || photoThumbs == null) {
                        photoThumbs = new ArrayList<>(messageOwner.media.webpage.photo.sizes);
                    } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                        for (TLRPC.PhotoSize photoObject : photoThumbs) {
                            for (TLRPC.PhotoSize size : messageOwner.media.webpage.photo.sizes) {
                                if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                    continue;
                                }
                                if (size.type.equals(photoObject.type)) {
                                    photoObject.location = size.location;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public CharSequence replaceWithLink(CharSequence source, String param, TLRPC.User user) {
        String name = ContactsController.formatName(user.first_name, user.last_name);
        int start = TextUtils.indexOf(source, param);
        URLSpanNoUnderline span = new URLSpanNoUnderline("" + user.id);
        SpannableStringBuilder builder = new SpannableStringBuilder(TextUtils.replace(source, new String[]{param}, new String[]{name}));
        builder.setSpan(span, start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
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

    public int getFileType() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
            return FileLoader.MEDIA_DIR_VIDEO;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.MEDIA_DIR_DOCUMENT;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaAudio) {
            return FileLoader.MEDIA_DIR_AUDIO;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return FileLoader.MEDIA_DIR_IMAGE;
        }
        return FileLoader.MEDIA_DIR_CACHE;
    }

    private boolean containsUrls(CharSequence message) {
        if (message == null || message.length() < 3 || message.length() > 1024 * 20) {
            return false;
        }

        int length = message.length();

        int digitsInRow = 0;
        int schemeSequence = 0;
        int dotSequence = 0;

        char lastChar = 0;

        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);

            if (c >= '0' && c <= '9') {
                digitsInRow++;
                if (digitsInRow >= 6) {
                    return true;
                }
                schemeSequence = 0;
                dotSequence = 0;
            } else if (!(c != ' ' && digitsInRow > 0)) {
                digitsInRow = 0;
            }
            if ((c == '@' || c == '#') && i == 0 || i != 0 && (message.charAt(i - 1) == ' ' || message.charAt(i - 1) == '\n')) {
                return true;
            }
            if (c == ':') {
                if (schemeSequence == 0) {
                    schemeSequence = 1;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '/') {
                if (schemeSequence == 2) {
                    return true;
                }
                if (schemeSequence == 1) {
                    schemeSequence++;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '.') {
                if (dotSequence == 0 && lastChar != ' ') {
                    dotSequence++;
                } else {
                    dotSequence = 0;
                }
            } else if (c != ' ' && lastChar == '.' && dotSequence == 1) {
                return true;
            } else {
                dotSequence = 0;
            }
            lastChar = c;
        }
        return false;
    }

    public void generateLinkDescription() {
        if (linkDescription != null) {
            return;
        }
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage && messageOwner.media.webpage.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.webpage.description);
            if (containsUrls(linkDescription)) {
                Linkify.addLinks((Spannable) linkDescription, Linkify.WEB_URLS);
            }
        }
    }

    private void generateLayout() {
        if (type != 0 || messageOwner.to_id == null || messageText == null || messageText.length() == 0) {
            return;
        }

        generateLinkDescription();
        textLayoutBlocks = new ArrayList<>();

        if (messageText instanceof Spannable && containsUrls(messageText)) {
            if (messageText.length() < 100) {
                Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
            } else {
                Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS);
            }

            try {
                Pattern pattern = Pattern.compile("(^|\\s)@[a-zA-Z\\d_]{5,32}|(^|\\s)#[\\w\\.]+");
                Matcher matcher = pattern.matcher(messageText);
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    if (messageText.charAt(start) != '@' && messageText.charAt(start) != '#') {
                        start++;
                    }
                    URLSpanNoUnderline url = new URLSpanNoUnderline(messageText.subSequence(start, end).toString());
                    ((Spannable) messageText).setSpan(url, start, end, 0);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            if (messageOwner.to_id.chat_id != 0 && !isOut()) {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
            } else {
                maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
            }
        } else {
            if (messageOwner.to_id.chat_id != 0 && !isOut()) {
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

        int blocksCount = (int) Math.ceil((float) linesCount / LINES_PER_BLOCK);
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
                        blockHeight = Math.min(blockHeight, (int) (block.textYOffset - prevOffset));
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

            int linesMaxWidth = (int) Math.ceil(lastLine);
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;
            boolean hasNonRTL = false;

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int) Math.ceil(lastLine + lastLeft);
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
                        lineWidth = maxWidth;
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
                    linesMaxWidth = Math.max(linesMaxWidth, (int) Math.ceil(lineWidth));
                    linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int) Math.ceil(lineWidth + lineLeft));
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
                textWidth = Math.max(textWidth, (int) Math.ceil(textRealMaxWidth));
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
        return (messageOwner.flags & TLRPC.MESSAGE_FLAG_OUT) != 0;
    }

    public boolean isUnread() {
        return (messageOwner.flags & TLRPC.MESSAGE_FLAG_UNREAD) != 0;
    }

    public void setIsRead() {
        messageOwner.flags &= ~TLRPC.MESSAGE_FLAG_UNREAD;
    }

    public int getId() {
        return messageOwner.id;
    }

    public boolean isSecretPhoto() {
        return messageOwner instanceof TLRPC.TL_message_secret && messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageOwner.ttl != 0 && messageOwner.ttl <= 60;
    }

    public boolean isSecretMedia() {
        return messageOwner instanceof TLRPC.TL_message_secret &&
                (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageOwner.ttl != 0 && messageOwner.ttl <= 60 ||
                        messageOwner.media instanceof TLRPC.TL_messageMediaAudio ||
                        messageOwner.media instanceof TLRPC.TL_messageMediaVideo);
    }

    public static void setIsUnread(TLRPC.Message message, boolean unread) {
        if (unread) {
            message.flags |= TLRPC.MESSAGE_FLAG_UNREAD;
        } else {
            message.flags &= ~TLRPC.MESSAGE_FLAG_UNREAD;
        }
    }

    public static boolean isUnread(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_UNREAD) != 0;
    }

    public static boolean isOut(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_OUT) != 0;
    }

    public long getDialogId() {
        if (messageOwner.dialog_id != 0) {
            return messageOwner.dialog_id;
        } else {
            if (messageOwner.to_id.chat_id != 0) {
                return -messageOwner.to_id.chat_id;
            } else if (isOut()) {
                return messageOwner.to_id.user_id;
            } else {
                return messageOwner.from_id;
            }
        }
    }

    public boolean isSending() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENDING && messageOwner.id < 0;
    }

    public boolean isSendError() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SEND_ERROR;
    }

    public boolean isSent() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENT || messageOwner.id > 0;
    }

    public String getSecretTimeString() {
        if (!isSecretMedia()) {
            return null;
        }
        int secondsLeft = messageOwner.ttl;
        if (messageOwner.destroyTime != 0) {
            secondsLeft = Math.max(0, messageOwner.destroyTime - ConnectionsManager.getInstance().getCurrentTime());
        }
        String str;
        if (secondsLeft < 60) {
            str = secondsLeft + "s";
        } else {
            str = secondsLeft / 60 + "m";
        }
        return str;
    }

    public String getDocumentName() {
        if (messageOwner.media != null && messageOwner.media.document != null) {
            return FileLoader.getDocumentFileName(messageOwner.media.document);
        }
        return "";
    }

    public static boolean isStickerMessage(TLRPC.Message message) {
        if (message.media != null && message.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : message.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getStrickerChar() {
        if (messageOwner.media != null && messageOwner.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return attribute.alt;
                }
            }
        }
        return null;
    }

    public int getApproximateHeight() {
        if (type == 0) {
            return textHeight;
        } else if (contentType == 2) {
            return AndroidUtilities.dp(68);
        } else if (contentType == 3) {
            return AndroidUtilities.dp(71);
        } else if (type == 9) {
            return AndroidUtilities.dp(100);
        } else if (type == 4) {
            return AndroidUtilities.dp(114);
        } else if (type == 13) {
            float maxHeight = AndroidUtilities.displaySize.y * 0.4f;
            float maxWidth;
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() * 0.5f;
            } else {
                maxWidth = AndroidUtilities.displaySize.x * 0.5f;
            }
            int photoHeight = 0;
            int photoWidth = 0;
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                    photoWidth = attribute.w;
                    photoHeight = attribute.h;
                    break;
                }
            }
            if (photoWidth == 0) {
                photoHeight = (int) maxHeight;
                photoWidth = photoHeight + AndroidUtilities.dp(100);
            }
            if (photoHeight > maxHeight) {
                photoWidth *= maxHeight / photoHeight;
                photoHeight = (int)maxHeight;
            }
            if (photoWidth > maxWidth) {
                photoHeight *= maxWidth / photoWidth;
            }
            return photoHeight + AndroidUtilities.dp(14);
        } else {
            int photoHeight = 0;
            int photoWidth = 0;

            if (AndroidUtilities.isTablet()) {
                photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
            } else {
                photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
            }
            photoHeight = photoWidth + AndroidUtilities.dp(100);
            if (photoWidth > AndroidUtilities.getPhotoSize()) {
                photoWidth = AndroidUtilities.getPhotoSize();
            }
            if (photoHeight > AndroidUtilities.getPhotoSize()) {
                photoHeight = AndroidUtilities.getPhotoSize();
            }
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());

            if (currentPhotoObject != null) {
                float scale = (float) currentPhotoObject.w / (float) photoWidth;
                int w = (int) (currentPhotoObject.w / scale);
                int h = (int) (currentPhotoObject.h / scale);
                if (w == 0) {
                    w = AndroidUtilities.dp(100);
                }
                if (h == 0) {
                    h = AndroidUtilities.dp(100);
                }
                if (h > photoHeight) {
                    float scale2 = h;
                    h = photoHeight;
                    scale2 /= h;
                    w = (int) (w / scale2);
                } else if (h < AndroidUtilities.dp(120)) {
                    h = AndroidUtilities.dp(120);
                    float hScale = (float) currentPhotoObject.h / h;
                    if (currentPhotoObject.w / hScale < photoWidth) {
                        w = (int) (currentPhotoObject.w / hScale);
                    }
                }
                if (isSecretPhoto()) {
                    if (AndroidUtilities.isTablet()) {
                        w = h = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                    } else {
                        w = h = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
                    }
                }

                photoWidth = w;
                photoHeight = h;
            }
            return photoHeight + AndroidUtilities.dp(14);
        }
    }

    public boolean isSticker() {
        return isStickerMessage(messageOwner);
    }

    public boolean isForwarded() {
        return (messageOwner.flags & TLRPC.MESSAGE_FLAG_FWD) != 0;
    }

    public boolean isReply() {
        return !(replyMessageObject != null && replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty) && messageOwner.reply_to_msg_id != 0 && (messageOwner.flags & TLRPC.MESSAGE_FLAG_REPLY) != 0;
    }

    public boolean isMediaEmpty() {
        return isMediaEmpty(messageOwner);
    }

    public static boolean isMediaEmpty(TLRPC.Message message) {
        return message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty || message.media instanceof TLRPC.TL_messageMediaWebPage;
    }
}
